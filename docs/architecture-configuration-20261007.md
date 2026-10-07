# Configuration panels — 7 октября 2026

По просьбе Billy продолжить оптимизацию и улучшение архитектуры выделены state,
projection и presentation панели модели и anchored effort. Версия сохраняется
0.6.006/versionCode13; wire configuration по-прежнему принадлежит ChatSession.

## Разделение и повторное использование

`ui/ModelSettingsSheet` уменьшен с91 до24 строк; он только размещает production
screen в Android modal host, передаёт callback и закрывает owner при dismissal.
`ui/EffortPopup` уменьшен со146 до73 строк: сохранены anchor geometry, IME/back,
lifecycle, reveal и platform listeners. Ни один из hosts не хранит draft/pending
configuration state внутри Compose content.

`ModelSettingsSession` владеет catalog, поиском/filtered rows, выбранной моделью,
нормализованными draft effort/tier, explicit Apply, pending и error. Применение
изменений остаётся injected callback в ChatSession. Смена модели нормализует
effort сразу, независимо от первого AndroidView update. Успех re-enables Apply
в той же панели; ошибка сохраняет draft для явной повторной попытки.

`QuickEffortSession` владеет preview/commit, tier, pending и confirmed rollback.
Preview не отправляет команду; changed commit отправляет одну. Поле, подтверждённое
успешным ACK, становится rollback baseline, даже если configuration frame ещё не
пришёл. Более поздний отчёт этого поля выигрывает у старого ACK. Частичный tier
frame не подтверждает ожидающий effort, и tier ACK не подтверждает неотправленный
effort preview. Ошибка восстанавливает последние подтверждённые значения без
автоматической отправки. Close инвалидирует действия и поздние результаты.

`ModelSettingsScreen` и `QuickEffortScreen` содержат Compose presentation и
bounded binding к существующему EffortSlider. Публичные constructors/methods
платформенных hosts сохранены. Добавлен только internal pending-result getter
для Activity routing. Config ACK/error получает открытая ожидающая панель; когда
она уже закрыта, ошибка показывается в чате. Панель, не отправлявшая изменение,
не получает чужую ошибку. ChatSession продолжает фильтровать session/request IDs;
state панелей не создаёт второго command ledger.

`ModelCapabilities` — общая pure projection для обеих панелей, EffortSlider и
TierToggle. Она копирует identities/names и только фактически reported canonical
levels/tiers, сохраняет порядок уровней, ограничивает registry первыми1000 rows
с truncation marker. Missing/invalid registry identities пропускаются. Canonical
model keys deduplicate до разбора capability arrays: дубликат не создаёт второй
LazyColumn key и не запускает повторный разбор. Набор levels ограничен7 значениями
без большого промежуточного списка одинаковых строк. Empty capabilities не
открывают управление неизвестной моделью; reported effort остаётся видимым.

JSON не хранится в presentation. Названия, capabilities, search strings и
provider-group flag вычисляются при projection, поиск — при изменении query.
Отдельный internal slider binding принимает immutable levels вместо обратного
построения JSON. Legacy JSONArray configure и label APIs сохраняются и используют
ту же policy. Snapshot mutation не меняет уже открытый draft.

Все пять internal файлов зарегистрированы у владельца chat в owners.json.
Source contracts: pure projection, platform-free state (Compose runtime only)
и presentation only. Добавлены негативные Node проверки Android/transport/UI
imports и обратного state → screen / projection → state направления. Новых
Gradle-модулей, DI, зависимостей и параллельных реализаций нет.

## Проверки

JVM ModelCapabilitiesTest, ModelSettingsSessionTest и QuickEffortSessionTest
проверяют malformed/duplicate registry, bounded/copying projection, normalization,
explicit Apply/commit, pending/error/retry, lifecycle, synchronous callbacks и
partial/newer configuration vs ACK ordering. Native ConfigurationPanelUiTest
проверяет production modal/anchor/slider с synthetic callbacks и closed-panel
failure routing; остальные сценарии — EffortInteractionUiTest, HotfixUiTest,
ExpressiveUiTest и DesignPreviewTest.

Финальный gate:93/93 Node,284/284 JVM (29 новых проверок состояния и projection),
debug/androidTest build — PASS. Lint:0 errors/149 warnings против150 в baseline.
Ownership, dependency boundaries и hashes всех10 pinned UI screenshots — PASS.
На финальном APK native14/14 в каждом из трёх API35 режимов:1080×2400 light/font1,
360dp dark/font1.3 и360dp light/font2; всего42 PASS. Это focused configuration
matrix, а не повторный полный UI прогон приложения.

Native counter подтверждает один проход registry и два чтения capability arrays
(levels и tiers) после duplicate row, tier/effort edits, Apply/ACK и поиска;
повторного разбора JSON при этих действиях нет. Это проверка количества чтений,
не измерение FPS, расхода батареи или скорости сети.

Before/after просмотрены визуально. Static app pixels совпадают в tier picker
и effort popup с клавиатурой после явного исключения system bars, фонового spinner
и transient Material selection/ripple в tier picker. Не заявляется равенство
полных PNG с анимацией. Панель также проверена визуально при360dp/font2.
Логи, baseline/after screenshots, scripts и точные маски сравнения находятся в
`/Users/billy/temp/pi-mobile-configuration-20261007/`.

Эмулятор восстановлен в1080×2400/light/font1 и остановлен после проверки.
SHA256 принятого debug APK:
`3b20ef2d9f92572848ceedaf59505d04b6ffb22fd9cf7c4c03155063c14842cd`.

Проверяется bounded configuration slice. Физический телефон и настройка настоящей
Pi-сессии этим прогоном не проверяются. Live Pi/Orca/gateway, подпись, dependencies
и private data не меняются; prompts, APK release и внешние сообщения не отправляются.
