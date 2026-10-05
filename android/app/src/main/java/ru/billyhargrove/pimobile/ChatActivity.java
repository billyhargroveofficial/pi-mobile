package ru.billyhargrove.pimobile;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import com.google.android.material.button.MaterialButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import ru.billyhargrove.pimobile.core.Ack;
import ru.billyhargrove.pimobile.core.Catalog;
import ru.billyhargrove.pimobile.core.ChatMessage;
import ru.billyhargrove.pimobile.core.CommandBuilder;
import ru.billyhargrove.pimobile.core.ConnectionState;
import ru.billyhargrove.pimobile.core.ImageGuard;
import ru.billyhargrove.pimobile.core.ImagePayload;
import ru.billyhargrove.pimobile.core.ImageRef;
import ru.billyhargrove.pimobile.core.MessagesUpdate;
import ru.billyhargrove.pimobile.core.Session;
import ru.billyhargrove.pimobile.core.SessionStatus;
import ru.billyhargrove.pimobile.core.Snapshot;
import ru.billyhargrove.pimobile.core.TranscriptReconciler;
import ru.billyhargrove.pimobile.core.TranscriptStore;
import ru.billyhargrove.pimobile.media.Attachment;
import ru.billyhargrove.pimobile.media.ImagePreparer;
import ru.billyhargrove.pimobile.net.AppExecutors;
import ru.billyhargrove.pimobile.net.PiClient;
import ru.billyhargrove.pimobile.store.SettingsStore;
import ru.billyhargrove.pimobile.ui.MessageAdapter;
import ru.billyhargrove.pimobile.ui.StatusUi;
import ru.billyhargrove.pimobile.ui.SystemInsets;

/**
 * Live transcript for one session, plus the composer.
 *
 * <p>Command lifecycle: the composer is cleared only after the frame has been
 * written to the socket, and every command keeps its draft. If the gateway
 * rejects it the text comes back to the composer; if no ack arrives the message is
 * marked "статус неизвестен" and is never replayed automatically – only an
 * explicit tap on "Повторить вручную" sends it again.</p>
 */
