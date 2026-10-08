# Локальная декомпозиция очереди и подтверждений — 8 октября 2026

Сверка очереди выделена в чистую QueueProjection. ChatOutbox повторно использует
готовые списки подтверждений и вызывает сохранение только при изменении их
состояния. Streaming и повторные snapshots неизменной очереди больше не кодируют
одинаковые receipts в JSON. Версия 0.6.007/code14, пакет, сертификат и зависимости
сохранены.

## Владельцы и поведение

Internal `features/chat/QueueProjection` принимает typed canonical messages и
receipts. В классе нет Android, Compose, JSON, transport, storage или команд.
Он собирает canonical USER candidates одним проходом; ACK остаётся подтверждением
принятия, а потребление очереди выполняет новый matching USER echo. Порядок
requests, существующий sameContent и private queue-baseline semantics сохранены.
Один echo потребляет одну запись; old identical prompt и repeated snapshot не
потребляют следующую. Consumed keys входят в baseline оставшихся запросов.
Проекция не изменяет входные receipts; unchanged baseline сохраняет их references.

Список canonical keys ограничивается до mapping: tailKeys читает только последние
1500 entries. Сбор USER candidates выполняется один раз для всех queued requests;
без queued receipts этот проход не нужен. Поиск matching candidates остаётся
в порядке canonical USER сообщений для каждой записи очереди.

Public ChatOutbox сохраняет constructor/send/retry/restore/thumbnail API и
единственное владение receipts/retained draft bytes. Он владеет receipt revision,
read-only cached receipt/queue lists и publication checkpoint. Send, ACK,
uncertain, restore, queue baseline/consumption и terminal cleanup инвалидируют
списки при фактическом изменении; unchanged projection их не пересобирает.
Ранее полученный список сохраняет своё состояние после нового ACK/removal;
потребитель не может изменить структуру списка.

persistReceipts использует прежний typed persistence callback ChatSession.
Revision резервируется до вызова порта, поэтому повторный вход не сериализует её
дважды. Исключение передаётся caller и оставляет save retryable; завершение старого
вызова не сбрасывает checkpoint более новой revision, сохранённой reentrantly.
ChatSession сохраняет session/ACK scoping, canonical rendering, queue facade,
detached-ledger reconciliation и injected callback. PendingMessages/codec сохраняют
прежний host/session text-only формат, local image indices и queued baseline.

PendingMessages уже сравнивал готовый JSON перед записью в preferences. Изменение
убирает работу до этого сравнения: сборку receipt/queue lists и кодирование
неизменных подтверждений. Это проверка количества обходов/вызовов и выделений,
а не замер FPS, elapsed CPU или числа физических disk writes.

Sending echo может скрыть local bubble, сохраняя request для позднего ACK;
terminal canonical cleanup удаляет receipt и сохраняет пустую проекцию. Explicit
retry/restore, исходные file/image bytes и source indices остаются прежними.
Restored SENDING становится UNCERTAIN/Unknown; restored accepted queue показывает
Last known. Stop/start ничего не переотправляет. Read-only, composer/caret,
attachments, стабильные reader keys, catch-up/reduced motion и UI-геометрия
сохранены.

## Проверка финального кода

- `npm run quality`: 103 Node PASS. Owners/import/sourceContract guards и все
  10 pinned baseline SHA256 PASS. QueueProjection зарегистрирована как
  pure-projection; negative checks запрещают Android/Compose/transport/storage/
  media и зависимость от profiled state policy.
- Полный JVM gate: 530 PASS, 0 failures/errors/skips. Новые 21 проверки:
  QueueProjection8 + ReceiptPublication9 + ChatSession integration4. Покрыты
  duplicate/old/assistant/new USER echoes, ordered consumption, bounded suffix,
  неизменность входов, reference reuse, send/ACK/unknown/cleanup/retry/restore,
  frozen lists, reentry/newer revision/failed save и reconnect без replay.
  Counted canonical list подтверждает 1500 reads для suffix из6000 entries;
  40 queued receipts собирают USER candidates за один проход по6001 entries.
  200 unchanged streaming frames не вызывают повторной публикации.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint 0 errors/149 прежних warnings (+3 information), JDK17/API35.
  Production: 123 Kotlin/0 Java.
