package ru.billyhargrove.pimobile.net;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import ru.billyhargrove.pimobile.core.Ack;
import ru.billyhargrove.pimobile.core.AckParser;
import ru.billyhargrove.pimobile.core.Catalog;
import ru.billyhargrove.pimobile.core.CatalogParser;
import ru.billyhargrove.pimobile.core.CommandBuilder;
import ru.billyhargrove.pimobile.core.ConnectionState;
import ru.billyhargrove.pimobile.core.EndpointPolicy;
import ru.billyhargrove.pimobile.core.ImagePayload;
import ru.billyhargrove.pimobile.core.MessagesParser;
import ru.billyhargrove.pimobile.core.MessagesUpdate;
import ru.billyhargrove.pimobile.core.Snapshot;
import ru.billyhargrove.pimobile.core.SnapshotParser;

/**
 * WebSocket session with the Pi Mobile gateway.
 *
 * <p>Behaviour that the protocol requires and that is implemented here:</p>
 * <ul>
 *   <li>the token travels in the {@code Authorization} header only – never in a URL;</li>
 *   <li>the server pushes the catalog right after connect, and every reconnect
 *       re-subscribes to the currently open session;</li>
 *   <li>a command is sent with a fresh {@code requestId} and the ack resolves it.
 *       An ack means "accepted", not "completed";</li>
 *   <li>if no ack arrives (timeout or socket loss) the command is reported as
 *       uncertain and is <b>never</b> replayed automatically;</li>
 *   <li>all callbacks are delivered on the main thread.</li>
 * </ul>
 */
public final class PiClient extends WebSocketListener {

    /** How long to wait for an ack before calling the command uncertain. */
    public static final long ACK_TIMEOUT_MS = 20_000L;

    private static final long[] BACKOFF_MS = {1_000L, 2_000L, 4_000L, 8_000L, 15_000L, 30_000L};

    public interface Listener {
        void onConnectionState(ConnectionState state, String detail);

        void onCatalog(Catalog catalog);

        void onSnapshot(Snapshot snapshot);

        /**
         * Incremental transcript update. Only new/changed messages and removed ids
         * are delivered; the canonical order is kept by {@link ru.billyhargrove.pimobile.core.TranscriptStore}.
         */
        default void onMessages(MessagesUpdate update) {
        }

        void onAck(Ack ack);

        default void onConfiguration(String sessionId, JSONObject configuration) {}
        default void onTimelineMeta(JSONObject frame) {}
        default void onData(String requestId,String sessionId,JSONObject data) {}

        /** No ack arrived: timeout or the socket died first. Never auto-retried. */
        default void onCommandUncertain(String requestId, String sessionId, String reason) {
        }

        default void onProtocolError(String message) {
        }
    }

    private static final class Pending {
        final String requestId;
        final String sessionId;
        final Runnable timeoutTask;
        final boolean abort;

        Pending(String requestId, String sessionId, Runnable timeoutTask, boolean abort) {
            this.requestId = requestId;
            this.sessionId = sessionId;
            this.timeoutTask = timeoutTask;
            this.abort = abort;
        }
    }

    private final OkHttpClient http;
    private final HttpApi api;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final Map<String, Pending> pending = new LinkedHashMap<>();

    private volatile WebSocket socket;
    private volatile boolean shuttingDown = true;

