# Проверки релизов Pi Mobile

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
