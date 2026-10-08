# Проверки релизов Pi Mobile

## Релиз0.6.008 — 8 октября2026

[Изменения, измерения и ограничения](release-0.6.008.md):15 архитектурных слайсов;
108 Node/608 JVM PASS, build/lint PASS (0 errors/149 existing warnings/3 info).
Именно0.6.008/code15:180 synthetic native PASS/1 exact live-opt-in assumption skip;
39/39 dark360dp/font1.3 и39/39 light360dp/font2, дополнительно5/5 дока после
уточнения тестового жеста. Production APK при этом не изменился.51 PNG просмотрены.
Финальные counts: capabilities2000→2 для1000 моделей,80 unchanged-row replacements→0,
40 повторных outbox serialization callbacks→0; это bounded before/after scenarios.
Catch-up16/16/15 разных hardware frames, smooth tail36/36/37 positions; reader
answer/−620px сохранён. APK15 620 829 bytes (+0,5272% против опубликованной0.6.007),
SHA256d4312d652e188eee4e8a1303e2027d44fa6d033faee5a237711ce76de39c4d57.
Прежняя подпись; upgrade опубликованной0.6.007 сохраняет все9 private files.
Whole-app FPS/CPU/heap/battery/startup и physical-device/live-network acceptance
не измерены. Commit/push/GitHub release прямо разрешены; Telegram/host deploy
не входят в запрос. Посторонний Caddyfile исключён.
Stable Latest [v0.6.008](https://github.com/billyhargroveofficial/pi-mobile/releases/tag/v0.6.008) опубликован; публичные APK/checksums повторно скачаны и совпали с frozen artifact и GitHub digest. Сеть/экран восстановлены,129 owned guest fixtures удалены, emulator exit0/adb devices пуст.

## Локальная декомпозиция изображений переписки — 8 октября 2026

[Изменения и границы проверки](architecture-transcript-image-20261008.md):108 Node /
608 JVM PASS;41 final native PASS (11 в каждом из трёх режимов,5 shared transcript
regressions и3 усиленных late-delivery checks). Opaque TranscriptImageSession
резервирует один load, принимает первый result и закрывает callbacks; presentation
отделён в TranscriptImageScreen. Публичные transcript/actions, same-origin MediaLoader
и native zoom сохранены. Same-URL loader replacement на прежнем APK оставляет16 815
выборочных old red pixels и прежнюю ошибку; на финальном старых samples0 и Loading
во всех режимах. Реальная доставка old decode подтверждена; новая картинка остаётся
на12 аппаратных кадрах, по1 request на loader. Local/no remote, aspect и zoom PASS.
Scope:1 прежний production-файл изменён,2 добавлены,0 удалений;130 Kotlin/0 Java.
Просмотрены36 PNG;3 paired images дают0 app pixels diff. Catch-up16 hardware frames/
36/36/37 viewport positions, reader/history/tools/reduced-motion PASS. Build/lint
PASS,0 errors/149 прежних warnings;4 preference SHA256 сохранены при upgrade.
Версия0.6.007/code14, пакет/сертификат/зависимости прежние. Сеть/экран восстановлены,
emulator exit0, adb devices пуст. Heap/FPS, физический телефон и реальные remote
images не проверялись; live Pi не затронут, публикации нет.

## Локальная декомпозиция активных задач — 8 октября 2026

[Изменения и границы проверки](architecture-active-work-20261008.md):107 Node /
597 JVM PASS;48 final native PASS (13 в каждом из трёх режимов,9 inspector/composer
regressions). Pure ActiveWork копирует facts/counters/clocks/targets и history/live
в общий read-only snapshot шапки и dock. Discovery facade и публичные JSON APIs
сохранены; Compose presentation выделен в ActiveWorkScreen. Реальный elapsed tick
на прежнем APK даёт phase/clock/counter reads4/4/2→8/8/2, на финальном1/2/2→1/2/2.
Общая проекция чата сохраняет2→2 array reads после40 metadata frames, notice и draft
во всех режимах, writes0. Scope:6 прежних production-файлов изменены,2 добавлены,
0 удалений;128 Kotlin/0 Java. Просмотрены34 PNG;3 paired widgets дают0 app pixels
diff. Catch-up16 hardware frames/36/36/37 viewport positions, reader/reduced-motion
PASS. Build/lint PASS,0 errors/149 прежних warnings;4 preference SHA256 сохранены
при upgrade. Версия0.6.007/code14, пакет/сертификат/зависимости прежние. Сеть/экран
восстановлены, emulator exit0, adb devices пуст. Heap/FPS и физический телефон
не проверялись; live Pi не затронут, публикации нет.

## Локальная декомпозиция политики панелей — 8 октября 2026

[Изменения и границы проверки](architecture-panels-20261008.md):106 Node /584 JVM
PASS;46 final native PASS (8 configuration commands +3 panels в трёх режимах,
8 controls +5 arrivals). ConfigurationPanels отделяет opening/fallback/editability,
visible waiting result routing и disposal от Android window/IME/anchor adapters.
Публичные конструкторы, model draft snapshot, manual dismissal и partial/equal
field ACK fences сохранены. Реальный quick report с1000 моделями читает capabilities
2000→2 раза,40 partial effort reports — registry40→0; во всех трёх режимах.
Scope:6 прежних production-файлов изменены,1 owner добавлен,0 удалений;
126 Kotlin/0 Java. Просмотрены24 PNG; matched model/rejected —0 app pixels diff,
quick/selected отличаются только подсветкой нажатой effort-кнопки. Catch-up16
hardware frames/36 viewport positions, reader/reduced-motion PASS. Build/lint PASS,
0 errors/149 прежних warnings;4 preference SHA256 сохранены при upgrade.
Версия0.6.007/code14, пакет/сертификат/зависимости прежние. Сеть/экран восстановлены,
emulator exit0, adb devices пуст. Heap/FPS и физический телефон не проверялись;
live Pi не затронут, публикации нет.

## Локальная декомпозиция канонической ленты — 8 октября 2026

[Изменения и границы проверки](architecture-transcript-20261008.md):
105 Node /568 JVM PASS, включая7 pure projection,9 transcript owner и3 facade checks.
60 native PASS:3 transcript +5 history +5 arrivals в трёх режимах,20 shared
inspector/composer/outbox/viewport/WS/physical-pixel regressions и1 dark pixel check.
TranscriptSession отделяет каноническую ленту и reuse ChangeSet snapshot от scoped
wire adaptation; checkpoint предшествует viewport publication. Pure projection
объединяет source/status/metadata и повторно использует rows/tools.40 metadata
и40 empty delta дают80→0 замен answer row во всех режимах, reader/draft/caret
и saves1→1 неизменны, writes0. Legacy/epoch/manual disclosure, presence flags,
real streaming, history/context и shared inspector PASS. Физическое появление
раскрытого журнала проверяется стабильными аппаратными pixels, не одной semantics.
Catch-up16 hardware frames и36/36/37 viewport positions; delayed-cache30 reader
frames стабильны. Просмотрены27 PNG; все4 matched normal snapshots дают0 app
pixels diff. Build/lint PASS (0 errors/149 прежних warnings), upgrade/round-trip
сохраняет4 preferences SHA256. Версия0.6.007/code14, пакет/сертификат/зависимости
прежние. Сеть/экран восстановлены, emulator exit0, adb devices пуст.
Heap/FPS не измерялись; live Pi/physical phone не проверялись.

## Локальная декомпозиция черновика — 8 октября 2026

[Изменения и границы проверки](architecture-composer-20261008.md):
104 Node /549 JVM PASS, включая16 новых composer и3 chat regression checks.
53 native PASS:5 composer +6 outbox +5 arrivals в трёх режимах,5 viewport/WS.
Generic platform-free ComposerSession сохраняет полное значение редактора,
opaque attachments и публичный ChatSession facade. Ошибка потери нового черновика
воспроизведена JVM/native на старом APK; revision-fenced consumption сохраняет
следующий text/caret/file при одном отправленном prompt. Stop/start, reversed
selection при recreation, dictation/no auto-send, occupied restore/rejection,
read-only и late callbacks после actual destroy PASS. Пустые batches и неверные
индексы обходят concat/filter; heap/FPS не измерялись. Catch-up16 аппаратных
кадров и36/36/37 позиций, reader/reduced-motion PASS. Просмотрены21 финальный PNG;
парная диктовка даёт0 app pixels diff, busy отличается только фазой спиннера.
Build/lint PASS (0 errors/149 прежних warnings),4 preferences SHA256 сохранены
при upgrade. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Сеть/экран эмулятора восстановлены, owned emulator завершён, adb devices пуст.
Физический телефон и реальные speech/Pi callbacks не проверялись; слайс локальный.

## Локальная декомпозиция очереди и подтверждений — 8 октября 2026

[Изменения и границы проверки](architecture-outbox-20261008.md):
103 Node /530 JVM PASS, включая21 новую projection/state/chat проверку;
47 focused native PASS (3 outbox state +6 outbox UI +5 arrivals в каждом из
трёх режимов,5 viewport/WS regression). Pure QueueProjection сохраняет ordered
new USER consumption и bounded1500-key baseline; unchanged receipts переиспользуются.
Revision-owned receipt/queue lists и persistence checkpoint убирают повторное
кодирование:40 unchanged frames на предыдущем APK дают4→44 callbacks,
на финальном2→2 во всех режимах. ACK не потребляет очередь; один echo потребляет
одну копию, repeated snapshots не потребляют следующую и не вызывают save.
Unknown/Last known, actual stop/start/no replay, draft/file/image restore и caret
PASS. Restore fixture ждёт свежую стабильную геометрию кнопки после удаления chip.
Catch-up даёт16 аппаратных кадров и36/36/37 позиций; reader/viewport и reduced
motion PASS. Просмотрены18 PNG; matched stream имеет0 app pixels diff.
Build/lint PASS,0 errors/149 прежних warnings;4 preferences SHA256 сохранены
при установке поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Сеть/экран эмулятора восстановлены, эмулятор остановлен. Физический телефон
и live Pi queue/ACK не проверялись; изменения локальные, публикации нет.

## Локальная декомпозиция позиции читателя — 8 октября 2026

[Изменения и границы проверки](architecture-viewport-20261008.md):
102 Node /509 JVM PASS, включая 16 новых state/chat проверок; 47 focused native
PASS (3 viewport +5 arrivals +5 history в каждом из трёх режимов, 8 outbox/WS
regression). Manual drag запрещает позднему кешу восстановить старый anchor;
initial restore остаётся однократным, load резервируется до вызова порта.
Предыдущий APK воспроизводит скачок answer/−620→−9811px; финальный сохраняет
−620px на всех 30 кадрах в каждом режиме с 0 лишних cache reads. Destroy
закрывает viewport state и late callbacks, эффекты привязаны к chat identity.
Arriving keys вычисляются без промежуточного списка. Catch-up даёт 16 аппаратных
кадров и 36/36/37 позиций; history anchors и reduced motion PASS. Просмотрены
15 PNG; matched reader имеет 0 app pixels diff. Build/lint PASS,
0 errors/149 прежних warnings; 4 preferences SHA256 сохранены при установке
поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Сеть/экран эмулятора восстановлены, эмулятор остановлен. Физический телефон
и live Pi frames не проверялись; изменения локальные, публикации нет.

## Локальная декомпозиция отправки настроек чата — 8 октября 2026

[Изменения и границы проверки](architecture-configuration-requests-20261008.md):
101 Node /493 JVM PASS, включая 17 новых state/chat проверок; 56 focused native
PASS (6 configuration requests +3 panels +5 arrivals в каждом из трёх режимов,
14 controls/outbox regression). Typed request резервируется до transport call;
null/empty ID и setup failure снимают pending. Partial reports не потребляют
запрос; ACK не меняет canonical configuration/status/draft. Stop/start сверяет
ledger без replay; destroy закрывает ticket и обе панели, late effects
не изменяют старый UI. Catch-up даёт 16 аппаратных кадров и 36/36/37 позиций;
reader anchors и reduced motion PASS. Просмотрены 15 PNG; matched rejection
имеет 0 app pixels diff, quick — 0 вне transient pressed-button feedback.
Build/lint PASS, 0 errors/149 прежних warnings; 4 preferences SHA256 сохранены
при установке поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Сеть/экран эмулятора восстановлены, эмулятор остановлен. Физический телефон
и live Pi configure не проверялись; изменения локальные, публикации нет.

## Локальная декомпозиция служебных команд чата — 8 октября 2026

[Изменения и границы проверки](architecture-controls-20261008.md): 100 Node /
476 JVM PASS, включая 23 новых state/projection/chat проверок; 52 focused native
PASS (8 controls +5 arrivals в каждом из трёх режимов, 13 outbox/history/WS
regression). MCP-data принимается один раз и только для собственной команды;
повторный Stop не посылает дубли до terminal result. Missing MCP body сохраняет
draft, Stop ACK не придумывает idle. Stop/start сверяет ledger без replay,
destroy снимает ожидание и MCP-window, late callbacks не открывают его заново.
Catch-up даёт 16 аппаратных кадров и 36/36/37 положений viewport;
reader anchors и reduced motion PASS. Просмотрены 15 PNG; matched normal
MCP-dialog имеет 0 изменённых пикселей ниже y100. Build/lint PASS,
0 errors/149 прежних warnings; 4 preferences SHA256 сохранены при установке
поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние. Сеть/экран
эмулятора восстановлены, эмулятор остановлен. Физический телефон и live Pi
commands не проверялись; изменения локальные, публикация не выполнялась.

## Локальная декомпозиция пагинации чата — 8 октября 2026

[Изменения и границы проверки](architecture-history-20261008.md):99 Node /
453 JVM PASS, включая23 новых state/chat/store проверок;46 focused native PASS
(5 history +5 arrivals в каждом из3 режимов,16 documents/outbox/WS regression).
Страница принимается один раз; повтор/цикл курсоров и отсутствующий body не
запускают read storm. Canonical user-context index убирает дополнительный copy/
scan на streaming. Real rendered anchor сохраняется точно: answer/−480px
во всех режимах; loaded USER виден, спиннер завершается. Catch-up даёт16
аппаратных кадров и36/36/37 положений viewport; reduced motion PASS.
Просмотрены12 PNG; matched reader имеет0 изменённых пикселей нижеy100.
Build/lint PASS,0 errors/149 прежних warnings;4 preferences SHA256 сохранены
при установке поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Сеть/экран эмулятора восстановлены, эмулятор остановлен. Физический телефон
и live Pi history не проверялись; изменения локальные, публикация не выполнялась.

## Локальная декомпозиция просмотра документов — 8 октября 2026

[Изменения и границы проверки](architecture-documents-20261008.md):98 Node /
430 JVM PASS, включая18 новых state/chat integration проверок;44 focused native
PASS (8×3 режима +20 renderer/WS/LaTeX/attachments/outbox regression).
Close/Back/destroy снимают ожидание документа; поздний ответ не открывает окно
снова, dismissal старого окна не снимает новый запрос. Идентичное содержимое
сохраняет reader после реальной прокрутки817/694/694px и parseCount1→1.
Просмотрены12 PNG; matched rich preview имеет0 изменённых пикселей нижеy100.
Build/lint PASS,0 errors/149 прежних warnings;4 preferences SHA256 сохранены
при установке поверх. Версия0.6.007/code14, пакет/сертификат/зависимости прежние.
Отмена waiting локальная, remote read не прерывается. Физический телефон и live
файлы не проверялись; изменения локальные, публикация не выполнялась.

## Локальная декомпозиция вложений — 8 октября 2026

[Изменения и границы проверки](architecture-attachments-20261008.md):97 Node /
412 JVM PASS, включая36 новых state/stream/batch проверок;43 focused native PASS
(10×3 режима +6 outbox +7 transcription). Импорт через настоящий ContentResolver
с synthetic provider проверил file/image-байты, portrait Bitmap, один metadata
query на вложение, точный общий бюджет и сохранение успешного префикса. Остаток
меньше64KiB больше не увеличивается. onStop сохраняет импорт, onDestroy снимает
ticket и закрывает stream; отмена освобождает только неподтверждённые превью.
Просмотрены18 PNG; matched normal thumbnail имеет0 изменённых пикселей вне часов.
Build/lint PASS,0 errors/149 прежних warnings. Все4 preferences имеют прежние
SHA256 после установки поверх; версия0.6.007/code14, пакет/сертификат/зависимости
сохранены. Системный documents picker/физический телефон не проверялись;
изменения локальные, публикация не выполнялась.

## Локальная декомпозиция транскрипции — 8 октября 2026

[Изменения и границы проверки](architecture-transcription-20261008.md):96 Node/
376 JVM PASS, включая25 новых state/HTTP/file-ownership проверок;54 focused
native PASS (13×3 режима +12 updater/outbox +3 production handoff).
onStop/onStart сохраняют один запрос; onDestroy отменяет HTTP и отсекает поздний
результат. PCM остаётся доступным reader до выхода; queued cancel удаляет его
до HTTP. Actual AudioRecord → synthetic HTTP → production draft прошёл в normal,
360dp font1.3 dark и360dp font2 light без отправки prompt. Просмотрены15 PNG;
matched spinner имеет0 изменённых пикселей вне clock/progress. Build/lint PASS,
0 errors/149 прежних warnings, версия0.6.007/code14 и сертификат сохранены.
Реальная транскрипция/физический телефон не проверялись; изменения локальные.

## Локальная декомпозиция диктовки — 7–8 октября 2026

[Изменения и границы проверки](architecture-voice-20261007.md):95 Node/351 JVM
PASS, включая34 новых PCM/capture/state/HTTP проверок;30 focused native PASS
(8× normal/360dp font1.3 dark/360dp font2 light +6 updater regression).
Cancel/Back/onStop снимают запись и отсекают поздний файл; очередь meter ограничена
одним ожидающим callback. Исходная разметка воспроизведена synthetic fixture:
matched normal modal y1345–2399 имеет0 изменённых пикселей, все9 новых PNG
просмотрены. Реальный AudioRecord в эмуляторе возвращает приватный PCM без HTTP.
Debug/androidTest build и lint0 errors/149 прежних warnings; восстановленный
окончательный voice fixture дополнительно прошёл6/6. Версия0.6.007/code14,
сертификат прежний. Физический телефон/реальная транскрипция/process-kill не
проверялись. Owned emulator восстановлен и остановлен; изменения локальные.

## Локальная декомпозиция обновлятора — 7 октября 2026

[Изменения и границы проверки](architecture-updater-20261007.md):94 Node/317 JVM
PASS, включая33 новых state/HTTP проверок;24/24 focused native проверки в normal,
360dp/font1.3/dark и360dp/font2/light. Отмена/закрытие отсекают поздние результаты,
permission Later снимает pending install; orphan cleanup сохраняет active partials.
Два matched offer/permission PNG имеют0 изменённых app pixels; все9 новых modal
PNG просмотрены. Debug/androidTest build и lint0 errors/149 прежних warnings.
Версия0.6.007/code14 и сертификат прежние; нового push/release/delivery нет.
Реальный installer, future-release download/upgrade и физический телефон не
проверялись. Owned emulator восстановлен и остановлен; live hosts не менялись.

## 0.6.007 — стабильная прокрутка, плавные события и архитектура, 7 октября 2026

[Релиз и доказательства](release-0.6.007.md):93 Node/284 JVM PASS,99 synthetic UI
PASS и1 live opt-in skipped (`OK (100 tests)`) на финальном APK; по29 focused PASS
в360dp dark1.3× и light2×. Hardware reveal дал16 разных кадров в каждом режиме,
catch-up —36/36/37 позиций. Подпись совпала с опубликованной0.6.006; upgrade
code13→14 без удаления сохранил SHA256 всех9 приватных файлов. Build PASS,
lint0 errors/149 warnings. Физический телефон/live hosts не проверялись.

## Панели модели и effort — 7 октября 2026, без нового релиза

[Декомпозиция configuration panels](architecture-configuration-20261007.md):93 Node/
284 JVM PASS, включая29 новых state/projection проверок; финальный APK прошёл
native14/14 в normal,360dp1.3× dark и360dp2× light (42 PASS). Один capability
projection обслуживает выбор модели, поиск, effort и tier без повторных JSON scans.
Проверены confirmed rollback, порядок ACK/partial reports и ошибка после закрытия
панели. Два static before/after сравнения совпадают с явными исключениями system
bars, spinner и transient tier feedback. Debug/androidTest build PASS,
lint0 errors/149 warnings; версия0.6.006/code13. Физический телефон и live hosts
этим локальным прогоном не проверялись.

## Каталог, Usage и история — 7 октября 2026, без нового релиза

[Следующий слайс декомпозиции](architecture-catalog-20261007.md):92 Node/255 JVM PASS,
в том числе30 новых state/projection проверок; native8/8 в normal,360dp1.3× dark
и360dp2× light (24 PASS). Детали Usage открываются без повторного JSON scan;
history сохраняет unique date-run keys при pagination, guards stale/duplicate
callbacks и pending delete confirmation. Четыре matched static before/after PNG
совпадают с явным исключением системных bars, spinner и fixture timestamps.
Debug/androidTest build и lint0 errors/150 warnings; версия0.6.006/code13.
Физический телефон/live hosts этим локальным слайсом не проверялись.

## Ревью и стабильность чата — 7 октября 2026, без нового релиза

[Ревью, декомпозиция и новые события](review-architecture-20261007.md):91 Node/225 JVM PASS; полный API35 UI93 synthetic PASS/1 live opt-in skipped (`OK (94 tests)`), финальный native runtime7/7 и arrival5/5 в normal/360dp1.3× dark/360dp2× light. Сохранены pixel anchors при новых ответах/tools/stream/history и actual stop/start; hardware16 кадров подтверждают reveal,36/36/37 позиций — smooth catch-up. Учтены motion off, границы cached history и исправленный font2 fixture. Шесть matched static before/after PNG имеют0 изменённых app pixels. Markdown1 parse вместо2; dock counters2→2 вместо6→10. Debug/androidTest build и lint0 errors/150 прежних warnings. Полный gate предшествовал последним bounded epoch/capsule fixes; final APK проверен225 JVM, focused UI и display matrix. Новая публикация и live Pi/Orca/gateway не выполнялись.

## Локальная архитектура — 7 октября 2026, без нового релиза

[Декомпозиция read-only orchestration и следующие слайсы](architecture-work-20261007.md):85 Node/211 JVM PASS;82 synthetic UI PASS +1 live opt-in skipped (`OK (83 tests)`),9-case orchestration normal/narrow/font/theme прогоны, сборка/lint0 errors. Семь matched dark before/after PNG имеют0 изменённых app pixels, immutable baseline сохранён. Android counter подтвердил устранение повторного JSON aggregation на тике часов:4→6 до,2→2 после. Нового APK-релиза/deployment/Telegram delivery нет; версия остаётся0.6.006/versionCode13. Ограничения физического устройства/live-сети сохраняются.

## 0.6.006 — compact UI, Queue и workflows, 7 октября 2026

Актуальные проверки: [release-0.6.006.md](release-0.6.006.md):82 Node,185 JVM,81 synthetic UI PASS/1 live opt-in skipped (`OK (82 tests)`); по6 focused display/font/theme проверок именно0.6.006 и предыдущие широкие UI матрицы, визуальный просмотр. Upgrade опубликованной0.6.005→0.6.006 сохранил SHA256 всех9 существующих приватных файлов. Прежний сертификат, versionCode13. Billy прямо разрешил GitHub-релиз и затем отправку в «Парилка228». Live Pi/Orca/gateway не трогать.

## 0.6.005 — UI хотфикс, 7 октября 2026

Актуальные проверки: [release-0.6.005.md](release-0.6.005.md):82 Node,173 JVM,64 synthetic UI PASS/1 live opt-in skipped (`OK (65 tests)`), обе15-case display/font/theme матрицы, визуальный просмотр и upgrade/rollback с сохранением приватных данных. GitHub-релиз разрешён; Telegram не требуется.

## 0.6.004 — Kotlin/Compose, 7 октября 2026

Актуальный результат и ограничения: [release-0.6.004.md](release-0.6.004.md). Ниже сохранены исторические проверки; они не заменяют проверку нового APK.

## Проверки 0.6.000 — 5 октября 2026

## Новый релиз

- Node: **48/48 PASS**, включая условную подписку без повторной передачи неизменённых сообщений, изменения/удаления, сброс checkpoint, отдельный бюджет history/prompt и доставку двух изображений.
- Android JVM: **125/125 PASS**. Проверены приватный дисковый кеш, разделение серверов/токенов/сессий, срок и размер хранения, восстановление viewport, слияние истории, выбор GitHub-релиза и ограничения URL/digest.
- Android instrumentation: **31/31 PASS** на API35 ARM64 (полный финальный прогон). Старый тест Material You заменён проверкой фиксированной палитры. Добавлены проверки исчезновения loader, цвета пузыря, чтения APK с расширением `.part`, сертификата и границ FileProvider. В промежуточном прогоне два gesture-теста упали, отдельный и повторный полный прогон прошли.
- APK `0.6.000` (versionCode7) и test APK успешно собраны и установлены поверх предыдущей версии в эмуляторе; финальный полный прогон:31/31. Подпись совпадает с0.5.1 (проверено apksigner). SHA256 APK: `eb4147f0cd7c180e9bfe75e480c8403e77648a6d531abd5b92f2bf68d531aa96`.
- Read-only запрос к установленной Orca подтвердил реальные окна лимитов Codex/Cursor. Grok вернул ошибку без чисел; приложение не подставляет нулевое использование.
- Полная цепочка обновления через GitHub и подтверждение системной установки на физическом телефоне пока не проверены. Обновлятор проверяет SHA256, package ID, более новый versionCode и точное совпадение сертификатов. Первая установка этой версии — вручную.
- Визуализация workflows/субагентов и окончательная английская локализация технических ошибок не заявляются готовыми.

## Предыдущий релиз 0.5.1

## Автоматические проверки текущего релиза

- Node: **37/37 PASS** — HTTP/WSS bearer и Origin, routing/dedupe, session ownership, archive resume/new/close/delete, path-prefix, фильтр служебных сессий, навыки/MCP, приватная запись файлов и лимиты, authenticated transcription endpoint.
- Android JVM: **109/109 PASS** — протокол и состояния, timeline с отдельными progress-блоками и Steer-границами, читаемые аргументы, файлы, тайминг, Markdown и reconciliation.
- Android instrumentation: **28/28 PASS** на API35 ARM64, 1080×2400; аппаратный GPU, animation scales1. Проверяются реальные finger swipe/tap, effort и промежуточные положения пружины, Queue/Steer picker, клавиатура, одинаковая геометрия скелетонов/истории, «+» пустого пространства, подтверждения close/delete, независимое раскрытие блоков после завершения, прокрутка длинного ответа и сохранение позиции читателя, сохранение pending-текста при повторном входе.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest`: BUILD SUCCESSFUL. APK и test APK установлены через ADB.
- `git diff --check`: PASS. Локальные deployment-notes, токены и артефакты исключены из Git.

UI-тесты используют вымышленные сессии и данные; не отправляют запросы провайдеру и не закрывают пользовательских агентов. Node lifecycle-тесты используют подставной Orca transport и временные настоящие session files для проверки unlink.

## Диктовка и хосты

- Настоящий установленный в Orca sherpa-onnx + Parakeet TDT v3 int8 распознал синтезированную PCM-запись16000Hz mono: `Hello Billy, this is a test of voice dictation.` Результат получен локально, не через mock/облачный STT. Тишина вернула пустой текст.
- Внутренний speech RPC Orca требует paired mobile client. Relay **не обходит pairing и не выдаёт себя за такой клиент**: для диктовки запускает отдельный временный worker с уже установленной библиотекой и файлами модели. Настройки и активная диктовка Orca не изменяются.
- Gateway Mac обновлён и перезапущен; существующий живой Pi сам переподключился. `/health` по публичному HTTPS200, `/api/catalog` без токена401.
- Runtime-код обновлён на Linux-хосте, Node-тесты прошли, `pi-mobile.service` active. Ни один существующий Pi не перезапускался и не получал `/reload` автоматически.
- Диктовка проверена с моделью на Mac. На Linux требуется настроить постоянные пути к имеющемуся sherpa-onnx/Parakeet; установщик не скачивает их автоматически.

## Что НЕ является проверенным этим прогоном

- Физический Android-микрофон, камера/галерея конкретного производителя и сотовая сеть: не проверены. Получение результата STT проверено настоящим аудио на хосте; мобильное permission/capture/transcribe требует проверки на телефоне.
- Новые close/delete команды не применялись к пользовательским рабочим сессиям ради проверки. Их безопасность и операции проверены временными файлами/инъекцией транспорта.
- Effort во время работы требует новой версии загруженного Pi-расширения; обновление файлов/gateway не заменяет безопасный `/reload` после завершения задачи.
- Это не независимый security-аудит. APK debug-signed, не production/Play Store.

## Историческая интеграционная проверка (до 0.5.1)

Ранее на специально созданных Pi-сессиях были проверены отправка текста и картинки с Android, настоящий tool read/image result, изоляция двух разных workspace/session ID, публичный WSS и reconnect без повторной отправки. Эти проверки не подменяют текущие ограничения выше.

## Артефакт

GitHub prerelease `v0.5.1`: `Pi-Mobile-0.5.1.apk` и `SHA256SUMS.txt`. Контрольная сумма относится к загруженному APK; токенов в APK нет.