    private Listener listener;
    private String baseUrl = "";
    private String token = "";
    private String desiredSessionId;
    private ConnectionState state = ConnectionState.IDLE;
    private String stateDetail = "";
    private int attempt;
    private boolean authRejected;
    private Catalog catalog = Catalog.empty();
    private Snapshot lastSnapshot;
    private JSONObject configuration;
    private String configurationSessionId = "";
    private Runnable reconnectTask;
    private ru.billyhargrove.pimobile.store.ConversationCache diskCache;
    private final java.util.concurrent.ExecutorService cacheIo=java.util.concurrent.Executors.newSingleThreadExecutor();
    private final LinkedHashMap<String,JSONObject> conversations=new LinkedHashMap<>(8,.75f,true);
    private int subscriptionGeneration;private boolean subscriptionReady;
    public void cacheDirectory(java.io.File directory){diskCache=new ru.billyhargrove.pimobile.store.ConversationCache(directory);}
    private String cacheKey(String session){return ru.billyhargrove.pimobile.store.ConversationCache.key(baseUrl,token,session);}
    private final java.util.Set<String> pendingCacheWrites=new java.util.HashSet<>();
    private void retain(String key,JSONObject value){conversations.put(key,value);while(conversations.size()>8)conversations.remove(conversations.keySet().iterator().next());if(diskCache!=null&&pendingCacheWrites.add(key))main.postDelayed(()->{pendingCacheWrites.remove(key);JSONObject latest=conversations.get(key);if(latest!=null&&!cacheIo.isShutdown()){String json=latest.toString();cacheIo.execute(()->diskCache.write(key,json));}},750);}
    public JSONObject cachedViewport(String session){JSONObject value=conversations.get(cacheKey(session));return value==null?null:value.optJSONObject("viewport");}
    public void saveViewport(String session,String anchor,int offset,boolean follow){JSONObject value=conversations.get(cacheKey(session));if(value==null)return;try{value.put("viewport",new JSONObject().put("anchor",anchor).put("offset",offset).put("follow",follow));retain(cacheKey(session),value);}catch(JSONException ignored){}}
    private void cacheFrame(JSONObject frame){String session=frame.optString("sessionId");if(session.isEmpty())return;String key=cacheKey(session);JSONObject prior=conversations.get(key);try{JSONObject next=ru.billyhargrove.pimobile.core.ConversationFrames.merge(prior,frame);if(prior!=null&&prior.has("viewport"))next.put("viewport",prior.get("viewport"));retain(key,next);}catch(JSONException ignored){}}
    private void sendSubscription(){WebSocket ws=socket;if(!subscriptionReady||desiredSessionId==null||ws==null||state!=ConnectionState.CONNECTED)return;JSONObject frame=CommandBuilder.subscribe(desiredSessionId);JSONObject cached=conversations.get(cacheKey(desiredSessionId));try{if(cached!=null&&cached.has("checkpoint"))frame.put("resume",cached.get("checkpoint"));}catch(JSONException ignored){}sendFrame(ws,frame.toString());}
    private void restoreAndSubscribe(String session,String key,int generation,JSONObject cached){if(generation!=subscriptionGeneration||!session.equals(desiredSessionId)||!key.equals(cacheKey(session)))return;JSONObject latest=conversations.get(key);if(latest!=null)cached=latest;
        if(cached!=null&&session.equals(cached.optString("sessionId"))){conversations.put(key,cached);Snapshot parsed=SnapshotParser.parse(cached);if(parsed!=null){lastSnapshot=parsed;configuration=cached.optJSONObject("configuration");configurationSessionId=session;if(listener!=null){try{JSONObject meta=new JSONObject(cached.toString()).put("cached",true);listener.onTimelineMeta(meta);}catch(JSONException ignored){}if(configuration!=null)listener.onConfiguration(session,configuration);listener.onSnapshot(parsed);}}}
        subscriptionReady=true;sendSubscription();
    }

    public PiClient(OkHttpClient http, HttpApi api) {
        this.http = http;
        this.api = api;
    }

    // ---------------------------------------------------------------- lifecycle

    public void setListener(Listener listener) {
        this.listener = listener;
        if (listener != null) {
            listener.onConnectionState(state, stateDetail);
        }
    }

    public void clearListener(Listener listener) {
        if (this.listener == listener) {
            this.listener = null;
        }
    }

    /** Opens (or re-opens) the socket for the given endpoint. */
    public void connect(String baseUrl, String token) {
        EndpointPolicy.Result result = EndpointPolicy.validate(baseUrl, ru.billyhargrove.pimobile.BuildConfig.DEBUG);
        if (result != EndpointPolicy.Result.OK) {
            setState(ConnectionState.ERROR, russianReason(result));
            return;
        }
        String nextUrl=EndpointPolicy.normalize(baseUrl),nextToken=token==null?"":token.trim();
        if(!this.baseUrl.equals(nextUrl)||!this.token.equals(nextToken)){subscriptionGeneration++;subscriptionReady=false;desiredSessionId=null;lastSnapshot=null;configuration=null;configurationSessionId="";}
        this.baseUrl = nextUrl;
        this.token = token == null ? "" : token.trim();
        this.authRejected = false;
        this.attempt = 0;
        this.shuttingDown = false;
        cancelReconnect();
        closeSocket();
        setState(ConnectionState.CONNECTING, this.baseUrl);
        openSocket();
    }

    public void disconnect() {
        shuttingDown = true;
        cancelReconnect();
        desiredSessionId = null;subscriptionGeneration++;subscriptionReady=false;
        failAllPending("Connection closed by user");
        closeSocket();
        setState(ConnectionState.DISCONNECTED, "");
    }

    // ------------------------------------------------------------------ commands

