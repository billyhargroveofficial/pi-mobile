# Активные workflow и агенты — локальный слайс 8 октября 2026

`core/ActiveWork` выделяет из Compose разбор активных workflow, фаз и самостоятельных агентов. Проекция копирует статические счётчики, примитивные timestamps, подписи фаз, navigation targets и признаки history/live в read-only snapshot, который не удерживает исходные JSONObject. ChatScreen вычисляет его один раз для принятого orchestration snapshot и передаёт шапке, composer и dock. Изменение notice, черновика или остальных метаданных чата не запускает новый разбор прежних данных.

`ui/OrchestrationEntry` сохраняет discovery lifecycle, generation fence, injected fetch и публичный JSONObject-фасад ActiveOrchestration. Новый typed adapter передаёт snapshot внутреннему presentation-only `features/orchestration/ActiveWorkScreen`. Экран сохраняет компактную и развёрнутую геометрию, горизонтальную прокрутку, семантику кнопок и прежние переходы. Его секундный ticker форматирует примитивное время; фазы, timestamps и счётчики из JSON повторно не читаются. Публичный JSONObject-контракт ChatScreen также сохранён.

Проекция сохраняет порядок источника, прежние active statuses и membership самостоятельных агентов, первый running phase, включая различие отсутствующего и пустого title. Записанные счётчики завершённых агентов входят в workflow metrics, active count отражает только активных участников. Unknown/zero/negative/saturating counters используют прежний AgentMetrics. Для самостоятельных агентов сохраняется earliest-positive-start clock; для duplicate workflow IDs — прежний last-clock-wins. Saved данные сохраняют history navigation, но не отображаются как live work. Опубликованные списки не изменяются после мутации исходного JSON.

Новый принятый snapshot по-прежнему требует прохода по фактам. Повторное использование связано с identity orchestration JSONObject, как в прежнем Compose-контракте: новые данные публикуются новым объектом. Это устранение повторных чтений при тиках и независимых изменениях чата; heap, FPS и время кадра не измерялись.

## Проверки

- `npm run quality`: **107 Node PASS**, ownership/import contracts и10 immutable UI baseline SHA256 PASS. Новый contract запрещает pure projection зависимости от Android/Compose, transport/storage и presentation-only screen; экран не получает PiClient.
- `testDebugUnitTest`: **597 JVM PASS**, без failures/errors/skips.13 новых ActiveWork checks покрывают live/history, порядок и membership, phases/targets, counters/clocks, копирование и неизменяемость, duplicate IDs и отсутствие JSON reads при40 тиках.
- `assembleDebug`, `assembleDebugAndroidTest`, `lintDebug`: PASS; lint0 errors/149 прежних warnings/3 information.
- **48 final native PASS**: по13 в normal1080×2400/font1/light, narrow945×2100/font1.3/dark и narrow945×2100/font2/light. В каждом режиме:5 ActiveWork,1 existing dock counter,2 workflow/keyboard и5 arrival checks. Ещё9 normal regressions:4 orchestration inspector/counter и5 composer. Все режимы density420, API35, offline emulator.
- На прежнем APK отдельно **3 baseline native PASS**. Реальный elapsed tick менял label7s→8s и увеличивал phase/clock/counter reads `[4,4,2]→[8,8,2]`. Финальный APK в каждом режиме меняет тот же label, сохраняя reads **`[1,2,2]→[1,2,2]`**. Existing dock и inspector counter checks также PASS.
- Реальный ChatScreen сохраняет source-array reads **2→2** после40 metadata frames, header notice и редактирования черновика во всех трёх режимах. Fixture проверяет retained draft и0 transport writes. Live→saved убирает active dock, сохраняя history; удаление history также отражается в шапке.
- Проверены фактические compact/expanded navigation targets и горизонтальная прокрутка, busy workflow вместе с Queue/IME, сохранение reader anchor при новых событиях и stop/start. Catch-up дал16 разных аппаратных кадров в каждом режиме и36/36/37 промежуточных viewport positions с завершением на измеренном хвосте; reduced-motion PASS.

Просмотрены **34 финальных PNG**: по9 в трёх режимах и7 inspector/composer regression. Три парных normal снимка expanded/compact/compact-agents дают **0 различий RGB ниже y100**, где исключены часы status bar. Крупный шрифт, доступность редактора и кнопок, history и reader/tail проверены визуально.

Первоначальные fixture failures из-за отсутствующих test resource IDs, устаревших accessibility nodes, системного Back на краевом swipe и немедленной проверки следующего Compose frame исправлены в тестовом host. Fixture использует свежие стабильные bounds, swipe внутри dock и bounded ожидание удаления элемента. Только полностью завершённые последовательные final runs включены в48 PASS; исходные failed logs сохранены отдельно. Production-код после этих исправлений fixture не менялся.

## Область и артефакты

Production scope относительно начала слайса: добавлены `core/ActiveWork` и `features/orchestration/ActiveWorkScreen`; изменены OrchestrationData, OrchestrationEntry, ChatScreen, ChatHeader, ChatComposer и ChatWorkDock. Все остальные production SHA256 прежние, удалённых файлов нет.128 Kotlin/0 Java,197 файлов main. Registry/capsule/source contracts и entry docs обновлены. Прежние dirty slices сохранены; `server/Caddyfile` сохранил SHA256 `b3e6a271cce6106c9c739a57f7e9ae386317edf271b3996c44af8aec370af71f`. `git diff --check` PASS.

Версия0.6.007/code14, пакет `ru.billyhargrove.pimobile`, зависимости и сертификат прежние. APK SHA256 `4689d11d867f1ead23c198d18d7f3c9912620b2661d6cde38c498cc2c3ef1db5`; certificate SHA256 `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`. In-place upgrade baseline→final без clear/uninstall сохранил SHA256 всех4 private preferences.

Созданные guest artifacts удалены после сохранения host evidence; остальные файлы эмулятора сохранены. Сеть восстановлена:airplane0/Wi-Fi1/mobile-data0; экран1080×2400/density420/font1/light, animator scale1. Owned emulator завершился с exit0, `adb devices` пуст. Live Pi/Orca/gateway и paid prompts не использовались, физический телефон не подключён. Изменения локальные, нового релиза/публикации нет.

Evidence: `/Users/billy/temp/pi-mobile-active-work-20261008/` — checklist, production before/final/scope hashes, baseline APK/native counters, full gates, последовательные native runs, upgrade preference hashes, APK identity, paired pixels,34 reviewed PNG, auxiliary hardware frames и emulator restore/exit proof.
