package ru.billyhargrove.pimobile;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import ru.billyhargrove.pimobile.core.Catalog;
import ru.billyhargrove.pimobile.core.CatalogRow;
import ru.billyhargrove.pimobile.core.ConnectionState;
import ru.billyhargrove.pimobile.core.EndpointPolicy;
import ru.billyhargrove.pimobile.core.SessionGrouping;
import ru.billyhargrove.pimobile.core.Snapshot;
import ru.billyhargrove.pimobile.net.ApiException;
import ru.billyhargrove.pimobile.net.AppExecutors;
import ru.billyhargrove.pimobile.net.PiClient;
import ru.billyhargrove.pimobile.store.SettingsStore;
import ru.billyhargrove.pimobile.ui.CatalogAdapter;
import ru.billyhargrove.pimobile.ui.StatusUi;
import ru.billyhargrove.pimobile.ui.SystemInsets;

/**
 * Connect form plus the grouped workspace / session catalog.
 *
 * <p>All blocking work (health check, catalog refresh) happens on
 * {@link AppExecutors#io()}; every view touch is posted back to the main thread.</p>
 */
public final class MainActivity extends AppCompatActivity
        implements PiClient.Listener, CatalogAdapter.Listener {

    private PiApp app;
    private PiClient client;
    private SettingsStore settings;
    private CatalogAdapter adapter;

    private View connectionDot;
    private TextView connectionStatusText;
    private TextView connectionDetailText;
    private TextView catalogStatusText;
    private TextView catalogEmptyText;
    private TextView connectErrorText;
    private View connectPanel;
    private com.google.android.material.bottomsheet.BottomSheetDialog connectionSheet;
    private boolean firstCatalog = true;
    private EditText connectUrlInput;
    private EditText connectTokenInput;
    private Button connectButton;
    private Button disconnectButton;
    private Button healthCheckButton;
    private Button refreshButton;
    private Button settingsButton;

    private boolean healthCheckRunning;
    private boolean catalogRunning;
    private ru.billyhargrove.pimobile.ui.ArchiveSheet archiveSheet;
    private String resumeRequest, resumeSession, resumeTitle;
    private final android.os.Handler resumeHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        app = PiApp.get(this);
        client = app.client();
        settings = app.settings();

        connectionDot = findViewById(R.id.connectionDot);
        connectionStatusText = findViewById(R.id.connectionStatusText);
        connectionDetailText = findViewById(R.id.connectionDetailText);
        catalogStatusText = findViewById(R.id.catalogStatusText);
        catalogEmptyText = findViewById(R.id.catalogEmptyText);
        connectionSheet = new com.google.android.material.bottomsheet.BottomSheetDialog(this);
        View sheet = getLayoutInflater().inflate(R.layout.sheet_connection, null);
        connectionSheet.setContentView(sheet);
        connectionSheet.setOnShowListener(dialog -> {
            connectionSheet.getBehavior().setState(com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED);
            connectionSheet.getBehavior().setSkipCollapsed(true);
        });
        connectErrorText = sheet.findViewById(R.id.connectErrorText);
        connectPanel = sheet.findViewById(R.id.connectPanel);
        connectUrlInput = sheet.findViewById(R.id.connectUrlInput);
        connectTokenInput = sheet.findViewById(R.id.connectTokenInput);
        connectButton = sheet.findViewById(R.id.connectButton);
        disconnectButton = sheet.findViewById(R.id.disconnectButton);
        healthCheckButton = sheet.findViewById(R.id.healthCheckButton);
        refreshButton = findViewById(R.id.refreshButton);
        settingsButton = findViewById(R.id.settingsButton);

        connectUrlInput.setText(settings.baseUrl());
        applyTokenHint();

        if (!settings.hasConnection()) findViewById(R.id.mainRoot).post(() -> connectionSheet.show());

        RecyclerView list = findViewById(R.id.catalogList);
        list.setLayoutManager(new LinearLayoutManager(this));
        adapter = new CatalogAdapter(this);
        list.setAdapter(adapter);
        ru.billyhargrove.pimobile.ui.ExpressiveMotion.list(list);
        ru.billyhargrove.pimobile.ui.ExpressiveMotion.buttons(findViewById(R.id.mainRoot));
        ru.billyhargrove.pimobile.ui.ExpressiveMotion.buttons(sheet);
        com.google.android.material.transition.platform.MaterialSharedAxis exit = new com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, true);
        exit.setDuration(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled() ? 350 : 0);
        getWindow().setExitTransition(exit);
        com.google.android.material.transition.platform.MaterialSharedAxis reenter = new com.google.android.material.transition.platform.MaterialSharedAxis(com.google.android.material.transition.platform.MaterialSharedAxis.X, false);
        reenter.setDuration(ru.billyhargrove.pimobile.ui.ExpressiveMotion.enabled() ? 350 : 0);
        getWindow().setReenterTransition(reenter);
        settingsButton.setOnClickListener(v -> connectionSheet.show());
        connectButton.setOnClickListener(v -> onConnectClicked());
        disconnectButton.setOnClickListener(v -> {
            client.disconnect();
            showMessage(getString(R.string.state_disconnected), false);
        });
        healthCheckButton.setOnClickListener(v -> onHealthCheckClicked());
        refreshButton.setOnClickListener(v -> onRefreshClicked());
        findViewById(R.id.historyButton).setOnClickListener(v -> {
            if (client.state() != ConnectionState.CONNECTED) { showMessage("Сначала подключись к Mac", true); return; }
            archiveSheet = new ru.billyhargrove.pimobile.ui.ArchiveSheet(this, app, (id, title) ->
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
                    .setTitle("Возобновить диалог?").setMessage(title + "\n\nОн откроется в Orca на Mac с прежней историей. Сообщение агенту не отправляется.")
                    .setNegativeButton("Отмена", null).setPositiveButton("Открыть", (d, which) -> resumeSaved(id, title)).show());
            archiveSheet.show();
        });

        // Edge-to-edge (targetSdk 35): the top bar eats the status bar inset, the
        // bottom status strip eats the navigation inset, and the IME lifts the form.
        SystemInsets.apply(this,
                findViewById(R.id.mainRoot),
                findViewById(R.id.mainTopBar),
                null,
                false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        client.setListener(this);
        if (client.state() == ConnectionState.IDLE && settings.hasConnection()) {
            client.connect(settings.baseUrl(), settings.token());
        }
        if (client.catalog().sessions().size() + client.catalog().terminals().size() > 0) {
            renderCatalog(client.catalog());
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        client.clearListener(this);
    }

    @Override protected void onDestroy() {
        connectionSheet.dismiss();
        if (archiveSheet != null) archiveSheet.dismiss();
        resumeHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    // ------------------------------------------------------------ user actions

    private void onConnectClicked() {
        String url = connectUrlInput.getText().toString().trim();
        String typedToken = connectTokenInput.getText().toString().trim();
        String token = typedToken.isEmpty() ? settings.token() : typedToken;

        if (url.isEmpty()) {
            showMessage(getString(R.string.error_url_missing), true);
            return;
        }
        EndpointPolicy.Result result = EndpointPolicy.validate(url, BuildConfig.DEBUG);
        if (result != EndpointPolicy.Result.OK) {
            showMessage(PiClient.describeResult(result), true);
            return;
        }
        if (token.isEmpty()) {
            showMessage(getString(R.string.error_token_missing), true);
            return;
        }

        settings.setBaseUrl(url);
        if (!typedToken.isEmpty()) {
            settings.setToken(typedToken);
        }
        applyTokenHint();
        showMessage("", false);
        client.connect(url, token);
    }

    private void onHealthCheckClicked() {
        final String url = connectUrlInput.getText().toString().trim();
        EndpointPolicy.Result result = EndpointPolicy.validate(url, BuildConfig.DEBUG);
        if (result != EndpointPolicy.Result.OK) {
            showMessage(PiClient.describeResult(result), true);
            return;
        }
        if (healthCheckRunning) {
            return;
        }
        healthCheckRunning = true;
        showMessage(getString(R.string.state_connecting), false);
        AppExecutors.io().execute(() -> {
            String text;
            boolean failed;
            try {
                String body = app.api().health(url);
                text = getString(R.string.health_ok) + (body.isEmpty() ? "" : " · " + trim(body));
                failed = false;
            } catch (Exception e) {
                text = getString(R.string.health_failed, messageOf(e));
                failed = true;
            }
            final String finalText = text;
            final boolean finalFailed = failed;
            AppExecutors.main(() -> {
                healthCheckRunning = false;
                showMessage(finalText, finalFailed);
            });
        });
    }

    private void onRefreshClicked() {
        if (!settings.hasToken()) {
            showMessage(getString(R.string.error_token_missing), true);
            return;
        }
        if (catalogRunning) {
            return;
        }
        catalogRunning = true;
        final String baseUrl = settings.baseUrl();
        final String token = settings.token();
        AppExecutors.io().execute(() -> {
            Catalog loaded = null;
            String error = null;
            try {
                loaded = app.api().fetchCatalog(baseUrl, token);
            } catch (Exception e) {
                error = messageOf(e);
            }
            final Catalog catalog = loaded;
            final String failure = error;
            AppExecutors.main(() -> {
                catalogRunning = false;
                if (catalog != null) {
                    renderCatalog(catalog);
                } else {
                    showMessage(failure == null ? "Не удалось обновить каталог" : failure, true);
                }
            });
        });
    }

    // ----------------------------------------------------------- PiClient.Listener

    @Override
    public void onConnectionState(ConnectionState state, String detail) {
        connectionStatusText.setText(StatusUi.connectionLabel(this, state));
        connectionDot.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(StatusUi.connectionDotColor(this, state)));
        connectionDetailText.setText(detail == null || detail.isEmpty()
                ? getString(R.string.catalog_updated_never)
                : detail);
        boolean busy = state == ConnectionState.CONNECTING
                || state == ConnectionState.RECONNECTING;
        if (state == ConnectionState.CONNECTED && connectionSheet.isShowing()) connectionSheet.dismiss();
        disconnectButton.setEnabled(state != ConnectionState.IDLE && state != ConnectionState.DISCONNECTED);
        connectButton.setEnabled(!busy);
    }

    @Override
    public void onCatalog(Catalog catalog) {
        renderCatalog(catalog);
        openResumedIfReady(catalog);
    }

    @Override
    public void onSnapshot(Snapshot snapshot) {
        // Snapshots belong to the chat screen.
    }

    @Override
    public void onAck(ru.billyhargrove.pimobile.core.Ack ack) {
        if (ack.requestId().equals(resumeRequest) && !ack.ok()) { clearResume(); showMessage(ack.error(), true); }
    }

    @Override
    public void onProtocolError(String message) {
        showMessage(getString(R.string.protocol_error_format, message), true);
    }

    @Override public void onData(String requestId, String sessionId, org.json.JSONObject data) {
        if (requestId.equals(resumeRequest) && "resume".equals(data.optString("type"))) {
            showMessage("Вкладка открыта в Orca. Ждём подключение Pi…", false);
            openResumedIfReady(client.catalog());
        }
    }

    @Override public void onCommandUncertain(String requestId, String sessionId, String reason) {
        if (requestId.equals(resumeRequest)) { clearResume(); showMessage("Результат открытия неизвестен. Проверь вкладки Orca на Mac; автоматического повтора нет.", true); }
    }

    private void resumeSaved(String id, String title) {
        if (resumeSession != null) return;
        if (archiveSheet != null) archiveSheet.dismiss();
        resumeSession = id; resumeTitle = title;
        resumeRequest = client.readCommand(id, "resume", new org.json.JSONObject());
        if (resumeRequest == null) { clearResume(); showMessage("Нет подключения к Mac", true); return; }
        findViewById(R.id.historyButton).setEnabled(false);
        ((TextView)findViewById(R.id.catalogSummary)).setText("Открываем диалог в Orca…");
        resumeHandler.postDelayed(() -> { if (resumeSession != null) { clearResume(); showMessage("Pi ещё не подключился. Проверь открытую вкладку на Mac — возможно, нужен ответ на диалог запуска.", true); } }, 45000);
    }

    private void openResumedIfReady(Catalog catalog) {
        if (resumeSession == null || !getLifecycle().getCurrentState().isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)) return;
        ru.billyhargrove.pimobile.core.Session session = catalog.findSession(resumeSession);
        if (session == null || !session.connected()) return;
        String id = resumeSession, title = resumeTitle;
        clearResume();
        startActivity(ChatActivity.intent(this, id, title, false));
    }

    private void clearResume() { resumeRequest = null; resumeSession = null; resumeTitle = null; resumeHandler.removeCallbacksAndMessages(null); findViewById(R.id.historyButton).setEnabled(true); }

    // ---------------------------------------------------------- CatalogAdapter.Listener

    @Override
    public void onSessionClick(CatalogRow row) {
        Intent intent = ChatActivity.intent(this, row.sessionId(), row.title(), false);
        startActivity(intent, android.app.ActivityOptions.makeSceneTransitionAnimation(this).toBundle());
    }

    @Override
    public void onTerminalClick(CatalogRow row) {
        Toast.makeText(this, getString(R.string.read_only_banner), Toast.LENGTH_LONG).show();
    }

    // -------------------------------------------------------------------- helpers

    private void renderCatalog(Catalog catalog) {
        List<CatalogRow> rows = SessionGrouping.build(catalog);
        adapter.submit(rows);
        TextView summary = findViewById(R.id.catalogSummary);
        long running = catalog.sessions().stream().filter(s -> s.status() == ru.billyhargrove.pimobile.core.SessionStatus.RUNNING && s.connected()).count();
        summary.setText(resumeSession != null ? "Открываем диалог в Orca…" : "Пространства: " + catalog.workspaces().size() + "  ·  Вкладки: " + (catalog.sessions().size() + catalog.terminals().size()) + (running > 0 ? "  ·  В работе: " + running : ""));
        if (firstCatalog && !rows.isEmpty()) {
            firstCatalog = false;
            ru.billyhargrove.pimobile.ui.ExpressiveMotion.enter(findViewById(R.id.catalogList), 80);
        }
        catalogEmptyText.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        String time = DateFormat.getTimeInstance(DateFormat.SHORT, Locale.getDefault()).format(new Date());
        catalogStatusText.setText(time);
    }

    private void applyTokenHint() {
        connectTokenInput.setHint(settings.hasToken() ? R.string.token_hint_existing : R.string.token_hint);
        if (settings.hasToken()) {
            connectTokenInput.setText("");
        }
    }

    private void showMessage(String text, boolean isError) {
        if (text == null || text.isEmpty()) {
            connectErrorText.setVisibility(View.GONE);
            return;
        }
        if (!connectionSheet.isShowing() && isError) com.google.android.material.snackbar.Snackbar.make(findViewById(R.id.mainRoot), text, com.google.android.material.snackbar.Snackbar.LENGTH_LONG).show();
        connectErrorText.setVisibility(View.VISIBLE);
        connectErrorText.setText(text);
        connectErrorText.setTextColor(getColor(isError ? R.color.danger : R.color.success));
    }

    private static String messageOf(Throwable t) {
        if (t instanceof ApiException && t.getMessage() != null) {
            return t.getMessage();
        }
        if (t == null || t.getMessage() == null || t.getMessage().isEmpty()) {
            return "неизвестная ошибка";
        }
        return t.getMessage();
    }

    private static String trim(String value) {
        if (value == null) {
            return "";
        }
        String flat = value.replace('\n', ' ').trim();
        return flat.length() > 80 ? flat.substring(0, 80) + "…" : flat;
    }
}
