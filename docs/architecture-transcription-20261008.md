# Транскрипция и передача в черновик — 8 октября 2026

Продолжение локальной декомпозиции [записи микрофона](architecture-voice-20261007.md).
HTTP, владение готовым PCM и приём результата в черновик вынесены из Activity.
Предыдущие слайсы записи и обновлятора сохранены. Версия0.6.007/versionCode14
не меняется; новая публикация не выполнялась.

## Владельцы и поведение

`features/chat/TranscriptionSession` — внутренний владелец одной операции передачи
готовой записи в черновик. Через порты он запускает работу, отражает busy в
существующем ChatSession и принимает результат только от текущего request ticket.
Cancel/close сначала снимают ticket и busy, затем отменяют transport. Повторный
или поздний результат не вставляет текст, не показывает ошибку и не снимает
спиннер следующего запроса. Read-only/закрытый host отвергает и удаляет полученный
файл без HTTP. Повторная передача того же активного файла не удаляет его.

`net/SpeechTranscriber` владеет готовым PCM с начала операции. Он использует
существующий bounded IO pool и main dispatcher, создаёт отдельный HTTP call
с переданным snapshot URL/token и удаляет только свой файл. Отмена до запуска
worker удаляет PCM сразу и не открывает HTTP. Во время создания call отмена
передаётся ему до execute; во время чтения вызывается `Call.cancel()`, а PCM
остаётся доступным reader до выхода. Finally очищает файл, в том числе при
ошибке reader; повторная очистка не затрагивает путь заново. Отменённый queued
UI callback не доставляет ни текст, ни ошибку. Отмена одного job не меняет другой
job, его credentials или файл.

`HttpApi.transcribe` сохраняет публичный blocking API. Он делегирует внутреннему
cancellable handle, использующему тот же endpoint `/api/transcribe`, bearer,
бинарное тело `application/octet-stream`, заголовок sample rate16000 и shared
PCM preflight. Write/read/call timeout остаются90/590/600 секунд; существующие
ограничение JSON body, обработка HTTP ошибок и response close сохранены.

Activity теперь только связывает порты с HTTP, настройками и chat callbacks.
`onStop` по-прежнему отменяет незавершённую запись микрофона, сохраняя уже начатую
транскрипцию. `onStart` той же Activity не повторяет запрос. `onDestroy` закрывает
владельца до отмены HTTP; состояние закрытого экрана не получает поздний результат.
Сохраняется запрет вставки/feedback в finishing/destroyed Activity. Поздний
permission result также не открывает новую запись во время транскрипции.

Текст вставляет прежний `ChatSession.insertDictation` в текущую позицию курсора,
сохраняя текущее содержимое черновика. Результат не отправляет prompt. Пустой
ответ показывает `No speech detected`; исключение без сообщения получает
`Could not transcribe recording` вместо ложного сообщения об отсутствии речи.
Геометрия ChatComposer/спиннера, persistence и путь ручной отправки не менялись.

Owners/passports/architecture/llms регистрируют новых владельцев. Source contract
для TranscriptionSession запрещает Android, HTTP, capture, settings и обратную
зависимость от screen; file является opaque входом для портов. Guard дополняет
компилятор и поведенческие тесты, не заменяя полный анализ Kotlin/reflection.

## Проверка

- `npm run quality`:96 Node PASS, owner/import contracts и SHA256 всех10 pinned
  UI baseline сохранены. Новая отрицательная проверка запрещает platform/HTTP/
  credentials/capture imports в состоянии транскрипции.
- JVM:376 PASS,0 failures/errors/skips. Добавлены25 проверок:12
  TranscriptionSession,12 SpeechTranscriber и1 cancellable HTTP handle.
  Проверены single-flight/read-only, sync completion/start failure, cancel/close,
  late/duplicate callbacks, ошибка без сообщения, blank speech, scoped files и
  credentials, queued/active cancellation, cleanup до main delivery и при fatal
  reader error. Blocking API тест дополнительно сверяет все три timeout.
  OkHttp application interceptor используется без сети: controlled latch
  подтверждает, что active cancel достигает настоящего call, PCM не удаляется
  до выхода reader, late result подавлен. Для уже отменённого call application
  interceptor может выполняться; проверяется cancellation flag и IOException,
  а не ошибочное предположение, что interceptor не вызывается.
