# Отправка настроек чата — 8 октября 2026

Следующий локальный слайс после служебных команд: ожидание изменения модели,
effort и tier выделено из ChatSession. Версия 0.6.007/code14, пакет, сертификат
и зависимости сохранены.

## Владельцы и поведение

Internal `features/chat/ConfigurationSession` владеет типизированными nullable
provider/model/effort/tier, одним request ticket, terminal result, сверкой с
pending ledger и локальным close. Порты — send, idle, pending, completed и notice;
в классе нет Android, Compose, JSON, transport или storage. ChatSession сохраняет
public configure/configurationPending, scoped session/ACK/uncertain routing,
canonical configuration JSON и адаптацию transport. ModelSettingsSession и
QuickEffortSession по-прежнему владеют draft и подтверждёнными полями панели.

Read-only не отправляет изменения; модель требует IDLE, effort/tier разрешены
во время работы в рамках прежнего modelChange флага. Валидация модели/effort/tier
остаётся в существующем transport/gateway/Pi. Nullable поля и wire semantics
не меняются. Повторная отправка при pending даёт прежний inline notice.

Ticket и pending резервируются до transport call. Reentrant вызов не может
отправить второй запрос до возврата первого request ID. Null/empty IDs или
setup exception завершают ожидание с обратной связью, не оставляют панель
заблокированной и позволяют новый явный Apply. Close во время pending callback
не начинает запись; close во время transport call не восстанавливает ticket
после возврата и не показывает запоздалую ошибку.

Собственный ACK или uncertain завершают ticket ровно один раз. Чужие session/
request ID, старый ACK и повторный timeout не завершают новый запрос. Успешный
ACK не меняет canonical model/effort/tier/status и не потребляет draft:
configuration reports остаются авторитетными. Partial/foreign reports не
снимают pending. Панели сохраняют прежний rollback отдельно по полям и ревизии
отчёта; принятие изменений не закрывает панель автоматически. Ошибка после
ручного закрытия отправившей панели по-прежнему видна в chat notice.

onStop сохраняет ticket. onStart оставляет живой pending ledger, а исчезнувший
сообщает unknown result без replay. onDestroy закрывает локальное ожидание до
остальных cleanup; обе ссылки на model/effort панели снимаются синхронно перед
dismiss. Finishing/destroyed shell не открывает панель и не обрабатывает поздний
configuration effect. Закрытие не отменяет remote configure и не отправляет
abort: транспорт заканчивает запрос штатным ACK/deadline. Остальные history/
document/control/outbox tickets остаются у своих владельцев.

Оптимизация ограничена одновременными запросами: прежнее окно повторного входа
между write и установкой pending закрыто. Это не замер общей FPS/скорости сети.
UI-геометрия, read-only inspection, reader anchors, catch-up и reduced motion
сохранены.

## Проверка финального кода

- `npm run quality`: 101 Node PASS; новый negative source contract запрещает
  platform/transport/storage/panel dependencies в ConfigurationSession.
  Owners/import/sourceContract guards и все 10 pinned baseline SHA256 PASS.
- Полный JVM gate: 493 PASS, 0 failures/errors/skips. Новые 17 проверок:
  ConfigurationSession12 + ChatSession integration5. Проверены nullable fields,
  read-only/idle policy, single-flight, owned/foreign/old ACK, rejection, unknown/
  live ledger, null/empty/exception setup, close и reentry, canonical reports,
  неизменный draft/status и независимость document ticket.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint 0 errors/149 прежних warnings (+3 information), JDK17/API35.
  Production: 121 Kotlin/0 Java.

- На финальном production APK: 56 focused native PASS. В каждом из трёх
  режимов — 6 ConfigurationCommandUiTest +3 прежних ConfigurationPanelUiTest
  +5 ChatArrivalUiTest; ещё 14 regression: 8 controls +6 outbox. Все fixtures
  offline с synthetic transport/data; настоящие Pi configure не использовались.
- Режимы: 1080×2400/density420/font1/light;
  945×2100/density420/font1.3/dark; 945×2100/density420/font2/light.
  Через настоящие Activity/панели проверены Apply/tier, поля wire, один запрос,
  scoped/old ACK, partial report, pending/confirmed rollback, ручное закрытие,
  setup exception/null ID/явный retry, stop/start/live/missing ledger,
  destroy/late/direct effects и отсутствие действий/записей в read-only.
- Прежние reader-anchor/catch-up/reduced-motion checks прошли во всех режимах.
  Catch-up дал 16 разных аппаратных кадров в каждом режиме и 36/36/37
  промежуточных положений viewport. Controls/outbox regression тоже прошли.
- Просмотрены 15 итоговых PNG: quick panel, rejection, model Apply, unknown
  result и main-chat reader в трёх режимах. Matched normal rejection panel
  имеет 0 изменённых пикселей ниже y100; diff только системных часов,
  bbox (132,54,150,82). В normal quick panel отличаются 8188 пикселей только
  внутри затухающей подсветки нажатой effort-button, bbox (310,2184,416,2290);
  вне этой области ниже y100 — 0 изменённых пикселей. Diff/crops просмотрены.
  Before получен тем же native fixture на предыдущем APK; frozen baseline
  не менялся. При font2 Apply/закрытие доступны, сообщения ошибок читаемы.
- При настройке новых UI fixtures уточнены фактические semantics: Fast виден
  как text, read-only не показывает configuration actions. Итоговые 14 случаев
  прошли в каждом режиме с окончательными assertions. Production APK при
  этих поправках не изменился; финальная test APK сборка и lint прошли.

## Артефакты и границы

Финальный локальный debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`. SHA256:
`ba16dd84b3c2c2cf2d8735a2c30776991440fdc117682ecdb418b5fe320f905f`.
Package `ru.billyhargrove.pimobile`, versionName0.6.007/versionCode14.
Прежний SHA256 signing certificate проверен apksigner:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Установка поверх предыдущего APK без clear/uninstall сохранила SHA256 всех
4 preferences; содержимое и токены не выводились. От начала слайса изменены
только два прежних production-файла: ChatActivity и ChatSession; добавлен
ConfigurationSession. Все предыдущие локальные updater/voice/transcription/
attachments/documents/history/controls сохранены. server/Caddyfile сохранил SHA256.

Доказательства и checklist: `/Users/billy/temp/pi-mobile-config-command-20261008/`.
Перед завершением app/test force-stopped; normal display/font/theme и
исходные airplane0/Wi-Fi1/mobile-data0 восстановлены с read-back.
Созданный эмулятор остановлен, его процесс завершился с кодом0; `adb devices`
после остановки пуст. Новые configuration PNG удалены с эмулятора после
сохранения доказательств в task folder.
Физический телефон и live Pi configure не проверялись. Pi/Orca/gateway не
менялись, реальные prompts/речь не запускались. Изменения локальные; bump,
commit, push и публикация не выполнялись.