- На финальном production APK: 47 focused native PASS. В каждом из трёх режимов
  — 3 OutboxStateUiTest +6 ChatOutboxUiTest +5 ChatArrivalUiTest; ещё5 regression:
  3 ViewportUiTest +2 RuntimeStabilityTest для owned WS results/history prefix.
  Все fixtures offline с synthetic transport/data; новый callback использует
  реальный private PendingMessages/codec с отдельным fixture host/session key.
- Режимы: 1080×2400/density420/font1/light;
  945×2100/density420/font1.3/dark; 945×2100/density420/font2/light.
  На предыдущем APK 40 unchanged streaming/snapshot frames увеличивали число
  codec/store callbacks с4 до44; тот же optimization fixture ожидаемо падает
  именно на этом счётчике. На финальном —2→2 во всех режимах, reader anchor точно
  answer/−480px, один synthetic prompt write и сохранённый Next draft.
- ACK двух identical queued prompts оставляет обе записи. Один new canonical
  echo оставляет request-2; repeated snapshots сохраняют callback count6→6 и
  ровно2 writes. Restored Unknown/Last known проходят actual stop/start:
  callback count1→1, writes0 и retained draft. Native outbox checks сохраняют
  исходные file/image bytes, read-only guards, явный restore/retry и caret.
- При настройке fixture исправлено первоначальное ожидание Last known для
  UNCERTAIN: проверяются отдельно Unknown и accepted Last known. Первый dark
  group имел нестабильный второй Restore tap; изолированный сценарий прошёл.
  Финальный fixture получает свежие UI bounds и ждёт стабильную геометрию на
  Choreographer frames перед одним click. В dark/large второй target находится
  на126px ниже первого после удаления attachment chip. Финальные14 случаев
  повторены и прошли во всех трёх режимах. Production APK при этих уточнениях
  не менялся; final test APK и lint пересобраны успешно.
- Reader/stop-start/history-prepend/catch-up/reduced-motion checks PASS.
  Catch-up дал16 разных аппаратных кадров в каждом режиме и36/36/37 положений
  viewport до measured bottom. Viewport regression сохранил answer/−620px на
  30 реальных кадрах после delayed cache, initial saved answer/−480px и close.
- Просмотрены18 итоговых PNG: unchanged stream queue, две queued копии,
  оставшаяся после echo запись, restored states, restored file/image draft и
  chat reader в каждом режиме. Matched normal stream имеет0 изменённых пикселей
  нижеy100;351 отличающийся пиксель только в системных часах,
  bbox (109,54,129,82). Before получен тем же окончательным stream fixture на
  предыдущем APK; frozen baseline не менялся.

## Артефакты и границы

Финальный локальный debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`. SHA256:
`4b3e3cd762bef38e7e3c985caaba4b19444a9271ded6b7ffc478f265b02f5bef`.
Package `ru.billyhargrove.pimobile`, versionName0.6.007/versionCode14.
Прежний SHA256 signing certificate проверен apksigner:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Установка поверх предыдущего APK без clear/uninstall сохранила SHA256 всех
4 preferences; содержимое и токены не выводились. От начала слайса изменены
только два прежних production-файла: ChatOutbox и ChatSession; добавлена
QueueProjection. Все предыдущие локальные updater/voice/transcription/attachments/
documents/history/controls/configuration/viewport slices сохранены.
server/Caddyfile сохранил SHA256.

Доказательства и checklist: `/Users/billy/temp/pi-mobile-outbox-projection-20261008/`.
Перед завершением app/test force-stopped; normal display/font/theme/motion и
исходные airplane0/Wi-Fi1/mobile-data0 восстановлены с read-back.
Созданный эмулятор остановлен, его процесс завершился с кодом0; `adb devices`
после остановки пуст. Новые outbox/viewport PNG удалены с эмулятора после сохранения
доказательств в task folder. Crash reporting для собственного emulator run выключен.
Физический телефон и live Pi queue/ACK не проверялись. Pi/Orca/gateway не менялись,
реальные prompts/речь не запускались. Изменения локальные; bump, commit, push
и публикация не выполнялись.
