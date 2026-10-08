# Пагинация чата — 8 октября 2026

Следующий локальный слайс после просмотра документов: ожидание и продвижение
истории выделены из ChatSession, а проверка пользовательского контекста получила
канонический индекс. Версия0.6.007/code14, пакет, сертификат и зависимости прежние.

## Владельцы и поведение

Internal `features/chat/HistorySession` владеет epoch, курсором, request ID,
single-flight, приёмом страницы один раз, ACK и сверкой с transport-ledger. Его
порты — before/request ID, наличие канонического пользователя, loading и notice;
в классе нет Android, UI, net, storage или JSONObject. `ChatSession` сохраняет
public loadOlder/historyLoading, JSON-адаптацию, session/type guards и prepend.

Принятая страница остаётся под своим ticket до ACK. Новый live update не запускает
следующий запрос параллельно; повторный data не разбирается и не заменяет body
или cursor. После успешного ACK следующая страница нужна только при отсутствии
канонического USER и при наличии продвижения. Повтор/цикл before, пустой
continuation cursor и отсутствующая history-мета прекращают цепочку. В случае
повторного/пустого курсора с hasMore показывается обычный inline notice
`History cursor did not advance`. Принятые сообщения сохраняются.

Успешный ACK без страницы или history-data без массива messages сообщает
`Pi returned no history`. Ошибка/timeout/setup failure не запускают повтор
на каждом streaming update; явное движение читателя позволяет повторить
непринятую страницу. Принятый before больше не читается внутри этой цепочки.
Авторитетный snapshot начинает новую цепочку и снимает старый ticket: даже
same-epoch snapshot может заменить prefix при несовместимой истории. Snapshot
без history не сохраняет чужой старый cursor. Чужие session/request/type и
старый epoch не добавляют строки. Cached snapshot не читает сервер.

`core/TranscriptStore.hasUserContext` использует счётчик канонических USER.
Snapshot учитывает дубли ID; live upsert/removal обновляет счётчик одновременно
с map, включая смену роли. Prepend учитывает результат merge, где live row
имеет приоритет над устаревшим overlap. Clear/reset снимает индекс. Проверка
контекста на live update больше не делает дополнительную копию списка и полный
обход. Optimistic outbox USER не считается серверным контекстом. Остальные
render/merge операции по-прежнему работают со списком; это не оценка общей FPS
или асимптотики всего чата.

onStop сохраняет pending history. onStart сверяет ledger: live ticket остаётся,
исчезнувший становится unknown без повторной отправки. onDestroy снимает
локальное ожидание до остальных cleanup; поздний data/ACK не меняет старый
transcript. Закрытие не отменяет read на сервере: transport завершает его
обычным ACK/deadline. Оно не посылает abort и не влияет на Pi/другие commands.

Prepend сохраняет прежние stable keys, положение читателя, tailRevision и
arrivalRevision; страницы истории не раскрываются как новые live события.
Catch-up анимации и reduced-motion путь сохранены. UI-геометрия, read-only,
протокол, число сообщений в странице40 и приватные данные прежние.

## Проверка финального кода

- `npm run quality`:99 Node PASS; новый negative contract запрещает
  Android/transport/storage/renderer и state → presentation в HistorySession.
  Owners/import/sourceContract guards и все10 pinned baseline SHA256 PASS.
- Полный JVM gate:453 PASS,0 failures/errors/skips. Новые23 проверки:
  HistorySession13, ChatSession integration6 и TranscriptStore4. Проверены
  single-flight, duplicate/foreign/wrong epoch, missing body, unchanged/cyclic
  cursor, explicit retry, setup failure, detached/live ledger, close/reentry,
  read-only/no prompts и canonical/live overlap. Index сравнен с настоящим
  transcript после2000 смешанных replace/apply/prepend/remove/clear операций.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint0 errors/149 прежних warnings (+3 information), JDK17/API35.
  Production118 Kotlin/0 Java.

- На финальном production APK46 focused native PASS:5 HistoryUiTest и5
  прежних ChatArrivalUiTest в каждом из3 режимов, плюс16 regression
  (8 documents,6 outbox и2 actual WS/history-prefix checks). Все fixtures
  offline, с synthetic transport/data; реальные Pi prompts не использовались.
- Режимы:1080×2400/density420/font1/light;945×2100/density420/font1.3/dark;
 945×2100/density420/font2/light. Повторный data не заменяет принятый body,
  historySpinner действительно появляется/исчезает, загруженный USER виден
  на экране; pending stop/start и исчезнувший ledger проверены через Activity.
  Destroy отсекает позднюю страницу; сохранились read-only draft и0 writes.
- Реальный rendered anchor после prepend/data/ACK: `(answer, −480)` →
  `(answer, −480)` во всех3 режимах. LazyList показывает весь принятый набор
  строк; проверка не ограничивается прежним layout до Compose commit.
  Catch-up дал16 различных аппаратных кадров в каждом режиме и36/36/37
  промежуточных положений viewport; reduced motion тоже прошёл.
- Просмотрены12 итоговых PNG: reader, inline cursor error, видимый loaded USER
  и прежний main-chat reader в3 режимах. Matched normal main-chat reader
  имеет0 изменённых пикселей нижеy100; весь diff — системные часы,
  bbox x110–129/y54–81. Frozen baseline остаётся прежним.
- Начальные новые UI assertions обращались к semantics до Compose кадра.
  Их заменили ожиданием фактических spinner/notice/question и дополнили
  проверкой числа rendered строк; итоговые10 случаев повторены в каждом
  режиме после этого усиления. Production APK при корректировке fixtures
  не изменился.

## Артефакты и границы

Финальный локальный debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`. SHA256:
`44a0410cb19b26748e036127f08138b4c951b500e183804e2a474c5296f85f06`.
Package `ru.billyhargrove.pimobile`, versionName0.6.007/versionCode14. Проверен
прежний SHA256 signing certificate:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Установка поверх предыдущего APK без clear/uninstall сохранила SHA256 всех4
preferences; содержимое и токены не выводились. По сравнению с началом слайса
изменены только3 прежних production-файла: ChatActivity, ChatSession и
TranscriptStore; добавлен HistorySession. Ранее сделанные updater/voice/
transcription/attachments/documents остаются; server/Caddyfile сохранил SHA256.

Доказательства и checklist: `/Users/billy/temp/pi-mobile-history-20261008/`.
Перед завершением app/test force-stopped; normal display/font/theme и
исходные airplane0/Wi-Fi1/mobile-data0 восстановлены с read-back. Созданный
эмулятор остановлен, его процесс завершился с кодом0.
Физический телефон и live Pi history не проверялись. Pi/Orca/gateway не менялись,
реальные prompts/речь не запускались. Изменения локальные; новый bump, commit,
push и публикация не выполнялись.
