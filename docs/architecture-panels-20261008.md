# Политика панелей модели и effort — локальный слайс 8 октября 2026

`ConfigurationPanels` выделяет из ChatActivity правила открытия, fallback при неизвестной модели, доступность редактирования, передачу результата видимым ожидающим панелям и локальное закрытие. Platform-free владелец получает состояние экрана и типизированные порты окон. Activity сохраняет Android-окна, IME, anchor bounds и явные configure callbacks; `ConfigurationSession` сохраняет командный ticket и single-flight. Окна по-прежнему закрываются пользователем, ACK не закрывает панель. После её закрытия ошибка остаётся в chat notice. onDestroy закрывает оба окна и очищает ссылки; поздние результаты, reports и открытия игнорируются. Закрытие во время создания host также не оставляет бесхозное окно.

Публичные JSONObject-конструкторы EffortPopup и ModelSettingsSheet сохранены. Внутренние типизированные конструкторы принимают уже разобранные Model/Catalog и передают их владельцам состояния без повторной проекции. ModelSettingsSession сохраняет snapshot выбранной модели и draft, пока открыта панель; live reports не переписывают этот draft.

`ModelCapabilities.find` проверяет идентичность только в первых1000 элементах registry и разбирает capabilities выбранной модели, без полного Catalog/byKey и массивов остальных моделей. Сохраняются строгие string identities, первая валидная копия модели, порядок поддерживаемых уровней, fallback для отсутствующей модели и прежняя граница registry. QuickEffortSession использует эту проекцию для reports. Частичные reports без `models` не читают registry. Наличие effort/tier остаётся независимым: даже явный report с прежним равным значением продвигает ACK fence этого поля. Ошибка откатывает к последнему подтверждённому значению; успешный ACK не перекрывает более свежий report.

Поиск выбранной модели всё ещё просматривает до1000 identities. Это сокращение проекции capabilities и промежуточных структур, а не устранение всей работы с registry. Время кадра, FPS и heap не измерялись.

## Проверки

- `npm run quality`: **106 Node PASS**, ownership/import contracts и10 immutable baseline SHA256 PASS. Новый contract запрещает panel policy Android/UI, transport/storage и прямую зависимость от screen.
- `testDebugUnitTest`: **584 JVM PASS**, без failures/errors/skips.16 новых проверок:12 opening/result/lifecycle policy,2 selected capability projection и2 partial/equal field ACK fence.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: PASS; lint0 errors/149 прежних warnings/3 information.
- **46 final native PASS**:8 configuration commands +3 public panel host сценария в каждом из normal1080×2400/font1/light, narrow945×2100/font1.3/dark и narrow945×2100/font2/light; ещё8 controls +5 arrivals в normal. Все режимы density420, API35, offline emulator. Проверены реальный slider/Apply/tier, pending/retry/setup errors, read-only, foreign/stale ACK, manual dismissal, persistent feedback, stop/start, actual destroy и явный equal effort report перед success/rejection. Ticket, draft и отсутствие prompt/abort проверяются внутри конфигурационных fixtures.
- На прежнем APK отдельно **3 baseline native PASS**. В открытом quick panel report с1000 моделями давал **2000 capability-array reads**, новый APK даёт **2** во всех трёх режимах.40 partial effort reports давали40 registry-array reads; новый APK — **0**. Full model panel сохраняет1 registry projection и2 reads capabilities первой модели после duplicate row, search, tier/effort edit и Apply/ACK.
- Arrival regressions сохраняют фактический reader anchor при streaming/history и stop/start. Catch-up дал16 разных аппаратных кадров проявления и36 промежуточных viewport positions с завершением на измеренном хвосте; выключенные системные анимации соблюдаются.

Просмотрены24 финальных PNG (18 панелей,6 controls/reader/tail). В парных normal снимках model и rejected ниже y100 —0 различий RGB. Quick и selected имеют0 различий вне круга подсветки нажатой effort-кнопки `(310,2184)-(416,2290)`; сами панели совпадают. Часы status bar исключены из сравнения. Геометрия, доступные кнопки и крупный шрифт сохраняются.

Прерванные пересёкшиеся dark/large старты не включены в результат. Оба owned runner остановлены; последующие последовательные прогоны полностью завершились и проверены по числу успешных тестов и отсутствию failure status. Их отдельные raw logs сохранены для аудита.

## Область и артефакты

Production scope относительно начала слайса: новый `features/chat/ConfigurationPanels`; изменены ChatActivity, ModelCapabilities, QuickEffortSession, ModelSettingsSession, EffortPopup и ModelSettingsSheet. Все остальные production SHA256 прежние, удалённых файлов нет.126 Kotlin/0 Java. Registry/capsule/source contract и текущие entry docs обновлены. Прежние dirty slices сохранены; `server/Caddyfile` сохранил SHA256 `b3e6a271cce6106c9c739a57f7e9ae386317edf271b3996c44af8aec370af71f`. `git diff --check` PASS.

Версия0.6.007/code14, пакет `ru.billyhargrove.pimobile`, зависимости и сертификат прежние. APK SHA256 `5e1be1658368ebb7593e6bd2bc8aec4f95307d694763f10b351ca83582337a89`; certificate SHA256 `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`. In-place upgrade baseline→final без clear/uninstall сохранил SHA256 всех4 private preferences.

Созданные guest PNG удалены после сохранения host evidence. Сеть восстановлена:airplane0/Wi-Fi1/mobile-data0; экран1080×2400/density420/font1/light, animator scale1. Owned emulator завершился с exit0, `adb devices` пуст. Live Pi/Orca/gateway и paid prompts не использовались, физический телефон не подключён. Изменения локальные, нового релиза/публикации нет.

Evidence: `/Users/billy/temp/pi-mobile-panels-20261008/` — checklist, production before/final/scope hashes, baseline APK/native counters, full gates, последовательные native runs, upgrade preference hashes, APK identity, paired pixels,24 PNG и emulator restore/exit proof.