public final class ChatActivity extends AppCompatActivity
        implements PiClient.Listener, MessageAdapter.Listener, MessageAdapter.LocalThumbProvider {

    private static final String EXTRA_SESSION_ID = "session_id";
    private static final String EXTRA_TITLE = "session_title";
    private static final String EXTRA_READ_ONLY = "read_only";

    public static Intent intent(Context context, String sessionId, String title, boolean readOnly) {
        Intent intent = new Intent(context, ChatActivity.class);
        intent.putExtra(EXTRA_SESSION_ID, sessionId);
        intent.putExtra(EXTRA_TITLE, title);
        intent.putExtra(EXTRA_READ_ONLY, readOnly);
        return intent;
    }

    /** Everything needed to resend or restore a command. */
    private static final class Draft {
        final String text;
        final List<ImagePayload> payloads;
        final List<Bitmap> thumbs;
        final List<String> mimeTypes;
        final CommandBuilder.Behavior behavior;

        Draft(String text,
              List<ImagePayload> payloads,
              List<Bitmap> thumbs,
              List<String> mimeTypes,
              CommandBuilder.Behavior behavior) {
            this.text = text == null ? "" : text;
            this.payloads = payloads == null ? new ArrayList<ImagePayload>() : new ArrayList<>(payloads);
            this.thumbs = thumbs == null ? new ArrayList<Bitmap>() : new ArrayList<>(thumbs);
            this.mimeTypes = mimeTypes == null ? new ArrayList<String>() : new ArrayList<>(mimeTypes);
            this.behavior = behavior == null ? CommandBuilder.Behavior.FOLLOW_UP : behavior;
        }
    }

    private PiApp app;
    private PiClient client;
    private SettingsStore settings;

    private MessageAdapter adapter;
    private RecyclerView messageList;
    private EditText composerInput;
    private Button sendButton;
    private Button attachButton;
    private Button stopButton;
    private Button backButton;
    private MaterialButton behaviorMenu;
    private String historyRequest,documentRequest,beforeCursor="";
    private boolean hasMoreHistory;
    private long historyEpoch;
    private ru.billyhargrove.pimobile.ui.MarkdownPreview documentPreview;
    private LinearLayout attachmentStrip;
    private HorizontalScrollView attachmentsScroll;
    private TextView attachmentsTitle;
    private TextView chatTitleText;
    private TextView chatStatusText;
    private TextView chatEmptyText;
    private TextView truncatedBanner;
    private TextView readOnlyBanner;
    private View chatConnectionDot;

    private String sessionId = "";
    private String sessionTitle = "";
    private boolean readOnly;
    private ru.billyhargrove.pimobile.ui.ModelSettingsSheet modelSheet;
    private String configurationRequest;
    private org.json.JSONObject configuration;
    private SessionStatus sessionStatus = SessionStatus.UNKNOWN;

    /** Canonical transcript: full snapshots plus incremental {type:'messages'} frames. */
    private final TranscriptStore store = new TranscriptStore();
    private boolean firstRenderDone;
    private final List<ChatMessage> localMessages = new ArrayList<>();
    private final List<Attachment> attachments = new ArrayList<>();
    private final Map<String, Draft> drafts = new LinkedHashMap<>();
    private final Set<String> abortRequests = new HashSet<>();

    private final ActivityResultLauncher<String[]> imagePicker = registerForActivityResult(
            new ActivityResultContracts.OpenMultipleDocuments(),
            this::onImagesPicked);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_chat);
        com.google.android.material.transition.platform.MaterialSharedAxis enter = new com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, true);
        enter.setDuration(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled() ? 350 : 0);
        getWindow().setEnterTransition(enter);
        com.google.android.material.transition.platform.MaterialSharedAxis back = new com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, false);
        back.setDuration(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled() ? 350 : 0);
        getWindow().setReturnTransition(back);
        ru.billyhargrove.pimobile.ui.ExpressiveMotion.buttons(findViewById(R.id.chatRoot));

        app = PiApp.get(this);
        client = app.client();
        settings = app.settings();

        Intent intent = getIntent();
        sessionId = intent.getStringExtra(EXTRA_SESSION_ID) == null ? "" : intent.getStringExtra(EXTRA_SESSION_ID);
        sessionTitle = intent.getStringExtra(EXTRA_TITLE) == null ? "" : intent.getStringExtra(EXTRA_TITLE);
        readOnly = intent.getBooleanExtra(EXTRA_READ_ONLY, false);

        messageList = findViewById(R.id.messageList);
        composerInput = findViewById(R.id.composerInput);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachImageButton);
        stopButton = findViewById(R.id.stopButton);
        backButton = findViewById(R.id.backButton);
        behaviorMenu = findViewById(R.id.behaviorMenuButton);
        attachmentStrip = findViewById(R.id.attachmentStrip);
        attachmentsScroll = findViewById(R.id.attachmentsScroll);
        attachmentsTitle = findViewById(R.id.attachmentsTitle);
        chatTitleText = findViewById(R.id.chatTitleText);
        chatStatusText = findViewById(R.id.chatStatusText);
        chatEmptyText = findViewById(R.id.chatEmptyText);
        truncatedBanner = findViewById(R.id.truncatedBanner);
        readOnlyBanner = findViewById(R.id.readOnlyBanner);
        chatConnectionDot = findViewById(R.id.chatConnectionDot);

        chatTitleText.setText(sessionTitle.isEmpty() ? getString(R.string.chat_title) : sessionTitle);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        messageList.setLayoutManager(layoutManager);
        adapter = new MessageAdapter(app.mediaLoader(), this, this);
        messageList.setAdapter(adapter);
        ru.billyhargrove.pimobile.ui.ExpressiveMotion.list(messageList);

        updateBehavior();
        behaviorMenu.setOnClickListener(v -> {
            androidx.appcompat.widget.PopupMenu menu=new androidx.appcompat.widget.PopupMenu(this,behaviorMenu);
            menu.getMenu().add(0,1,0,"В очередь после задачи");menu.getMenu().add(0,2,1,"Вмешаться в текущую задачу");
            menu.setOnMenuItemClickListener(item->{settings.setBehavior(item.getItemId()==2?CommandBuilder.Behavior.STEER:CommandBuilder.Behavior.FOLLOW_UP);updateBehavior();return true;});menu.show();
        });
        findViewById(R.id.historyProgress).setOnClickListener(v->loadOlder());
        messageList.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrolled(RecyclerView list,int dx,int dy){if(dy<0&&((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition()<3)loadOlder();}});

        backButton.setOnClickListener(v -> finishAfterTransition());
        sendButton.setOnClickListener(v -> onSendClicked());
        attachButton.setOnClickListener(v -> imagePicker.launch(new String[]{"image/*"}));
        stopButton.setOnClickListener(v -> onStopClicked());
        findViewById(R.id.modelButton).setOnClickListener(v -> openModelSettings());

        if (readOnly) {
            readOnlyBanner.setVisibility(View.VISIBLE);
            findViewById(R.id.composerContainer).setVisibility(View.GONE);
        }

        // Edge-to-edge (targetSdk 35): the header eats the status bar inset and the
        // root bottom follows the IME so the composer is never hidden by the keyboard
        // or by the navigation bar.
        SystemInsets.apply(this,
                findViewById(R.id.chatRoot),
                findViewById(R.id.chatTopBar),
                null,
                false);

        render();
        updateStatusUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        client.setListener(this);
        configuration = client.configuration(sessionId);
        client.subscribe(sessionId);
        Snapshot cached = client.lastSnapshot();
        if (cached != null && sessionId.equals(cached.sessionId())) {
            applySnapshot(cached);
        }
        if (client.state() != ConnectionState.CONNECTED) {
            fetchSnapshotOverRest();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        client.clearListener(this);
    }

    @Override protected void onDestroy() {
        if (modelSheet != null) modelSheet.dismiss();
        if(documentPreview!=null)documentPreview.dismiss();
        if(adapter!=null)adapter.close();
        super.onDestroy();
    }

    @Override public void onConfiguration(String id, org.json.JSONObject value) {
        if (!sessionId.equals(id)) return;
        configuration = value;
        updateStatusUi();
    }

    private void openModelSettings() {
        if(configuration == null || configuration.optJSONArray("models") == null) {
            toast("Выполни /reload в этом Pi, когда он освободится, чтобы загрузить модели и effort.");return;
        }
        if(modelSheet != null)modelSheet.dismiss();
        modelSheet=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet(this,configuration,client.state()==ConnectionState.CONNECTED && sessionStatus==SessionStatus.IDLE && !readOnly,(provider,model,effort)->{
            if(sessionStatus!=SessionStatus.IDLE){modelSheet.failed("Дождись завершения задачи");return;}
            configurationRequest=client.configure(sessionId,provider,model,effort);
            if(configurationRequest==null)modelSheet.failed("Нет соединения с Pi. Изменения не отправлены.");
        });
        modelSheet.show();
    }

    private void updateBehavior(){behaviorMenu.setText(settings.behavior()==CommandBuilder.Behavior.STEER?"Вмешаться ▾":"Очередь ▾");}

    @Override public void onTimelineMeta(org.json.JSONObject frame){
        if(!sessionId.equals(frame.optString("sessionId")))return;
        adapter.metadata(frame);
        if("snapshot".equals(frame.optString("type"))){
            historyEpoch=frame.optLong("epoch");historyRequest=null;
            org.json.JSONObject h=frame.optJSONObject("history");hasMoreHistory=h!=null&&h.optBoolean("hasMore");beforeCursor=h==null?"":h.optString("before","");historyButton();
        }
    }
    private void historyButton(){Button b=findViewById(R.id.historyProgress);b.setVisibility(hasMoreHistory?View.VISIBLE:View.GONE);b.setEnabled(historyRequest==null);b.setText(historyRequest==null?"↑ Предыдущие сообщения":"Загружаю…");}
    private void loadOlder(){if(!hasMoreHistory||historyRequest!=null||beforeCursor.isEmpty())return;try{historyRequest=client.readCommand(sessionId,"history",new org.json.JSONObject().put("before",beforeCursor).put("limit",40));historyButton();if(historyRequest==null)toast("Для загрузки истории нужен подключённый Pi");}catch(org.json.JSONException ignored){}}
    @Override public void onData(String request,String id,org.json.JSONObject data){
        if(!sessionId.equals(id))return;
        if("history".equals(data.optString("type"))&&request.equals(historyRequest)){
            if(data.optLong("epoch")!=historyEpoch){historyRequest=null;historyButton();return;}
            LinearLayoutManager lm=(LinearLayoutManager)messageList.getLayoutManager();int first=lm.findFirstVisibleItemPosition();String anchor=adapter.keyAt(first);View top=lm.findViewByPosition(first);int offset=top==null?0:top.getTop()-messageList.getPaddingTop();
            store.prepend(ru.billyhargrove.pimobile.core.SnapshotParser.parseMessages(data.optJSONArray("messages")));adapter.metadata(data);render();
            int position=adapter.positionOf(anchor);if(position>=0)lm.scrollToPositionWithOffset(position,offset);
            org.json.JSONObject h=data.optJSONObject("history");if(h!=null){beforeCursor=h.optString("before");hasMoreHistory=h.optBoolean("hasMore");}historyButton();
        }else if("document".equals(data.optString("type"))&&request.equals(documentRequest)){
            if(documentPreview!=null)documentPreview.dismiss();String path=data.optString("path");
            documentPreview=new ru.billyhargrove.pimobile.ui.MarkdownPreview(this,path,data.optString("text"),link->{String parent=path.contains("/")?path.substring(0,path.lastIndexOf('/')+1):"";onDocument(link.startsWith("/")||link.contains(":")?link:parent+link);});documentPreview.show();
        }
    }
    @Override public void onDocument(String raw){
        android.net.Uri uri=android.net.Uri.parse(raw);String scheme=uri.getScheme();
        if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){toast("Нет приложения для этой ссылки");}return;}
        if(scheme!=null&&!"file".equalsIgnoreCase(scheme)){toast("Этот тип ссылки не поддерживается");return;}
        String path=scheme==null?raw:uri.getPath();if(path==null)return;int hash=path.indexOf('#');if(hash>=0)path=path.substring(0,hash);path=android.net.Uri.decode(path);
        if(!path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(md|markdown)$")){toast("Предпросмотр поддерживает .md и .markdown");return;}
        if(documentRequest!=null){toast("Файл уже загружается");return;}
        try{documentRequest=client.readCommand(sessionId,"document",new org.json.JSONObject().put("path",path));if(documentRequest==null)toast("Для предпросмотра нужен подключённый Pi");}catch(org.json.JSONException ignored){}
    }

    // -------------------------------------------------------------- commands

    private void onSendClicked() {
        if (readOnly) {
            return;
        }
        String text = composerInput.getText().toString();
        List<ImagePayload> payloads = new ArrayList<>();
        List<Bitmap> thumbs = new ArrayList<>();
        List<String> mimeTypes = new ArrayList<>();
        for (Attachment attachment : attachments) {
            payloads.add(attachment.payload());
            thumbs.add(attachment.thumbnail());
            mimeTypes.add(attachment.payload().mimeType());
        }
        if (text.trim().isEmpty() && payloads.isEmpty()) {
            toast(getString(R.string.error_empty_message));
            return;
        }
        CommandBuilder.Behavior behavior = settings.behavior();

        String requestId = client.sendPrompt(sessionId, text, payloads, behavior);
        if (requestId == null) {
            // Nothing was written to the socket: keep the composer exactly as it is.
            toast(getString(R.string.error_no_connection));
            return;
        }
        drafts.put(requestId, new Draft(text, payloads, thumbs, mimeTypes, behavior));
        localMessages.add(ChatMessage.local(requestId, text, localRefs(mimeTypes), ChatMessage.LocalState.SENDING));
        sendButton.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);
        composerInput.setText("");
        clearAttachments();
        render();
        scrollToBottom(true);
    }

    private void onStopClicked() {
        if (readOnly) {
            return;
        }
        String requestId = client.sendAbort(sessionId);
        if (requestId == null) {
            toast(getString(R.string.stop_unavailable));
            return;
        }
        abortRequests.add(requestId);
        toast(getString(R.string.stop_requested));
    }

    @Override
    public void onRetry(ChatMessage message) {
        Draft draft = drafts.get(message.requestId());
        if (draft == null) {
            return;
        }
        toast(getString(R.string.retry_warning));
        String requestId = client.sendPrompt(sessionId, draft.text, draft.payloads, draft.behavior);
        if (requestId == null) {
            toast(getString(R.string.error_no_connection));
            return;
        }
        drafts.put(requestId, new Draft(draft.text, draft.payloads, draft.thumbs, draft.mimeTypes, draft.behavior));
        localMessages.add(ChatMessage.local(requestId, draft.text, localRefs(draft.mimeTypes),
                ChatMessage.LocalState.SENDING));
        render();
        scrollToBottom(true);
    }

    @Override
    public void onRestore(ChatMessage message) {
        Draft draft = drafts.remove(message.requestId());
        if (draft == null) {
            return;
        }
        if (composerInput.getText().length() == 0) {
            composerInput.setText(draft.text);
            composerInput.setSelection(composerInput.getText().length());
        }
        if (!draft.payloads.isEmpty()) {
            attachments.clear();
            for (int i = 0; i < draft.payloads.size(); i++) {
                Bitmap thumb = i < draft.thumbs.size() ? draft.thumbs.get(i) : null;
                attachments.add(new Attachment(draft.payloads.get(i), thumb, "восстановлено"));
            }
            renderAttachments();
        }
        int index = indexOfLocal(message.requestId());
        if (index >= 0) {
            localMessages.remove(index);
        }
        render();
    }

    // ------------------------------------------------------------ image picker

    private void onImagesPicked(List<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return;
        }
        int free = ImageGuard.MAX_IMAGES - attachments.size();
        if (free <= 0) {
            toast(getString(R.string.error_attach_limit));
            return;
        }
        if (uris.size() > free) {
            toast(getString(R.string.error_attach_limit));
        }
        final List<Uri> selected = new ArrayList<>(uris.subList(0, Math.min(free, uris.size())));
        AppExecutors.io().execute(() -> {
            List<Attachment> staged = new ArrayList<>();
            String error = null;
            long budget = ImageGuard.remainingBytes(payloadsOf(attachments));
            for (Uri uri : selected) {
                try {
                    ImagePayload payload = ImagePreparer.prepare(getContentResolver(), uri, budget);
                    budget -= payload.size();
                    Bitmap thumb = ImagePreparer.thumbnail(payload.bytes(), 320);
                    staged.add(new Attachment(payload, thumb, ImagePreparer.displayName(getContentResolver(), uri)));
                } catch (Exception e) {
                    error = e.getMessage() == null ? "не удалось прочитать файл" : e.getMessage();
                    break;
                }
            }
            final List<Attachment> stagedFinal = staged;
            final String failure = error;
            AppExecutors.main(() -> {
                attachments.addAll(stagedFinal);
                renderAttachments();
                if (failure != null) {
                    toast(getString(R.string.error_attach_failed, failure));
                }
            });
        });
    }

    private static List<ImagePayload> payloadsOf(List<Attachment> list) {
        List<ImagePayload> payloads = new ArrayList<>();
        for (Attachment attachment : list) {
            payloads.add(attachment.payload());
        }
        return payloads;
    }

    private void renderAttachments() {
        attachmentStrip.removeAllViews();
        if (attachments.isEmpty()) {
            attachmentsScroll.setVisibility(View.GONE);
            attachmentsTitle.setVisibility(View.GONE);
            return;
        }
        attachmentsScroll.setVisibility(View.VISIBLE);
        attachmentsTitle.setVisibility(View.VISIBLE);
        attachmentsTitle.setText(getString(R.string.attachments_title, attachments.size()));
        for (int i = 0; i < attachments.size(); i++) {
            final int index = i;
            Attachment attachment = attachments.get(i);
            View item = getLayoutInflater().inflate(R.layout.item_attachment, attachmentStrip, false);
            ImageView thumb = item.findViewById(R.id.attachmentThumb);
            Button remove = item.findViewById(R.id.attachmentRemove);
            if (attachment.thumbnail() != null) {
                thumb.setImageBitmap(attachment.thumbnail());
            }
            thumb.setContentDescription(getString(R.string.cd_attachment_thumb, index + 1));
            remove.setContentDescription(getString(R.string.cd_attachment_remove, index + 1));
            remove.setOnClickListener(v -> {
                if (index < attachments.size()) {
                    attachments.remove(index);
                    renderAttachments();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            params.rightMargin = Math.round(getResources().getDisplayMetrics().density * 8);
            item.setLayoutParams(params);
            attachmentStrip.addView(item);
        }
    }

    private void clearAttachments() {
        attachments.clear();
        renderAttachments();
    }

    // ------------------------------------------------------- PiClient.Listener

    @Override
    public void onConnectionState(ConnectionState state, String detail) {
        updateStatusUi();
    }

    @Override
    public void onCatalog(Catalog catalog) {
        Session session = catalog.findSession(sessionId);
        if (session != null) {
            sessionStatus = session.status();
            if (!session.displayTitle().isEmpty()) {
                sessionTitle = session.displayTitle();
                chatTitleText.setText(sessionTitle);
            }
        }
        updateStatusUi();
    }

    @Override
    public void onSnapshot(Snapshot snapshot) {
        if (!sessionId.equals(snapshot.sessionId())) {
            return;
        }
        applySnapshot(snapshot);
    }

    /**
     * Incremental update: only new/changed messages and removed ids. The store keeps
     * the canonical order, so a message whose text grows stays exactly where it is.
     */
    @Override
    public void onMessages(MessagesUpdate update) {
        if (!sessionId.equals(update.sessionId())) {
            return;
        }
        if (update.hasStatus()) {
            sessionStatus = update.status();
        }
        if (update.hasTruncated()) {
            truncatedBanner.setVisibility(update.truncated() ? View.VISIBLE : View.GONE);
        }
        // `connected` is advisory here: the badge follows `status`, which the
        // protocol defines as the authoritative activity indicator.
        render(store.apply(update.messages(), update.removedIds()));
        updateStatusUi();
    }

    @Override
    public void onAck(Ack ack) {
        if(ack.requestId().equals(historyRequest)){historyRequest=null;historyButton();if(!ack.ok())toast(errorText(ack));return;}
        if(ack.requestId().equals(documentRequest)){documentRequest=null;if(!ack.ok())toast(errorText(ack));return;}
        if (ack.requestId().equals(configurationRequest)) {
            configurationRequest=null;
            if(ack.ok()){if(modelSheet!=null)modelSheet.dismiss();toast("Настройки текущего Pi изменены");}
            else if(modelSheet!=null)modelSheet.failed(errorText(ack));
            return;
        }
        if (abortRequests.remove(ack.requestId())) {
            if (!ack.ok()) {
                toast(getString(R.string.ack_failed_format, errorText(ack)));
            }
            return;
        }
        Draft draft = drafts.get(ack.requestId());
        if (draft == null) {
            return;
        }
        int index = indexOfLocal(ack.requestId());
        if (index < 0) {
            return;
        }
        ChatMessage message = localMessages.get(index);
        if (ack.ok()) {
            localMessages.set(index, message.withLocalState(ChatMessage.LocalState.ACCEPTED));
        } else {
            localMessages.set(index, message.withLocalState(ChatMessage.LocalState.FAILED));
            toast(getString(R.string.ack_failed_format, errorText(ack)));
            if (composerInput.getText().length() == 0) {
                composerInput.setText(draft.text);
                composerInput.setSelection(composerInput.getText().length());
            }
        }
        render();
    }

    @Override
    public void onCommandUncertain(String requestId, String sessionId, String reason) {
        if(requestId.equals(historyRequest)){historyRequest=null;historyButton();toast("История не загружена: "+reason);return;}
        if(requestId.equals(documentRequest)){documentRequest=null;toast("Файл не загружен: "+reason);return;}
        if(requestId.equals(configurationRequest)) {
            configurationRequest=null;
            if(modelSheet!=null)modelSheet.failed("Результат неизвестен. Проверь модель в терминале перед повтором.");
            return;
        }
        if (abortRequests.remove(requestId)) {
            toast(getString(R.string.uncertain_format, reason));
            return;
        }
        int index = indexOfLocal(requestId);
        if (index < 0) {
            return;
        }
        localMessages.set(index, localMessages.get(index).withLocalState(ChatMessage.LocalState.UNCERTAIN));
        toast(getString(R.string.uncertain_format, reason));
        render();
    }

    @Override
    public void onProtocolError(String message) {
        toast(getString(R.string.protocol_error_format, message));
    }

    // ------------------------------------------------------ MediaLoader callback

    @Override
    public Bitmap thumbnail(String requestId, int index) {
        Draft draft = drafts.get(requestId);
        if (draft == null || index < 0 || index >= draft.thumbs.size()) {
            return null;
        }
        return draft.thumbs.get(index);
    }

    // ----------------------------------------------------------------- helpers

    private void applySnapshot(Snapshot snapshot) {
        sessionStatus = snapshot.status();
        truncatedBanner.setVisibility(snapshot.truncated() ? View.VISIBLE : View.GONE);
        render(store.replaceAll(snapshot.messages()));
        updateStatusUi();
    }

    private void fetchSnapshotOverRest() {
        if (!settings.hasToken()) {
            return;
        }
        final String baseUrl = settings.baseUrl();
        final String token = settings.token();
        AppExecutors.io().execute(() -> {
            Snapshot snapshot = null;
            try {
                snapshot = app.api().fetchSnapshot(baseUrl, token, sessionId);
            } catch (Exception ignored) {
                // The WebSocket subscribe is the primary path; REST is a warm-up.
            }
            final Snapshot result = snapshot;
            if (result == null) {
                return;
            }
            AppExecutors.main(() -> {
                if (sessionId.equals(result.sessionId())) {
                    applySnapshot(result);
                }
            });
        });
    }

    private void render() {
        adapter.submit(TranscriptReconciler.merge(store.transcript(), localMessages));
        chatEmptyText.setVisibility(adapter.size() == 0 ? View.VISIBLE : View.GONE);
        pruneDrafts();
    }

    /**
     * Renders a transcript change. Scroll position is preserved unless the tail
     * changed (a new message or the streaming message growing) and the user is
     * already at the bottom.
     */
    private void render(TranscriptStore.ChangeSet changeSet) {
        render();
        boolean firstLoad = !firstRenderDone && adapter.size() > 0;
        if (firstLoad || (changeSet != null && changeSet.tailTouched())) {
            scrollToBottom(firstLoad);
        }
        if (firstLoad) {
            firstRenderDone = true;
        }
    }

    /** A draft is only needed while its local bubble is still on screen. */
    private void pruneDrafts() {
        Set<String> alive = new HashSet<>();
        for (ChatMessage message : localMessages) {
            alive.add(message.requestId());
        }
        drafts.keySet().retainAll(alive);
    }

    private void updateStatusUi() {
        boolean connected = client.state() == ConnectionState.CONNECTED;
        String model = configuration == null ? "" : configuration.optString("model", "");
        if(model.contains("/"))model=model.substring(model.lastIndexOf('/')+1);
        String detail=model.isEmpty()?"На связи":model+" · "+configuration.optString("thinkingLevel","off");
        chatStatusText.setText(!connected ? StatusUi.connectionLabel(this, client.state()) : sessionStatus == SessionStatus.OFFLINE ? "Pi отключён · история доступна" : sessionStatus==SessionStatus.RUNNING?"Работает":"Подключено");
        ((Button)findViewById(R.id.modelButton)).setText(detail+" ▾");
        findViewById(R.id.modelButton).setContentDescription("Модель и effort: " + detail);
        chatConnectionDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                StatusUi.sessionDotColor(this, sessionStatus, connected && sessionStatus != SessionStatus.OFFLINE)));
        boolean online = client.state() == ConnectionState.CONNECTED && sessionStatus != SessionStatus.OFFLINE && !readOnly;
        stopButton.setVisibility(sessionStatus == SessionStatus.RUNNING ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(online);
        sendButton.setEnabled(online);
        findViewById(R.id.thinkingStrip).setVisibility(online && sessionStatus == SessionStatus.RUNNING ? View.VISIBLE : View.GONE);
    }

    private int indexOfLocal(String requestId) {
        for (int i = 0; i < localMessages.size(); i++) {
            if (requestId.equals(localMessages.get(i).requestId())) {
                return i;
            }
        }
        return -1;
    }

    private static List<ImageRef> localRefs(List<String> mimeTypes) {
        List<ImageRef> refs = new ArrayList<>();
        for (int i = 0; i < mimeTypes.size(); i++) {
            refs.add(MessageAdapter.localRef(i, mimeTypes.get(i)));
        }
        return refs;
    }

    private boolean isNearBottom() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) messageList.getLayoutManager();
        if (layoutManager == null || adapter.size() == 0) {
            return true;
        }
        return layoutManager.findLastVisibleItemPosition() >= adapter.size() - 3;
    }

    private void scrollToBottom(boolean force) {
        if (!force && !isNearBottom()) {
            return;
        }
        final int last = adapter.size() - 1;
        if (last < 0) {
            return;
        }
        messageList.post(() -> {
            if (force || !ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled()) messageList.scrollToPosition(last);
            else messageList.smoothScrollToPosition(last);
        });
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static String errorText(Ack ack) {
        if (ack == null || ack.error() == null || ack.error().isEmpty()) {
            return "без подробностей";
        }
        return ack.error();
    }
}