- Debug/androidTest build и lint:BUILD SUCCESSFUL,0 errors/149 прежних warnings.
  Production APK после финальной компиляции test fixture остаётся тем же.
- Native API35:54 focused PASS на финальном production APK. По13 в normal
  1080×2400/font1/light,945×2100 (360dp при density420)/font1.3/dark и360dp/
  font2/light:6 новых controlled transcription cases,6 recording regressions и
  прежний spinner/draft case. Отдельно12 updater/outbox regression cases.
  Затем добавленный production handoff case прошёл по1 разу в каждом из трёх
  режимов на окончательном test APK. Это focused coverage, не повтор полного
  UI suite опубликованного релиза.

Controlled native tests проверяют настоящие Activity.stop/start/destroy и
Compose: запрос/спиннер сохраняются в фоне, результат меняет только текущий
черновик, старый ответ не влияет на новую Activity, ошибка остаётся dismissible
inline, read-only не запускает работу. При изменённом во время запроса тексте
`Keep suffix` и курсоре4 результат `speech` даёт `Keep speech suffix` с курсором11,
без transport writes и новых сообщений.

Дополнительная проверка проходит полный production путь startDictation →
AudioRecord → private PCM → Activity ports → SpeechTranscriber/HttpApi →
ChatSession/composer. Она реально записывает минимум32000 байт в эмуляторе
без host audio, явно нажимает Finish, читает бинарный RequestBody и получает
подставной HTTP JSON. Ровно один speech request вставляет `Synthetic speech`
после `Keep draft`; PCM удалён, transcript/Queue пусты. Test-only замена PiApp.api
и при необходимости разрешения микрофона восстанавливаются в finally.
Реальная сеть отключена и отсутствие activeNetwork проверяется; сохранённые
URL/token preferences не очищаются и не выводятся.

До изменения установленного APK прежний spinner case был снят в normal.
Before/after имеют0 изменённых пикселей вне явных исключений: системная верхняя
полоса y0–99 и вращающийся progress в rectangle x890–1009/y2180–2299.
`matched-spinner-comparison.json` сохраняет bounds/count; весь экран без
исключений не объявляется идентичным. Просмотрены15 новых PNG: spinner/draft/
error/existing-spinner/handoff ×3 режима. При font2 compact multiline TextField
сохраняет прежнюю одну видимую строку и показывает строку текущего курсора;
полный длинный черновик сверяется в состоянии. Controls и feedback помещаются.

Доказательства: `/Users/billy/temp/pi-mobile-transcription-20261008/` — checklist,
`full-gates.txt`, `final-test-gates.txt`, `accepted-quality.txt`, `native-*.txt`,
`regression.txt`, `handoff-*.txt`, before PNG, comparison JSON и три каталога PNG.
Final local debug APK SHA256:
`90f48e8847cbd1728bf4cdf76124cf2061d0496822a5426a49328c02d5778b47`.
AAPT подтверждает package `ru.billyhargrove.pimobile`, versionName0.6.007/code14;
apksigner — прежний certificate SHA256
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.

Физический телефон/микрофон, реальный speech service и качество распознавания
не проверялись. Process death не восстанавливает или повторяет HTTP-операцию;
новая persistence/background service не добавлялась. `Call.cancel()` отменяет
клиентский запрос; остановка вычисления на удалённом сервере не проверялась.
Live Pi/Orca/gateway не менялись; чужой Caddyfile сохранён с исходным SHA256.
Owned emulator после force-stop приложения возвращён к1080×2400/font1/light
и исходным network settings, затем остановлен. Изменения остаются локальными.
