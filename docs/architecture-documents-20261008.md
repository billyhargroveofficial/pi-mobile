# Чтение документов — 8 октября 2026

Следующий локальный слайс после подготовки вложений: выделены владелец ожидания
документа и Compose-представление. Исправлено повторное открытие окна после
закрытия при запоздалом ответе; повторное получение того же содержимого сохраняет
позицию чтения. Версия0.6.007/code14, пакет, сертификат и зависимости прежние.

## Владельцы и поведение

Internal `features/chat/DocumentSession` содержит read-only single-flight,
идентичность ожидающего запроса, приём документа один раз, отмену ожидания,
закрытие и сверку с transport-ledger при возвращении. Его порты принимают путь,
request ID и готовый документ; в классе нет Android, UI, net, storage или
JSONObject. Parent для относительных ссылок вычисляется при создании документа;
прежние правила абсолютных/schemed ссылок сохранены.

`ChatSession.document` сохраняет public API и JSON/WSS-адаптацию. Сессия делегирует
адресные data/ACK/uncertain события владельцу документа. Data завершает ticket
перед открытием окна: повторный data, ACK или timeout не открывает просмотр снова
и не изменяет следующий запрос. Успешный ACK без документа сообщает
`Pi returned no document`, вместо пустого или выдуманного preview. Read-only
может читать, но документ не изменяет текст/вложения/outbox и не отправляет prompt.

`features/chat/DocumentScreen` содержит прежние геометрию, Close и Compose scroll.
Нативный Markdown передаётся как presentation callback. `ui/MarkdownPreview`
сохраняет четырёхаргументный конструктор и отвечает за modal, TextView, selectable
text, MarkdownRenderer и его cleanup. Markdown/таблицы/LaTeX остаются прежними
native leaves, без WebView, выполнения HTML/JS или сетевой загрузки картинок.

Activity связывает navigation/lifecycle и окно. Закрытие текущего preview через
Close или Back снимает ожидание следующего файла. Callback от уже заменённого
окна сверяется по идентичности и не отменяет запрос нового окна. onDestroy
закрывает владельца документа до dismiss; finishing/destroyed Activity также
отклоняет эффект открытия. Видимое окно переиспользуется только при совпадении
path и исходного текста: оно сохраняет настоящий scroll, TextView и renderer.
Изменённый или другой документ получает новый просмотр.

Отмена здесь **локальная**: WSS-протокол не содержит отмены read-команды. Уже
отправленное чтение может завершиться у сервера и снимается обычным transport
ACK/deadline, но его retired результат больше не открывает UI. Отмена не посылает
abort, не завершает Pi и не снимает чужие prompt/configure/history tickets.
onStop сохраняет ожидающее чтение; если ответ ушёл при detached listener,
возвращение сверяет ledger и сообщает неизвестный результат без повторной отправки.

## Проверка финального кода

- `npm run quality`:98 Node PASS; owner/import/sourceContract guards PASS;
  все10 закреплённых baseline SHA256 прежние. Новый negative contract test
  запрещает Android/transport/storage/renderer в DocumentSession и Android IO
  в DocumentScreen, а также обратную зависимость state → screen.
- Полный JVM gate:430 PASS,0 failures/errors/skips. Новые18 проверок:
  DocumentSession14 и ChatSession integration4. Проверены foreign/duplicate
  результаты, single-flight, cancel/close, поздний ACK/timeout, setup failure,
  исчезнувший/live ledger, reentrant ready, read-only draft и относительные ссылки.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint0 errors/149 прежних warnings; JDK17/API35. Production117 Kotlin/0 Java.
- На финальном APK44 focused native PASS:8 DocumentUiTest в каждом из3 режимов,
  плюс20 regression (3 Markdown renderer,1 owned WS data/ACK,1 прежний native
  Markdown/LaTeX,9 AttachmentUiTest,6 ChatOutboxUiTest). Документ читается через
  настоящий Activity/ChatSession JSON/data/ACK путь с synthetic transport;
  относительный link проходит через настоящий native ClickableSpan handler.
- Режимы:1080×2400/density420/font1/light;945×2100/density420/font1.3/dark;
 945×2100/density420/font2/light. Новые fixtures требуют activeNetwork=null;
 live Pi/Orca/gateway и реальные prompts не использовались.
- Native cases проверили Close/Back с pending next-read без повторного открытия,
  dismissal старого окна при новом запросе, сохранение текущего reader при
  rejection/missing body, stop/start/destroy и поздний результат, неподдерживаемые
  схемы, percent-decoded Markdown path с fragment и неизменённый read-only draft.
- Проверка чтения различает прокрутку текста и движение BottomSheet: threshold
  ≥300px относительно path-header. Реальная прокрутка normal817px, dark694px,
  large694px. После повторного ответа y TextView точно сохранился:
  −299→−299/−221→−221/−221→−221; renderer parseCount1→1 во всех режимах.
  Первая версия проверки смотрела только screen-y; её усилили и повторили все
 8 document cases в каждом режиме. Итоговые44 учитывают усиленную проверку.
- Просмотрены12 итоговых PNG: rich document, scrolled reader, error и closed
  preview в3 режимах. Native LaTeX проверяется по готовому AsyncDrawableSpan,
  не по наличию исходной формулы. Matched normal rich preview имеет0 изменённых
  пикселей нижеy100; весь diff лежит в системных часах/status x101–277/y52–87.
- Установка поверх предыдущего debug APK без очистки данных: все4 preferences
  сохраняют прежние SHA256; содержимое/токены не выводились. Ранее сделанные
  updater/voice/transcription/attachment изменения сохранены. Из старых production
  файлов этот слайс меняет только ChatActivity, ChatSession и MarkdownPreview.
  Несвязанное изменение `server/Caddyfile` byte-for-byte прежнее.

APK SHA256:
`f538dcff30904479f21ec69fbff4b46e73824cc484fa8da1a41335afc8247f8e`.
Certificate SHA256:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Evidence/checklist: `/Users/billy/temp/pi-mobile-documents-20261008/`, включая
`accepted-gates.txt`, `accepted-quality.txt`, `accepted-native-*.txt`,
`{normal,dark,large}-reading-proof.txt`,12 PNG, `accepted-rich-comparison.json`,
`artifact-checks.json` и `emulator-restore.txt`.

## Границы

Это локальная архитектура и синтетическая native проверка; реальные серверные
файлы и физический телефон не проверялись. Разбор MarkdownRenderer и server-side
authorization/path confinement/1MiB document limit не переписывались. Результаты
для закрытого/заменённого просмотра отклоняются, но remote read не прерывается.
Позиция сохраняется у уже открытого идентичного документа; закрытие или пересоздание
Activity не добавляет нового дискового хранения документов/scroll. Публикация,
изменение версии и host deployment не выполнялись.