    /** Remembers the session and subscribes now, or on the next (re)connect. */
    public void subscribe(String sessionId) {
        desiredSessionId=sessionId;subscriptionReady=false;int generation=++subscriptionGeneration;String key=cacheKey(sessionId);JSONObject cached=conversations.get(key);
        if(cached!=null||diskCache==null){restoreAndSubscribe(sessionId,key,generation,cached);return;}
        cacheIo.execute(()->{JSONObject disk=diskCache.read(key);main.post(()->restoreAndSubscribe(sessionId,key,generation,disk));});
    }

    /**
     * Sends a prompt command.
     *
     * @return the request id to correlate the ack with, or {@code null} when the
     *         socket is not usable. The caller must keep the composer content in
     *         that case.
     */
    @Nullable
    public String sendPrompt(String sessionId,
                             String text,
                             List<ImagePayload> images,
                             CommandBuilder.Behavior behavior) {
        WebSocket ws = socket;
        if (ws == null || state != ConnectionState.CONNECTED) {
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        String frame;
        try {
            frame = CommandBuilder.promptCommand(sessionId, requestId, text, images, behavior).toString();
        } catch (IllegalArgumentException e) {
            return null;
        }
        if (!sendFrame(ws, frame)) {
            return null;
        }
        registerPending(requestId, sessionId, false);
        return requestId;
    }

    /** Sends an abort (stop) command. */
    @Nullable
    public String sendAbort(String sessionId) {
        WebSocket ws = socket;
        if (ws == null || state != ConnectionState.CONNECTED) {
            return null;
        }
        String requestId = UUID.randomUUID().toString();
        if (!sendFrame(ws, CommandBuilder.abortCommand(sessionId, requestId).toString())) {
            return null;
        }
        registerPending(requestId, sessionId, true);
        return requestId;
    }

    @Nullable public String configure(String sessionId,String provider,String modelId,String thinkingLevel){return configure(sessionId,provider,modelId,thinkingLevel,null);}
    @Nullable public String configure(String sessionId, String provider, String modelId, String thinkingLevel,String serviceTier) {
        WebSocket ws=socket;
        if(ws==null||state!=ConnectionState.CONNECTED)return null;
        String requestId=UUID.randomUUID().toString();
        try {
            JSONObject frame=new JSONObject().put("type","command").put("sessionId",sessionId).put("requestId",requestId).put("command","configure").put("provider",provider).put("modelId",modelId).put("thinkingLevel",thinkingLevel).put("serviceTier",serviceTier);
            if(!sendFrame(ws,frame.toString()))return null;
            registerPending(requestId,sessionId,false);return requestId;
        } catch(JSONException e){return null;}
    }

    public JSONObject configuration(String sessionId) { return sessionId.equals(configurationSessionId) ? configuration : null; }

    @Nullable public String readCommand(String sessionId,String command,JSONObject args) {
        WebSocket ws=socket;if(ws==null||state!=ConnectionState.CONNECTED)return null;
        String id=UUID.randomUUID().toString();
        try{args.put("type","command").put("command",command).put("sessionId",sessionId).put("requestId",id);if(!sendFrame(ws,args.toString()))return null;registerPending(id,sessionId,false);return id;}catch(JSONException e){return null;}
    }

    // ---------------------------------------------------------------- accessors

    public ConnectionState state() {
        return state;
    }

    public String stateDetail() {
        return stateDetail;
    }

    public Catalog catalog() {
        return catalog;
    }

    @Nullable
    public Snapshot lastSnapshot() {
        return lastSnapshot;
    }

    public String baseUrl() {
        return baseUrl;
    }

    public HttpApi api() {
        return api;
    }

    public OkHttpClient http() {
        return http;
    }

    // -------------------------------------------------------------- web socket

    private void openSocket() {
        Request request = new Request.Builder()
                .url(EndpointPolicy.wsUrl(baseUrl))
                .header("Authorization", "Bearer " + token)
                .build();
        WebSocket ws = http.newWebSocket(request, this);
        socket = ws;
    }

    private void closeSocket() {
        WebSocket ws = socket;
        socket = null;
        if (ws != null) {
            try {
                ws.close(1000, "client closing");
            } catch (RuntimeException ignored) {
                // socket already gone
            }
        }
    }

    @Override
    public void onOpen(WebSocket webSocket, Response response) {
        if (webSocket != socket) {
            webSocket.close(1000, "stale socket");
            return;
        }
        runOnMain(new Runnable() {
            @Override
            public void run() {
                attempt = 0;
                setState(ConnectionState.CONNECTED, baseUrl);
                sendSubscription();
            }
        });
    }

    @Override
    public void onMessage(WebSocket webSocket, String text) {
        if (webSocket != socket) {
            return;
        }
        final String payload = text;
        runOnMain(new Runnable() {
            @Override
            public void run() {
                handleFrame(payload);
            }
        });
    }

    @Override
    public void onMessage(WebSocket webSocket, okio.ByteString bytes) {
        // The protocol is JSON text frames only; binary frames are ignored.
    }

    @Override
    public void onClosing(WebSocket webSocket, int code, String reason) {
        if (webSocket == socket) {
            webSocket.close(1000, null);
        }
    }

    @Override
    public void onClosed(WebSocket webSocket, int code, String reason) {
        if (webSocket != socket) {
            return;
        }
        socket = null;
        runOnMain(new Runnable() {
            @Override
            public void run() {
                failAllPending("Connection closed before acknowledgment");
                scheduleReconnect("Connection closed");
            }
        });
    }

    @Override
    public void onFailure(WebSocket webSocket, final Throwable t, final Response response) {
        if (webSocket != socket) {
            return;
        }
        socket = null;
        runOnMain(new Runnable() {
            @Override
            public void run() {
                failAllPending("Connection lost before acknowledgment");
                int code = response == null ? 0 : response.code();
                if (code == 401 || code == 403) {
                    authRejected = true;
                    setState(ConnectionState.ERROR,
                            "Server rejected the token (HTTP " + code + "). Check your token in Settings.");
                    return;
                }
                String reason = t == null || t.getMessage() == null ? "network error" : t.getMessage();
                scheduleReconnect(reason);
            }
        });
    }

    // ------------------------------------------------------------------ frames

    private void handleFrame(String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        JSONObject object;
        try {
            object = new JSONObject(text);
        } catch (JSONException e) {
            notifyProtocolError("Unrecognized server frame");
            return;
        }
        String type = object.optString("type", "");
        if("snapshot".equals(type)||"messages".equals(type))cacheFrame(object);
        if("ack".equals(type)&&object.optBoolean("ok")){JSONObject data=object.optJSONObject("data");if(data!=null&&"history".equals(data.optString("type"))){String key=cacheKey(object.optString("sessionId"));try{JSONObject merged=ru.billyhargrove.pimobile.core.ConversationFrames.prepend(conversations.get(key),data);if(merged!=null)retain(key,merged);}catch(JSONException ignored){}}}
        if (("snapshot".equals(type) || "messages".equals(type)) && object.optJSONObject("configuration") != null) {
            configuration = object.optJSONObject("configuration");
            configurationSessionId = object.optString("sessionId", "");
            if (listener != null) listener.onConfiguration(configurationSessionId, configuration);
        }
        if (("snapshot".equals(type)||"messages".equals(type)) && listener!=null) listener.onTimelineMeta(object);
        switch (type) {
            case "catalog": {
                Catalog parsed = CatalogParser.parse(object);
                if (parsed != null) {
                    catalog = parsed;
                    if (listener != null) {
                        listener.onCatalog(parsed);
                    }
                }
                break;
            }
            case "snapshot": {
                Snapshot parsed = SnapshotParser.parse(object);
                if (parsed != null) {
                    lastSnapshot = parsed;
                    if (listener != null) {
                        listener.onSnapshot(parsed);
                    }
                }
                break;
            }
            case "messages": {
                // Reconcile the resumed tail in server order while retaining cached older pages.
                if(object.optBoolean("resumed")){JSONObject merged=conversations.get(cacheKey(object.optString("sessionId")));Snapshot restored=merged==null?null:SnapshotParser.parse(merged);if(restored!=null){lastSnapshot=restored;if(listener!=null)listener.onSnapshot(restored);break;}}
                MessagesUpdate parsed = MessagesParser.parse(object);
                if (parsed != null && listener != null) {
                    listener.onMessages(parsed);
                }
                break;
            }
            case "ack": {
                if(object.optBoolean("ok")&&object.optJSONObject("data")!=null&&listener!=null)listener.onData(object.optString("requestId"),object.optString("sessionId"),object.optJSONObject("data"));
                Ack ack = AckParser.parse(object);
                if (ack != null) {
                    resolvePending(ack);
                }
                break;
            }
            case "error": {
                notifyProtocolError(AckParser.parseErrorMessage(object));
                break;
            }
            default:
                // Unknown frame types are ignored on purpose; the protocol is
                // extensible and we must not drop the socket because of them.
                break;
        }
    }

    private void resolvePending(Ack ack) {
        Pending p = pending.remove(ack.requestId());
        if (p != null) {
            main.removeCallbacks(p.timeoutTask);
        }
        if (listener != null) {
            listener.onAck(ack);
        }
    }

    private void registerPending(final String requestId, final String sessionId, final boolean abort) {
        Runnable timeout = new Runnable() {
            @Override
            public void run() {
                Pending p = pending.remove(requestId);
                if (p == null) {
                    return;
                }
                if (listener != null) {
                    String what = abort ? "stop request" : "command";
                    listener.onCommandUncertain(requestId, sessionId,
                            "Server did not acknowledge the " + what + " within " + (ACK_TIMEOUT_MS / 1000) + " s");
                }
            }
        };
        pending.put(requestId, new Pending(requestId, sessionId, timeout, abort));
        main.postDelayed(timeout, ACK_TIMEOUT_MS);
    }

    private void failAllPending(String reason) {
        if (pending.isEmpty()) {
            return;
        }
        List<Pending> all = new ArrayList<>(pending.values());
        pending.clear();
        for (Pending p : all) {
            main.removeCallbacks(p.timeoutTask);
            if (listener != null) {
                listener.onCommandUncertain(p.requestId, p.sessionId, reason);
            }
        }
    }

    // ----------------------------------------------------------------- retries

    private void scheduleReconnect(String reason) {
        if (shuttingDown || authRejected) {
            if (authRejected) {
                return;
            }
            setState(ConnectionState.DISCONNECTED, "");
            return;
        }
        long delay = BACKOFF_MS[Math.min(attempt, BACKOFF_MS.length - 1)];
        attempt++;
        long jitter = random.nextInt(400) - 200L;
        long effective = Math.max(500L, delay + jitter);
        setState(ConnectionState.RECONNECTING,
                "Retrying in " + Math.round(effective / 1000.0) + " s" + (reason.isEmpty() ? "" : " · " + reason));
        cancelReconnect();
        reconnectTask = new Runnable() {
            @Override
            public void run() {
                reconnectTask = null;
                if (shuttingDown || authRejected) {
                    return;
                }
                setState(ConnectionState.CONNECTING, "Reconnecting to " + baseUrl);
                openSocket();
            }
        };
        main.postDelayed(reconnectTask, effective);
    }

    private void cancelReconnect() {
        if (reconnectTask != null) {
            main.removeCallbacks(reconnectTask);
            reconnectTask = null;
        }
    }

    // ------------------------------------------------------------------ helpers

    private boolean sendFrame(WebSocket ws, String frame) {
        try {
            return ws.send(frame);
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void setState(ConnectionState newState, String detail) {
        String safeDetail = detail == null ? "" : detail;
        if (state == newState && safeDetail.equals(stateDetail)) {
            return;
        }
        state = newState;
        stateDetail = safeDetail;
        if (listener != null) {
            listener.onConnectionState(newState, safeDetail);
        }
    }

    private void notifyProtocolError(String message) {
        if (listener != null && message != null && !message.isEmpty()) {
            listener.onProtocolError(message);
        }
    }

    private void runOnMain(Runnable runnable) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            runnable.run();
        } else {
            main.post(runnable);
        }
    }

    /** User-showable explanation for a rejected endpoint. */
    public static String describeResult(EndpointPolicy.Result result) {
        return russianReason(result);
    }

    private static String russianReason(EndpointPolicy.Result result) {
        switch (result) {
            case EMPTY:
                return "Enter a server URL.";
            case MALFORMED:
                return "Invalid server URL.";
            case UNSUPPORTED_SCHEME:
                return "Only HTTPS and HTTP (debug) are supported.";
            case CLEARTEXT_NOT_ALLOWED:
                return "HTTP is allowed only for 10.0.2.2 and localhost in debug builds. Use HTTPS.";
            case HAS_CREDENTIALS:
                return "Remove credentials from the URL; enter your token separately.";
            case HAS_QUERY_OR_FRAGMENT:
                return "URL must not contain a query or fragment.";
            case MISSING_HOST:
                return "URL is missing a host.";
            default:
                return "URL rejected.";
        }
    }

    /** Bulk helper used by screens that iterate over pending ids (tests, UI cleanup). */
    public List<String> pendingRequestIds() {
        return new ArrayList<>(pending.keySet());
    }

    /** Removes callbacks; call from {@code Application#onTerminate} only. */
    public void shutdown() {
        disconnect();cacheIo.shutdown();
        Iterator<Map.Entry<String, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            main.removeCallbacks(it.next().getValue().timeoutTask);
            it.remove();
        }
    }
}
