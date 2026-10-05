package ru.billyhargrove.pimobile.net;

import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import ru.billyhargrove.pimobile.core.Catalog;
import ru.billyhargrove.pimobile.core.CatalogParser;
import ru.billyhargrove.pimobile.core.EndpointPolicy;
import ru.billyhargrove.pimobile.core.ImageGuard;
import ru.billyhargrove.pimobile.core.MediaUrlPolicy;
import ru.billyhargrove.pimobile.core.Snapshot;
import ru.billyhargrove.pimobile.core.SnapshotParser;

/**
 * Blocking REST calls. Never called from the main thread; the caller runs them on
 * {@link AppExecutors#io()}.
 */
public final class HttpApi {

    private final OkHttpClient client;

    public HttpApi(OkHttpClient client) {
        this.client = client;
    }

    /** {@code GET /health} – unauthenticated, returns the raw body (no secrets). */
    public String health(String baseUrl) throws IOException {
        Request request = new Request.Builder()
                .url(EndpointPolicy.apiUrl(baseUrl, "/health"))
                .get()
                .header("Accept", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = bodyString(response);
            if (!response.isSuccessful()) {
                throw failure(response, body);
            }
            return body;
        }
    }

    public String transcribe(String baseUrl,String token,byte[] audio)throws Exception {
        JSONObject body=new JSONObject().put("sampleRate",16000).put("audio",java.util.Base64.getEncoder().encodeToString(audio));
        Request request=new Request.Builder().url(EndpointPolicy.apiUrl(baseUrl,"/api/transcribe")).header("Authorization","Bearer "+token).post(okhttp3.RequestBody.create(body.toString(),okhttp3.MediaType.get("application/json"))).build();
        try(Response response=client.newBuilder().readTimeout(100,TimeUnit.SECONDS).callTimeout(110,TimeUnit.SECONDS).build().newCall(request).execute()){String value=bodyString(response);if(!response.isSuccessful())throw failure(response,value);return new JSONObject(value).getString("text");}
    }

    public Catalog fetchCatalog(String baseUrl, String token) throws IOException {
        JSONObject json = getJson(EndpointPolicy.apiUrl(baseUrl, "/api/catalog"), token);
        Catalog catalog = CatalogParser.parse(json);
        if (catalog == null) {
            throw new ApiException("Unexpected catalog response from server");
        }
        return catalog;
    }

    public JSONObject fetchUsage(String baseUrl,String token)throws IOException{return getJson(EndpointPolicy.apiUrl(baseUrl,"/api/usage"),token);}

    public JSONObject fetchArchive(String baseUrl, String token, int offset, String query) throws IOException {
        return getJson(EndpointPolicy.apiUrl(baseUrl, "/api/archive?offset=" + offset + "&q=" + encodePathSegment(query)), token);
    }

    public Snapshot fetchSnapshot(String baseUrl, String token, String sessionId) throws IOException {
        String path = "/api/sessions/" + encodePathSegment(sessionId);
        JSONObject json = getJson(EndpointPolicy.apiUrl(baseUrl, path), token);
        Snapshot snapshot = SnapshotParser.parse(json);
        if (snapshot == null) {
            throw new ApiException("Unexpected session snapshot from server");
        }
        return snapshot;
    }

    /**
     * Fetches session media with the bearer token.
     *
     * <p>The URL is first resolved by {@link MediaUrlPolicy}: if it does not belong
     * to the configured origin the request is refused <em>before</em> any header is
     * attached, so the token can never leak to a third-party host.</p>
     */
    public byte[] fetchMedia(String baseUrl, String token, String rawUrl) throws IOException {
        String resolved = MediaUrlPolicy.resolve(baseUrl, rawUrl);
        if (resolved == null) {
            throw new ApiException("Media URL points outside the configured server");
        }
        Request request = new Request.Builder()
                .url(resolved)
                .get()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "image/*")
                .build();
        OkHttpClient mediaClient = client.newBuilder()
                .readTimeout(45, TimeUnit.SECONDS)
                .build();
        try (Response response = mediaClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw failure(response, bodyString(response));
            }
            ResponseBody body = response.body();
            if (body == null) {
                throw new ApiException("Empty image response");
            }
            long declared = body.contentLength();
            if (declared > ImageGuard.MAX_TOTAL_BYTES) {
                throw new ApiException("Image exceeds 10 MB");
            }
            return readCapped(body.byteStream(), ImageGuard.MAX_TOTAL_BYTES);
        }
    }

    private JSONObject getJson(String url, String token) throws IOException {
        Request request = new Request.Builder()
                .url(url)
                .get()
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();
        try (Response response = client.newCall(request).execute()) {
            String body = bodyString(response);
            if (!response.isSuccessful()) {
                throw failure(response, body);
            }
            try {
                return new JSONObject(body);
            } catch (JSONException e) {
                throw new ApiException("Invalid JSON from server", response.code(), e);
            }
        }
    }

    private static ApiException failure(Response response, @Nullable String body) {
        String hint;
        switch (response.code()) {
            case 401:
            case 403:
                hint = "Server rejected the token. Check your token in Settings.";
                break;
            case 404:
                hint = "Requested resource was not found.";
                break;
            default:
                hint = "Server error: HTTP " + response.code();
                break;
        }
        String serverError = errorFromBody(body);
        if (!serverError.isEmpty()) {
            hint = hint + " (" + serverError + ")";
        }
        return new ApiException(hint, response.code(), null);
    }

    private static String errorFromBody(@Nullable String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            return new JSONObject(body).optString("error", "");
        } catch (JSONException e) {
            return "";
        }
    }

    private static String bodyString(Response response) {
        try {
            ResponseBody body = response.body();
            if (body == null) {
                return "";
            }
            long length = body.contentLength();
            if (length > 1024 * 1024) {
                return "";
            }
            return body.string();
        } catch (IOException e) {
            return "";
        }
    }

    private static byte[] readCapped(InputStream in, long cap) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[16 * 1024];
        long total = 0;
        int read;
        while ((read = in.read(buffer)) != -1) {
            total += read;
            if (total > cap) {
                throw new ApiException("Image exceeds 10 MB");
            }
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }

    static String encodePathSegment(String value) {
        if (value == null) {
            return "";
        }
        try {
            return URLEncoder.encode(value, "UTF-8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
