# Декомпозиция диктовки — 7–8 октября 2026

Локальное продолжение [слайса обновлятора](architecture-updater-20261007.md).
Запись микрофона, PCM-правила, состояние панели и её Compose-разметка получили
отдельных владельцев. Версия остаётся0.6.007/versionCode14; это изменение рабочего
дерева, а не новый опубликованный APK. Предыдущий слайс обновлятора сохранён.

## Изменения

- `core/PcmAudio` задаёт общие правила PCM16 little-endian mono:16000Hz,
  минимум3200 байт, максимум19_200_000 байт/600 секунд, чётная длина. RMS сохраняет
  прежний gain4 и предел1; время вычисляется по действительно записанным семплам.
- `media/PcmRecorder` владеет AudioRecord, worker-потоком и отдельным приватным
  `cache/dictation/recording-*.pcm`. Инъекции source/work/delivery позволяют
  проверить чтение и владение ресурсами без устройства. Finish/cancel останавливают
  source один раз; worker освобождает его и закрывает stream. Ошибка запуска,
  чтения, слишком короткая запись или отмена удаляют только принадлежащий capture
  файл. Переданный вызывающему коду файл поздняя отмена больше не удаляет.
- `features/voice/RecordingSession` через порты управляет одним capture и его
  deadline, состояниями Listening/Finishing/Ended и48 показаниями волны. Cancel
  сначала снимает request identity, затем отменяет capture; поздний файл удаляется,
  поздние meter/error/timer не меняют панель. Повторный результат не передаёт файл
  второй раз и не удаляет уже принятый файл. Finish отключает кнопку до результата.
- `features/voice/RecordingScreen` содержит прежнюю геометрию и явные callbacks.
  `ui/DictationRecorder` остаётся публичным facade с прежними constructor,
  `cancel()` и константами; его размер уменьшился с97 до46 строк. Он связывает
  Android deadline/modal host с feature state и PCM capture.

В исходном `ComposeSheet.apply { setOnCancelListener { cancel() } }` вызов
`cancel()` разрешался в Dialog receiver. Явный `this@DictationRecorder.cancel()`
теперь связывает Back/внешнее закрытие с владельцем записи. Отмена кнопкой и
существующий `ChatActivity.onStop` используют тот же путь. Ошибка запуска,
ожидающая доставки в main Handler, также подавляется после отмены facade.

У capture теперь максимум один ожидающий meter callback; он получает последнее
показание. Вместо поста на каждый3200-byte chunk при задержке main thread
промежуточные значения объединяются. Synthetic чтение640_000 байт/200 chunks
создало ровно один ожидающий meter и отдельный completion; доставлены реальные
20 секунд и RMS последнего chunk. Это проверка очереди, не замер FPS/CPU телефона.
Нулевой read ждёт10ms вместо непрерывного busy loop; нечётный или превышающий
запрошенный размер chunk отвергается до записи.

`HttpApi.transcribe` использует те же PCM bounds и sample rate. Нечётный PCM
теперь отвергается до HTTP, как уже требовал сервер. Binary body, endpoint,
bearer, timeout и response parsing сохранены. Запись возвращает файл прежнему
вызывающему коду: существующая HTTP-транскрипция/вставка в черновик в
`ChatActivity` не менялись, автоматической отправки prompt нет. Отмена уже
начавшейся HTTP-транскрипции после передачи файла — отдельная граница.

`owners.json`, паспорта, архитектурная карта и llms.txt описывают новых владельцев.
Source contracts запрещают Android/HTTP/media в recording state, Android/HTTP
в PCM projection, file/HTTP/capture imports в presentation; допустимые прежние
data/renderer leaves сохранены. Отрицательные Node-проверки подтверждают эти
границы. Source guard дополняет компилятор и поведенческие тесты; это не полный
анализ reflection или произвольного Kotlin-кода.

## Проверка

