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
import java.util.List;
import java.util.Set;

import ru.billyhargrove.pimobile.core.Ack;
import ru.billyhargrove.pimobile.core.Catalog;
import ru.billyhargrove.pimobile.core.ChatMessage;
import ru.billyhargrove.pimobile.core.CommandBuilder;
import ru.billyhargrove.pimobile.core.ConnectionState;
import ru.billyhargrove.pimobile.core.ImageGuard;
import ru.billyhargrove.pimobile.core.ImagePayload;
import ru.billyhargrove.pimobile.core.MessagesUpdate;
import ru.billyhargrove.pimobile.core.Session;
import ru.billyhargrove.pimobile.core.SessionStatus;
import ru.billyhargrove.pimobile.core.Snapshot;
import ru.billyhargrove.pimobile.core.TranscriptStore;
import ru.billyhargrove.pimobile.features.chat.ChatOutbox;
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
 * marked "status unknown" and is never replayed automatically – only an
 * explicit tap on "Retry manually" sends it again.</p>
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

    private PiApp app;
    private PiClient client;
    private SettingsStore settings;

    private MessageAdapter adapter;
    private RecyclerView messageList;
    private EditText composerInput;
    private ru.billyhargrove.pimobile.ui.ChatLoading loadingUi;
    private Button sendButton;
    private ru.billyhargrove.pimobile.ui.DictationRecorder dictation;
    private boolean transcribing;
    private boolean preparingAttachments;
    private final ActivityResultLauncher<String> microphonePermission=registerForActivityResult(new ActivityResultContracts.RequestPermission(),granted->{if(granted)startDictation();else toast("Microphone permission is required for dictation");});
    private Button attachButton;
    private Button stopButton;
    private Button backButton;
    private String historyRequest,documentRequest,beforeCursor="";
    private boolean hasMoreHistory;
    private long historyEpoch;
    private ru.billyhargrove.pimobile.ui.MarkdownPreview documentPreview;
    private LinearLayout attachmentStrip;
    private HorizontalScrollView attachmentsScroll;
    private TextView attachmentsTitle;
    private TextView chatTitleText;
    private TextView chatStatusText;
    private ru.billyhargrove.pimobile.ui.OrchestrationEntry orchestration;
    private TextView chatEmptyText;
    private TextView truncatedBanner;
    private TextView readOnlyBanner;
    private View chatConnectionDot;

    private String sessionId = "";
    private String sessionTitle = "";
    private boolean readOnly;
    private ru.billyhargrove.pimobile.ui.ModelSettingsSheet modelSheet;
    private ru.billyhargrove.pimobile.ui.EffortPopup effortPopup;
    private ru.billyhargrove.pimobile.ui.DeliverySheet deliverySheet;
    private String configurationRequest, controlRequest, controlDraft;
    private String controlKind;
    private org.json.JSONObject configuration;
    private SessionStatus sessionStatus = SessionStatus.UNKNOWN;

    /** Canonical transcript: full snapshots plus incremental {type:'messages'} frames. */
    private final TranscriptStore store = new TranscriptStore();
    private boolean firstRenderDone;
    private ChatOutbox outbox;
    private ru.billyhargrove.pimobile.store.PendingMessages pendingMessages;
    private final List<Attachment> attachments = new ArrayList<>();
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
        pendingMessages=new ru.billyhargrove.pimobile.store.PendingMessages(this,settings.baseUrl(),sessionId);
        outbox=new ChatOutbox(sessionId,readOnly,client::sendPrompt,pendingMessages.load());

        int background=getColor(R.color.bg);findViewById(R.id.messageTopFade).setBackground(new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,new int[]{background,background&0x00ffffff}));
        messageList = findViewById(R.id.messageList);
        composerInput = findViewById(R.id.composerInput);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachImageButton);
        stopButton = findViewById(R.id.stopButton);
        backButton = findViewById(R.id.backButton);
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
        orchestration=new ru.billyhargrove.pimobile.ui.OrchestrationEntry(this,findViewById(R.id.orchestrationButton),sessionId);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        messageList.setLayoutManager(layoutManager);
        adapter = new MessageAdapter(app.mediaLoader(), this, this);
        messageList.setAdapter(adapter);
        // Live diffs must not animate row relocation or steal the reading anchor.
        messageList.setItemAnimator(null);
        loadingUi=new ru.billyhargrove.pimobile.ui.ChatLoading((android.widget.FrameLayout)messageList.getParent());

        new ru.billyhargrove.pimobile.ui.CompactComposer(findViewById(R.id.composerEditor), composerInput);
        new ru.billyhargrove.pimobile.ui.SkillSuggestions(findViewById(R.id.skillSuggestions),composerInput,()->configuration);
        messageList.addOnScrollListener(new RecyclerView.OnScrollListener(){@Override public void onScrollStateChanged(RecyclerView list,int state){if(state==RecyclerView.SCROLL_STATE_DRAGGING){scrollGeneration++;followTail=false;}if(state==RecyclerView.SCROLL_STATE_IDLE)followTail=isNearBottom();}@Override public void onScrolled(RecyclerView list,int dx,int dy){if(dy<0&&list.getScrollState()!=RecyclerView.SCROLL_STATE_IDLE&&((LinearLayoutManager)list.getLayoutManager()).findFirstVisibleItemPosition()<3)loadOlder();}});

        backButton.setOnClickListener(v -> finishAfterTransition());
        sendButton.setOnClickListener(v -> {if(composerInput.getText().toString().trim().isEmpty()&&attachments.isEmpty())requestDictation();else onSendClicked();});
        composerInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){updateSendIcon();}public void afterTextChanged(android.text.Editable e){}});updateSendIcon();
        attachButton.setOnClickListener(v -> imagePicker.launch(new String[]{"*/*"}));
        stopButton.setOnClickListener(v -> onStopClicked());
        findViewById(R.id.effortButton).setOnClickListener(v -> openQuickEffort(v));
        findViewById(R.id.deliveryButton).setOnClickListener(v->{if(deliverySheet!=null)deliverySheet.dismiss();deliverySheet=new ru.billyhargrove.pimobile.ui.DeliverySheet(this,settings.behavior(),mode->{settings.setBehavior(mode);updateDeliveryIcon();});deliverySheet.show();});updateDeliveryIcon();

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
        orchestration.start();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if(orchestration!=null)orchestration.stop();
        if(dictation!=null){dictation.cancel();dictation=null;}
        LinearLayoutManager layout=(LinearLayoutManager)messageList.getLayoutManager();int first=layout.findFirstVisibleItemPosition();View top=layout.findViewByPosition(first);if(first>=0&&top!=null)client.saveViewport(sessionId,adapter.keyAt(first),top.getTop()-messageList.getPaddingTop(),followTail);
        client.clearListener(this);
    }

    @Override protected void onDestroy() {
        if (deliverySheet != null) deliverySheet.dismiss();
        if (modelSheet != null) modelSheet.dismiss();
        if (effortPopup != null) effortPopup.dismiss();
        if(documentPreview!=null)documentPreview.dismiss();
        if(adapter!=null)adapter.close();
        if(loadingUi!=null)loadingUi.close();
        super.onDestroy();
    }

    @Override public void onConfiguration(String id, org.json.JSONObject value) {
        if (!sessionId.equals(id)) return;
        configuration = value;
        updateStatusUi();
    }

    private void openQuickEffort(View anchor) {
        if (configurationRequest != null) return;
        if (configuration == null || client.state() != ConnectionState.CONNECTED) { toast("Connect to Pi first"); return; }
        org.json.JSONArray models = configuration.optJSONArray("models");
        org.json.JSONObject selected = null;
        if (models != null) for (int i = 0; i < models.length(); i++) { org.json.JSONObject m = models.optJSONObject(i); if (m != null && (m.optString("provider") + "/" + m.optString("id")).equals(configuration.optString("model"))) selected = m; }
        if (selected == null) { openModelSettings(); return; }
        if (effortPopup != null) effortPopup.dismiss();
        effortPopup = new ru.billyhargrove.pimobile.ui.EffortPopup(anchor, selected, configuration.optString("thinkingLevel", "off"), !readOnly, this::openModelSettings, value -> {
            if (configurationRequest != null) { toast("Waiting for the previous change to be confirmed"); return; }
            configurationRequest = client.configure(sessionId, null, null, value);
            if (configurationRequest == null) toast("Pi is disconnected. Change not sent");
        },configuration.optString("serviceTier","standard"),tier->{if(configurationRequest!=null)return;configurationRequest=client.configure(sessionId,null,null,null,tier);if(configurationRequest==null)toast("Pi is disconnected. Change not sent");});
    }

    private void openModelSettings() {
        if (configurationRequest != null) return;
        if (effortPopup != null) effortPopup.dismiss();
        if(configuration == null || configuration.optJSONArray("models") == null) {
            toast("Run /reload in Pi when idle to load models and effort levels.");return;
        }
        if(modelSheet != null)modelSheet.dismiss();
        ((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(composerInput.getWindowToken(),0);
        modelSheet=new ru.billyhargrove.pimobile.ui.ModelSettingsSheet(this,configuration,client.state()==ConnectionState.CONNECTED && sessionStatus==SessionStatus.IDLE && !readOnly,(provider,model,effort,tier)->{
            if(sessionStatus!=SessionStatus.IDLE){modelSheet.failed("Wait for the current task to finish");return;}
            configurationRequest=client.configure(sessionId,provider,model,effort,tier);
            if(configurationRequest==null)modelSheet.failed("Pi is disconnected. Changes not sent.");
        });
        modelSheet.show();
    }

    private boolean cachedView;
    @Override public void onTimelineMeta(org.json.JSONObject frame){
        if(!sessionId.equals(frame.optString("sessionId")))return;
        cachedView=frame.optBoolean("cached");
        adapter.metadata(frame);
        ((ru.billyhargrove.pimobile.ui.WorkingBadge)findViewById(R.id.workingBadge)).update(frame);
        if("snapshot".equals(frame.optString("type"))){
            historyEpoch=frame.optLong("epoch");historyRequest=null;
            org.json.JSONObject h=frame.optJSONObject("history");hasMoreHistory=h!=null&&h.optBoolean("hasMore");beforeCursor=h==null?"":h.optString("before","");historyButton();
        }
    }
    private void historyButton(){if(loadingUi!=null)loadingUi.history(historyRequest!=null);}
    private void ensureUserContext(){
        // A long tool run can evict ALL user messages from the 40-source-message tail.
        // Fetch preceding pages until the current prompt is present; never move the viewport up.
        if(hasMoreHistory && store.transcript().stream().noneMatch(m->m.role()==ChatMessage.Role.USER))loadOlder();
    }
    private void loadOlder(){if(!hasMoreHistory||historyRequest!=null||beforeCursor.isEmpty())return;try{historyRequest=client.readCommand(sessionId,"history",new org.json.JSONObject().put("before",beforeCursor).put("limit",40));historyButton();if(historyRequest==null)toast("Pi must be connected to load history");}catch(org.json.JSONException ignored){}}
    @Override public void onData(String request,String id,org.json.JSONObject data){
        if(!sessionId.equals(id))return;
        if("mcp".equals(data.optString("type"))&&request.equals(controlRequest)){
            org.json.JSONArray servers=data.optJSONArray("servers");StringBuilder text=new StringBuilder();
            if(servers!=null)for(int i=0;i<servers.length();i++){org.json.JSONObject s=servers.optJSONObject(i);if(s==null)continue;String status=s.optString("status");String label="connected".equals(status)?"connected":"cached".equals(status)?"cached, disconnected":"not-connected".equals(status)?"not connected":"needs-auth".equals(status)?"sign-in required":"disabled".equals(status)?"disabled":"blocked".equals(status)?"blocked":"error";text.append(s.optString("name")).append(" — ").append(label).append(" · ").append(s.optInt("toolCount")).append(" tools\n\n");}
            if(text.length()==0)text.append("No MCP servers in this Pi session.");
            long observed=data.optLong("observedAt");if(observed>0)text.append("Status reported by Pi: ").append(android.text.format.DateFormat.getTimeFormat(this).format(new java.util.Date(observed)));
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(this).setTitle("Session MCP servers").setMessage(text.toString()).setPositiveButton("Done",null).show();return;
        }
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
        if("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)){try{startActivity(new Intent(Intent.ACTION_VIEW,uri));}catch(Exception e){toast("No app available to open this link");}return;}
        if(scheme!=null&&!"file".equalsIgnoreCase(scheme)){toast("This link type is not supported");return;}
        String path=scheme==null?raw:uri.getPath();if(path==null)return;int hash=path.indexOf('#');if(hash>=0)path=path.substring(0,hash);path=android.net.Uri.decode(path);
        if(!path.toLowerCase(java.util.Locale.ROOT).matches(".*\\.(md|markdown)$")){toast("Preview supports .md and .markdown files");return;}
        if(documentRequest!=null){toast("A file is already loading");return;}
        try{documentRequest=client.readCommand(sessionId,"document",new org.json.JSONObject().put("path",path));if(documentRequest==null)toast("Pi must be connected to preview files");}catch(org.json.JSONException ignored){}
    }

    private void updateDeliveryIcon(){MaterialButton button=findViewById(R.id.deliveryButton);boolean steer=settings.behavior()==CommandBuilder.Behavior.STEER;button.setIconResource(steer?R.drawable.ic_steer:R.drawable.ic_queue);button.setContentDescription(steer?"Delivery: Steer":"Delivery: Queue");}
    private boolean hasCapability(String name){org.json.JSONArray caps=configuration==null?null:configuration.optJSONArray("capabilities");if(caps!=null)for(int i=0;i<caps.length();i++)if(name.equals(caps.optString(i)))return true;return false;}
    private boolean handleControl(String text){
        String kind=text.equals("/mcp")?"mcp":text.equals("/name")||text.startsWith("/name ")?"name":null;
        if(kind==null)return false;
        if(!attachments.isEmpty()){toast("This command does not send attachments. Remove them first");return true;}
        if(controlRequest!=null){toast("The previous command is still pending");return true;}
        if(!hasCapability(kind)){toast("Update the Pi bridge after the current task finishes");return true;}
        try{org.json.JSONObject args=new org.json.JSONObject();if("name".equals(kind)){String name=text.substring(5).trim();if(name.isEmpty()){toast("Use /name New name");return true;}args.put("name",name);}
            controlRequest=client.readCommand(sessionId,kind,args);if(controlRequest==null)toast("Not connected to this Pi session");else{controlKind=kind;controlDraft=text;}
        }catch(org.json.JSONException ignored){}return true;
    }

    // -------------------------------------------------------------- commands

    private void updateSendIcon(){if(sendButton==null)return;boolean voice=composerInput.getText().toString().trim().isEmpty()&&attachments.isEmpty();((com.google.android.material.button.MaterialButton)sendButton).setIconResource(voice?R.drawable.ic_mic:R.drawable.ic_send);sendButton.setContentDescription(transcribing?"Transcribing…":voice?"Dictation":"Send");}
    private void requestDictation(){if(transcribing){toast("The previous recording is still being transcribed");return;}if(androidx.core.content.ContextCompat.checkSelfPermission(this,android.Manifest.permission.RECORD_AUDIO)!=android.content.pm.PackageManager.PERMISSION_GRANTED)microphonePermission.launch(android.Manifest.permission.RECORD_AUDIO);else startDictation();}
    private void startDictation(){
        if(isFinishing()||isDestroyed())return;
        dictation=new ru.billyhargrove.pimobile.ui.DictationRecorder(this,bytes->{dictation=null;transcribing=true;updateSendIcon();toast("Transcribing on your computer…");String url=settings.baseUrl(),token=settings.token();
            AppExecutors.io().execute(()->{String text=null,error=null;try{text=app.api().transcribe(url,token,bytes);}catch(Exception e){error=e.getMessage();}finally{bytes.delete();}String result=text,failure=error;AppExecutors.main(()->{transcribing=false;if(isFinishing()||isDestroyed())return;updateSendIcon();if(failure!=null){toast(failure);return;}if(result==null||result.isBlank()){toast("No speech detected");return;}int at=Math.max(0,composerInput.getSelectionStart());composerInput.getText().insert(at,(at>0?" ":"")+result);});});
        },this::toast);
    }

    private void onSendClicked() {
        if(preparingAttachments){toast("Wait for attachments to finish preparing");return;}
        if (readOnly) {
            return;
        }
        String text = composerInput.getText().toString();
        if(handleControl(text.trim()))return;
        if(text.startsWith("$")&&!hasCapability("skills")){toast("Update the Pi bridge when idle to use skills");return;}
        ChatOutbox.SendResult result=outbox.send(text,attachments,settings.behavior());
        if (result.getStatus() == ChatOutbox.SendStatus.EMPTY) {
            toast(getString(R.string.error_empty_message));
            return;
        }
        if (result.getStatus() != ChatOutbox.SendStatus.WRITTEN) {
            // Nothing was written to the socket: keep the composer exactly as it is.
            toast(getString(R.string.error_no_connection));
            return;
        }
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
        if(readOnly)return;
        ChatOutbox.SendResult result=outbox.retry(message.requestId());
        if (result.getStatus() == ChatOutbox.SendStatus.MISSING_DRAFT) {
            toast("Restore the draft and reattach files. Check whether Pi already accepted the message before retrying.");return;
        }
        if (result.getStatus() == ChatOutbox.SendStatus.NO_CONNECTION) {
            toast(getString(R.string.error_no_connection));
            return;
        }
        if(result.getStatus()!=ChatOutbox.SendStatus.WRITTEN)return;
        toast(getString(R.string.retry_warning));
        render();
        scrollToBottom(true);
    }

    @Override
    public void onRestore(ChatMessage message) {
        ChatOutbox.RestoreResult result=outbox.restore(message.requestId(),
                composerInput.getText().length()>0||!attachments.isEmpty()||preparingAttachments);
        if(result.getStatus()==ChatOutbox.RestoreStatus.COMPOSER_OCCUPIED){toast("Clear the current draft first");return;}
        if(result.getStatus()!=ChatOutbox.RestoreStatus.RESTORED&&result.getStatus()!=ChatOutbox.RestoreStatus.REATTACH_REQUIRED)return;
        composerInput.setText(result.getText());
        composerInput.setSelection(composerInput.getText().length());
        if(result.getDraft()!=null)attachments.addAll(result.getDraft().getAttachments());
        renderAttachments();
        render();
        if(result.getStatus()==ChatOutbox.RestoreStatus.REATTACH_REQUIRED)toast("Check the chat before retrying. Select attachments again.");
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
        if(preparingAttachments)return;
        final List<Uri> selected = new ArrayList<>(uris.subList(0, Math.min(free, uris.size())));
        preparingAttachments=true;updateStatusUi();attachmentsTitle.setText("Preparing "+selected.size()+" attachment(s)…");attachmentsTitle.setVisibility(View.VISIBLE);
        final long remainingBudget=ImageGuard.remainingBytes(payloadsOf(attachments));
        AppExecutors.io().execute(() -> {
            List<Attachment> staged = new ArrayList<>();
            String error = null;
            long budget = remainingBudget;
            for (Uri uri : selected) {
                try {
                    ImagePayload payload = ru.billyhargrove.pimobile.net.AttachmentPreparer.prepare(getContentResolver(), uri, budget);
                    budget -= payload.size();
                    Bitmap thumb = payload.isFile()?null:ImagePreparer.thumbnail(payload.bytes(), 320);
                    staged.add(new Attachment(payload, thumb, ImagePreparer.displayName(getContentResolver(), uri)));
                } catch (Exception e) {
                    error = e.getMessage() == null ? "could not read file" : e.getMessage();
                    break;
                }
            }
            final List<Attachment> stagedFinal = staged;
            final String failure = error;
            AppExecutors.main(() -> {
                preparingAttachments=false;if(isDestroyed())return;updateStatusUi();
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
        updateSendIcon();
        attachmentStrip.removeAllViews();
        if (attachments.isEmpty()) {
            attachmentsScroll.setVisibility(View.GONE);
            attachmentsTitle.setVisibility(View.GONE);
            return;
        }
        attachmentsScroll.setVisibility(View.VISIBLE);
        attachmentsTitle.setVisibility(View.GONE);
        for (int i = 0; i < attachments.size(); i++) {
            final int index = i;
            Attachment attachment = attachments.get(i);
            com.google.android.material.chip.Chip item=new com.google.android.material.chip.Chip(this);
            item.setText(attachment.payload().isFile()?"📎 "+attachment.payload().fileName():"Image "+(index+1));item.setEnsureMinTouchTargetSize(true);item.setCloseIconVisible(true);item.setCloseIconContentDescription(getString(R.string.cd_attachment_remove,index+1));item.setMaxWidth(Math.round(260*getResources().getDisplayMetrics().density));item.setEllipsize(android.text.TextUtils.TruncateAt.END);
            item.setOnCloseIconClickListener(v -> {
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
        org.json.JSONObject viewport=cachedView?client.cachedViewport(sessionId):null;
        if(viewport!=null){firstRenderDone=true;followTail=false;scrollGeneration++;}
        applySnapshot(snapshot);
        if(viewport!=null){boolean follow=viewport.optBoolean("follow");String anchor=viewport.optString("anchor");int offset=viewport.optInt("offset");messageList.post(()->{int position=adapter.positionOf(anchor);if(position>=0)((LinearLayoutManager)messageList.getLayoutManager()).scrollToPositionWithOffset(position,offset);followTail=follow;});}
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
        loadingUi.loaded();adapter.sessionStatus(sessionStatus);
        render(store.apply(update.messages(), update.removedIds()));
        ensureUserContext();updateStatusUi();
    }

    @Override
    public void onAck(Ack ack) {
        if(!sessionId.equals(ack.sessionId()))return;
        if(ack.requestId().equals(controlRequest)){controlRequest=null;if(ack.ok()){if(composerInput.getText().toString().trim().equals(controlDraft))composerInput.setText("");if("name".equals(controlKind))toast("Session renamed");}else toast(errorText(ack));controlDraft=null;return;}
        if(ack.requestId().equals(historyRequest)){historyRequest=null;historyButton();if(!ack.ok())toast(errorText(ack));else ensureUserContext();return;}
        if(ack.requestId().equals(documentRequest)){documentRequest=null;if(!ack.ok())toast(errorText(ack));return;}
        if (ack.requestId().equals(configurationRequest)) {
            configurationRequest=null;
            if(ack.ok()){if(modelSheet!=null)modelSheet.dismiss();toast("Session settings updated");}
            else if(modelSheet!=null && modelSheet.isShowing())modelSheet.failed(errorText(ack));
            else toast(errorText(ack));
            return;
        }
        if (abortRequests.remove(ack.requestId())) {
            if (!ack.ok()) {
                toast(getString(R.string.ack_failed_format, errorText(ack)));
            }
            return;
        }
        ChatOutbox.AckResult result=outbox.acknowledge(ack);
        if(!result.getHandled())return;
        if (!ack.ok()) {
            toast(getString(R.string.ack_failed_format, errorText(ack)));
            if (result.getRejectedText()!=null && composerInput.getText().length()==0 && attachments.isEmpty() && !preparingAttachments) {
                composerInput.setText(result.getRejectedText());
                composerInput.setSelection(composerInput.getText().length());
            }
        }
        render();
    }

    @Override
    public void onCommandUncertain(String requestId, String sessionId, String reason) {
        if(!this.sessionId.equals(sessionId))return;
        if(requestId.equals(controlRequest)){controlRequest=null;toast("Command result unknown: "+reason);return;}
        if(requestId.equals(historyRequest)){historyRequest=null;historyButton();toast("History could not be loaded: "+reason);return;}
        if(requestId.equals(documentRequest)){documentRequest=null;toast("File could not be loaded: "+reason);return;}
        if(requestId.equals(configurationRequest)) {
            configurationRequest=null;
            if(modelSheet!=null && modelSheet.isShowing())modelSheet.failed("Result unknown. Check the model in the terminal before retrying.");
            else toast("Effort change result unknown. Check Pi before retrying.");
            return;
        }
        if (abortRequests.remove(requestId)) {
            toast(getString(R.string.uncertain_format, reason));
            return;
        }
        if(!outbox.uncertain(requestId,sessionId))return;
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
        return outbox.thumbnail(requestId,index);
    }

    // ----------------------------------------------------------------- helpers

    private void applySnapshot(Snapshot snapshot) {
        loadingUi.loaded();
        sessionStatus = snapshot.status();adapter.sessionStatus(sessionStatus);
        truncatedBanner.setVisibility(snapshot.truncated() ? View.VISIBLE : View.GONE);
        render(store.replaceAll(snapshot.messages()));
        if(!cachedView)ensureUserContext();
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
        List<ChatMessage> merged=outbox.reconcile(store.transcript());
        if(pendingMessages!=null)pendingMessages.save(outbox.localReceipts());
        adapter.submit(merged);
        chatEmptyText.setVisibility(adapter.size() == 0 && (loadingUi==null||!loadingUi.isLoading()) ? View.VISIBLE : View.GONE);
    }

    /**
     * Renders a transcript change. Scroll position is preserved unless the tail
     * changed (a new message or the streaming message growing) and the user is
     * already at the bottom.
     */
    private boolean followTail=true;
    private int scrollGeneration;
    private void render(TranscriptStore.ChangeSet changeSet) {
        render();
        boolean firstLoad = !firstRenderDone && adapter.size() > 0;
        if(firstLoad || (followTail && changeSet!=null && changeSet.tailTouched()))scrollToBottom(firstLoad);
        if(firstLoad)firstRenderDone=true;
        // RecyclerView already preserves the visible anchor through DiffUtil.
        // Reapplying an old top offset here races pending tail scrolls and IME layout.
    }

    private void updateStatusUi() {
        boolean connected = client.state() == ConnectionState.CONNECTED;
        String model = configuration == null ? "" : configuration.optString("model", "");
        if(model.contains("/"))model=model.substring(model.lastIndexOf('/')+1);
        String detail=model.isEmpty()?"Online":model+" · "+configuration.optString("thinkingLevel","off");
        boolean running=sessionStatus==SessionStatus.RUNNING;
        ru.billyhargrove.pimobile.core.ExecutionInfo execution=new ru.billyhargrove.pimobile.core.ExecutionInfo(configuration,running);
        String state=cachedView?"Cached · syncing":!connected?StatusUi.connectionLabel(this,client.state()):sessionStatus==SessionStatus.OFFLINE?"Pi disconnected":running?"Working":"Connected";
        String details=execution.model.isEmpty()?"":" · "+execution.model+" · "+ru.billyhargrove.pimobile.ui.EffortSlider.label(execution.effort);
        if(!execution.model.isEmpty()&&execution.available)details+=" · "+("fast".equals(execution.tier)?"⚡ ":"")+execution.tierLabel(running);
        chatStatusText.setText(state+details);
        findViewById(R.id.effortButton).setContentDescription("Effort: " + (configuration==null?"unknown":ru.billyhargrove.pimobile.ui.EffortSlider.label(configuration.optString("thinkingLevel","off"))));
        chatConnectionDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                StatusUi.sessionDotColor(this, sessionStatus, connected && sessionStatus != SessionStatus.OFFLINE)));
        boolean online = client.state() == ConnectionState.CONNECTED && sessionStatus != SessionStatus.OFFLINE && !readOnly;
        stopButton.setVisibility(sessionStatus == SessionStatus.RUNNING ? View.VISIBLE : View.GONE);
        stopButton.setEnabled(online);
        sendButton.setEnabled(online&&!preparingAttachments);
        attachButton.setEnabled(!readOnly&&!preparingAttachments);
    }

    private boolean isNearBottom() {
        LinearLayoutManager layoutManager = (LinearLayoutManager) messageList.getLayoutManager();
        if (layoutManager == null || adapter.size() == 0) {
            return true;
        }
        return !messageList.canScrollVertically(1);
    }

    private void scrollToBottom(boolean force) {
        if(force)followTail=true;
        final int version=++scrollGeneration;
        messageList.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener(){
            private boolean requested;
            public boolean onPreDraw(){
                if(version!=scrollGeneration||!followTail||messageList.getScrollState()==RecyclerView.SCROLL_STATE_DRAGGING){messageList.getViewTreeObserver().removeOnPreDrawListener(this);return true;}
                int last=adapter.size()-1;LinearLayoutManager lm=(LinearLayoutManager)messageList.getLayoutManager();
                if(last>=0&&lm.findViewByPosition(last)==null&&!requested){requested=true;lm.scrollToPositionWithOffset(last,0);return true;}
                messageList.getViewTreeObserver().removeOnPreDrawListener(this);alignTail(version);return true;
            }
        });messageList.invalidate();
    }
    private void alignTail(int version){
        if(version!=scrollGeneration||!followTail||messageList.getScrollState()==RecyclerView.SCROLL_STATE_DRAGGING)return;
        LinearLayoutManager lm=(LinearLayoutManager)messageList.getLayoutManager();View tail=lm.findViewByPosition(adapter.size()-1);
        if(tail!=null)messageList.scrollBy(0,Math.max(0,lm.getDecoratedBottom(tail)-(messageList.getHeight()-messageList.getPaddingBottom())));
    }

    private void toast(String text) {
        Toast.makeText(this, text, Toast.LENGTH_SHORT).show();
    }

    private static String errorText(Ack ack) {
        if (ack == null || ack.error() == null || ack.error().isEmpty()) {
            return "no details";
        }
        return ack.error();
    }
}
