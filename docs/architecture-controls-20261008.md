# Служебные команды чата — 8 октября 2026

Следующий локальный слайс после пагинации: управление `/mcp`, `/name` и Stop
выделено из ChatSession. Формирование MCP-статуса отделено от native окна.
Версия 0.6.007/code14, пакет, сертификат и зависимости сохранены.

## Владельцы и поведение

Internal `features/chat/ControlSession` владеет типизированной командой,
отправленным draft, request ID и результатом. У служебных команд один pending
ticket; Stop имеет отдельный ticket, поэтому остаётся доступным при ожидании
MCP или переименования. Порты принимают команду/Stop, capability, очистку
совпадающего draft и notice. В классе нет Android, JSON, UI, net или storage.
ChatSession сохраняет public send/abort, session/type guards, JSON-адаптацию
и transport boundary. Новый closeControls используется platform shell.

Синтаксис прежний: outer trim, точный `/mcp`, `/name` или `/name ` с именем.
Read-only, capability, вложения и незавершённая предыдущая команда блокируют
служебное действие. Распознанная заблокированная команда не становится prompt.
Существующий текст ошибок сохранён; пустое имя требует `/name New name`.
Перед вызовом порта резервируется ticket. Null/empty request ID и exception
снимают только его; reentrant close не может восстановить ожидание.

MCP-data принимается один раз и только для собственного MCP-запроса. Чужие,
повторные, запоздалые ответы и MCP-data для `/name` не открывают окно и не
заменяют принятый body. Ticket остаётся до ACK. Успешный MCP ACK без данных
показывает `Pi returned no MCP status` и сохраняет draft для явного повтора.
Обычный успешный результат очищает только совпадающий отправленный draft;
новый текст пользователя сохраняется. Rename ACK не придумывает новый title:
его по-прежнему сообщает authoritative catalog.

Повторные нажатия Stop до ACK или uncertain не посылают дополнительные abort.
После terminal result возможен новый явный Stop. ACK подтверждает приём
команды, а не завершение работы: статус RUNNING, transcript и draft не
меняются. Чужие и повторные terminal callbacks не снимают новый ticket.

onStop сохраняет ожидание. onStart сверяет pending ledger: живой ticket
остаётся, исчезнувший получает unknown result без повторной отправки.
onDestroy закрывает локального владельца до остальных cleanup. Поздние
data/ACK не меняют draft и не открывают старое окно. Close не отменяет
remote read, не отправляет Stop и не меняет остальные владельцы запросов.

Pure `features/chat/McpProjection` возвращает immutable text/observedAt.
Названия, порядок, дубли серверов, toolCount, status labels, разделители
и fallback сохранены. Native time formatting остаётся в Activity.
Projection и создание окна происходят после guard принятия данных: дубли
больше не повторяют JSON-проход и создание modal. Это ограниченная
оптимизация обработки ответов, не замер общей производительности приложения.

Activity владеет одним MCP AlertDialog. При замене dismissal старого окна
не очищает ссылку на новое; onDestroy синхронно снимает ссылку перед dismiss.
Finishing/destroyed shell не открывает окно даже при позднем прямом effect.
Сохранились native Material title/message/Done, геометрия и оформление.
История, outbox, reader anchors, catch-up/reduced-motion и wire protocol
этим слайсом не меняются.

## Проверка финального кода

- `npm run quality`: 100 Node PASS. Owners/import/sourceContract guards
  и все 10 pinned baseline SHA256 PASS. Новый negative contract запрещает
  platform/transport/storage/presentation в ControlSession и state
  dependency в pure McpProjection.
- Полный JVM gate: 476 PASS, 0 failures/errors/skips. Новые 23 проверки:
  ControlSession14, McpProjection4, ChatSession integration5. Проверены
  синтаксис, read-only/capability/attachments, независимые control/Stop,
  duplicate/foreign/wrong-kind, missing body, rejection/timeout/setup failure,
  detached/live ledger, close/reentry и сохранение нового draft.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint 0 errors/149 прежних warnings (+3 information), JDK17/API35.
  Production: 120 Kotlin/0 Java.

- На финальном production APK: 52 focused native PASS. В каждом из трёх
  режимов — 8 ControlUiTest +5 прежних ChatArrivalUiTest; ещё 13 regression:
  6 outbox, 5 history и 2 actual WS/history-prefix checks. Все fixtures offline
  с synthetic transport/data; настоящие Pi prompts не использовались.
- Режимы: 1080×2400/density420/font1/light;
  945×2100/density420/font1.3/dark; 945×2100/density420/font2/light.
  Через настоящую Activity проверены содержимое и Done MCP-dialog, duplicate/
  foreign/wrong-kind data, замена окон, missing body и сохранение `/mcp`,
  actual Stop button/single-flight/ACK/rejection, stop/start/detached ledger,
  destroy/late effects, read-only/no prompts и неизменный draft/status.
- Прежние reader-anchor/catch-up/reduced-motion checks прошли во всех режимах.
  Catch-up дал 16 различных аппаратных кадров в каждом режиме и 36/36/37
  промежуточных положений viewport. History и outbox regression прошли.
- Просмотрены 15 итоговых PNG: MCP-dialog, rename notice, missing status,
  Stop/draft и main-chat reader в трёх режимах. Matched normal MCP-dialog
  имеет 0 изменённых пикселей ниже y100; весь diff — системные часы,
  bbox (131,54,150,82). Before получен тем же native fixture на предыдущем APK,
  frozen baseline не изменялся. При font2 native message остаётся скроллируемым,
  Done виден и закрывает окно.
- Проверка destroy выявила отложенный native dismissal callback. Ссылка теперь
  снимается синхронно до dismiss; все code gates и native режимы выше повторены
  на окончательном APK после этой поправки.

## Артефакты и границы

Финальный локальный debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`. SHA256:
`60d76719e3eb857baa40ab847ebee6849c71005847659205441f175509fb5578`.
Package `ru.billyhargrove.pimobile`, versionName0.6.007/versionCode14.
Проверен прежний SHA256 signing certificate:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Установка поверх предыдущего APK без clear/uninstall сохранила SHA256
всех 4 preferences; содержимое и токены не выводились. По сравнению с началом
слайса изменены только два прежних production-файла: ChatActivity и
ChatSession; добавлены ControlSession и McpProjection. Ранее сделанные
updater/voice/transcription/attachments/documents/history сохранены.
server/Caddyfile сохранил SHA256.

Доказательства и checklist: `/Users/billy/temp/pi-mobile-commands-20261008/`.
Перед завершением app/test force-stopped; normal display/font/theme и
исходные airplane0/Wi-Fi1/mobile-data0 восстановлены с read-back.
Созданный эмулятор остановлен, его процесс завершился с кодом0; `adb devices`
после остановки пуст.
Физический телефон и live Pi commands не проверялись. Pi/Orca/gateway не
менялись, реальные prompts/речь не запускались. Изменения локальные; новый
bump, commit, push и публикация не выполнялись.