- `npm run quality`:95 Node PASS, owner/import checks и SHA256 всех10 исходных
  UI baseline сохранены.
- JVM:351 PASS,0 failures/errors/skips. Новые34 проверки:6 PcmAudio,
  13 PcmRecorder,13 RecordingSession и2 SpeechUpload. Проверены PCM signed-LE/RMS,
  точный ten-minute bound, cancellation до worker/во время read/до UI delivery,
  один stop/release, частичные файлы, две независимые записи, очередь meter,
  duplicate/late результаты, timer/stop/start ошибки и передача file ownership.
  SpeechUpload проверяет фактическое бинарное тело, заголовки и отклонение
  некорректного файла через synthetic OkHttp interceptor без реальной сети.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`:
  BUILD SUCCESSFUL; lint0 errors/149 прежних warnings. После временного baseline
  fixture восстановлен окончательный шестисценарный `RecordingUiTest`, test APK
  пересобран и снова прошёл6/6 на установленном финальном production APK.
- Native API35:30 focused PASS — по8 в1080×2400/font1/light,
  945×2100 (360dp при density420)/font1.3/dark и360dp/font2/light, затем6
  regression проверок обновлятора. Шесть RecordingUiTest используют production
  facade/state/modal с controlled capture: Cancel, Finish/disabled control,
  Back во время finishing, late result старой записи, настоящий Activity.onStop
  и возврат, startup/capture error. Дополнительно в каждом режиме проходят
  существующие AudioRecord/private-file и transcription spinner/draft проверки.
  Это focused matrix, а не повтор полного UI suite опубликованного релиза.

AudioRecord-проверка действительно открывает Android capture в эмуляторе,
читает PCM, завершает запись и получает приватный файл; эмулятор запущен без
host audio. Consumer теста удаляет файл, не вызывает HTTP и не отправляет prompt.
Для controlled native fixtures сеть эмулятора отключена, отсутствие activeNetwork
проверяется явно; сохранённые connection/credential preferences не очищались.

Для геометрии исходное Compose body скопировано в отдельный synthetic fixture
с теми же48 значениями и00:02. Это воспроизведение исходной разметки, не запись
старым микрофонным pipeline. Первый снимок оказался со шрифтом2×; сопоставимый
baseline переснят с1080×2400/font1/light. Финальный modal начинается на y1345
в обоих PNG; область y1345–2399 имеет0 изменённых пикселей. Вне неё меняются
системные часы и spinner фонового synthetic loading, поэтому равенство всего
экрана не заявляется. Все9 новых PNG (Listening/Finishing/AudioRecord ×3 режима)
просмотрены: controls и текст помещаются; font2 переносит subtitle и Finishing…,
сохраняя доступные кнопки и нижний inset.

Доказательства находятся в `/Users/billy/temp/pi-mobile-voice-20261007/`:
`tagged-gates.txt`, `accepted-quality.txt`, `native-accepted-*.txt`,
`updater-regression.txt`, `restored-test-gates.txt`, `restored-native-final.txt`,
`before/recording-baseline-normal.png`, три каталога новых PNG и checklist.
Final local debug APK SHA256:
`475cd3d77ca7d744db3d69cfbd21826b94014da06d9f5a69d0cb2b9d855971c9`.
AAPT подтверждает прежние package/versionName/versionCode; apksigner — certificate
SHA256 `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.

## Границы проверки

Физический микрофон/телефон, качество распознавания речи, реальная HTTP
транскрипция и process-kill во время capture не проверялись. Cleanup после
process death не добавлялся; проверено владение и удаление файлов при обычных
ошибках/отмене. Live Pi/Orca/gateway не менялись. Чужой `server/Caddyfile`
сохранён с исходным SHA256; production Java по-прежнему отсутствует.

Owned emulator восстановлен до1080×2400/font1/light, его исходные network
settings возвращены после force-stop приложения; затем эмулятор остановлен.
Изменения остаются локальными; новый push/release/delivery не выполнялся.
