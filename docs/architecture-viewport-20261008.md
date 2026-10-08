# Локальная декомпозиция позиции читателя — 8 октября 2026

Выделен владелец позиции читателя, follow-tail и показа новых событий.
Исправлен воспроизведённый скачок: поздний кешированный snapshot мог вернуть
старый saved anchor после ручной прокрутки и снова включить следование за хвостом.
Версия 0.6.007/code14, пакет, сертификат и зависимости сохранены.

## Владельцы и поведение

Internal `features/chat/ViewportSession` владеет first-render/catch-up phases,
follow-tail, tail/arrival revisions, arriving keys, new-activity disclosure,
однократным cached restore и локальным close. Typed `Position` содержит stable
key, signed pixel offset и follow; порты — load/save/restore. В классе нет
Android, JSON, network, storage, Compose foundation, измерения или скролла.
Compose runtime используется только для наблюдаемого состояния. Source contract
`platform-free-state` и negative tests фиксируют эти границы.

ChatSession сохраняет public Viewport/getters/methods, scoped snapshot/messages
JSON, canonical store/presentation/outbox, transport/cache adaptation и прежний
wire/storage формат anchor/offset/follow. Android/Compose измеряют реальные
пиксели и выполняют restore/animation/scroll. Stable keys, progress-prefix
fallback, measured tail и UI-геометрия остаются прежними.

Ручной drag запрещает позднему cached restore подменять текущее место чтения.
Первоначальный saved anchor по-прежнему восстанавливается один раз; subsequent
cached snapshots не повторяют restore. Load резервируется до вызова порта;
reentrant load, drag или close во время чтения не восстанавливают устаревшую
позицию. Signed offset сохраняется без преобразования в cache adapter.

Первая отрисовка остаётся немедленной. Последующие tail changes при follow-tail
используют существующую плавную анимацию. Если читатель выше, новые сообщения
показывают New activity без движения текста. Catch-up подсвечивает новые и
изменившиеся строки; обычный streaming сохраняет прежнюю new-only политику.
История и удаления не создают ложные arrivals. Только завершение текущей arrival
revision снимает подсветку; старое завершение не скрывает более новый batch.

ChatActivity закрывает viewport owner на onDestroy. Close идемпотентен;
поздние viewport callbacks не меняют revisions/follow/arrival state, не сохраняют
новую позицию и не восстанавливают старую. Canonical conversation остаётся у
своего владельца. Close не посылает remote команды и не удаляет сохранённые данные.
ChatScreen привязывает restore и arrival-completion effects к identity ChatSession.
onStop сохраняет позицию по прежнему lifecycle; возврат в приложение сохраняет
место чтения и существующие catch-up/reduced-motion правила.

Поиск arriving keys теперь выполняется одним проходом непосредственно в ordered
set; убран промежуточный filtered list. Сравнение message/tools/expanded и map
предыдущих stable keys сохранены. Это уменьшение лишних выделений памяти,
а не замер общей FPS или скорости сети.

## Проверка финального кода

- `npm run quality`: 102 Node PASS; owners/import/sourceContract guards и все
  10 pinned baseline SHA256 PASS. Новый negative contract запрещает platform,
  transport, storage и Compose scrolling зависимости в ViewportSession.
- Полный JVM gate: 509 PASS, 0 failures/errors/skips. Новые 16 проверок:
  ViewportSession12 + ChatSession integration4. Покрыты initial/smooth tail,
  signed save, restore once, manual-reader/reentrant/close fences, catch-up,
  changed tools/expanded, stale completion, removal/history и canonical/draft
  invariants после закрытия владельца.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` PASS.
  Lint 0 errors/149 прежних warnings (+3 information), JDK17/API35.
  Production: 122 Kotlin/0 Java.
- На финальном production APK: 47 focused native PASS. В каждом из трёх режимов
  — 3 ViewportUiTest +5 ChatArrivalUiTest +5 HistoryUiTest; ещё 8 regression:
  6 ChatOutboxUiTest +2 RuntimeStabilityTest для owned WS results/history prefix.
  Все fixtures offline с synthetic transport/data.
- Режимы: 1080×2400/density420/font1/light;
  945×2100/density420/font1.3/dark; 945×2100/density420/font2/light.
  Initial saved reader восстанавливается в answer/−480px с одним cache read;
  subsequent manual answer/−620px сохраняется. Поздний cache после drag имеет
  0 cache reads и ровно answer/−620px на всех 30 измеренных Choreographer frames
  в каждом режиме. Actual Activity destroy отсекает late viewport callbacks.
- На предыдущем APK тот же окончательный fixture ожидаемо воспроизводит баг:
  reader answer/−620px переходит через u/−56px к answer/−9811px; test завершается
  именно pixel-anchor failure. Первый вариант fixture проверял состояние слишком
  рано, поэтому финальный проверяет реальные позиции на 30 кадрах. Production APK
  при уточнении fixture не менялся; test APK и lint пересобраны успешно.
- Reader/history prepend/stop-start/reduced motion checks PASS во всех режимах;
  history anchor точно answer/−480px. Catch-up дал 16 разных аппаратных кадров
  в каждом режиме и 36/36/37 промежуточных положений viewport до measured bottom.
  Outbox regression сохранил draft, attachments, receipt ownership и read-only.
- Просмотрены 15 PNG: delayed cache, initial restore, chat reader, catch-up tail
  и history reader в трёх режимах. Matched normal chat reader имеет 0 изменённых
  пикселей ниже y100; 363 отличающихся пикселя только в системных часах,
  bbox (131,54,150,82). Before получен тем же существующим arrival fixture на
  предыдущем APK: отдельная baseline проверка 1 PASS. Frozen baseline не менялся.

## Артефакты и границы

Финальный локальный debug APK:
`android/app/build/outputs/apk/debug/app-debug.apk`. SHA256:
`d88423770951c6ae66bd4962f74416cc35973de786201d58225943b908b632b8`.
Package `ru.billyhargrove.pimobile`, versionName0.6.007/versionCode14.
Прежний SHA256 signing certificate проверен apksigner:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Установка поверх предыдущего APK без clear/uninstall сохранила SHA256 всех
4 preferences; содержимое и токены не выводились. От начала слайса изменены
только три прежних production-файла: ChatActivity, ChatSession и ChatScreen;
добавлен ViewportSession. Предыдущие локальные updater/voice/transcription/
attachments/documents/history/controls/configuration slices сохранены.
server/Caddyfile сохранил SHA256.

Доказательства и checklist: `/Users/billy/temp/pi-mobile-viewport-20261008/`.
Перед завершением app/test force-stopped; normal display/font/theme/motion и
исходные airplane0/Wi-Fi1/mobile-data0 восстановлены с read-back.
Созданный эмулятор остановлен, его процесс завершился с кодом0; `adb devices`
после остановки пуст. Новые viewport PNG удалены с эмулятора после сохранения
доказательств в task folder.
Физический телефон и live Pi frames не проверялись. Pi/Orca/gateway не менялись,
реальные prompts/речь не запускались. Изменения локальные; bump, commit, push
и публикация не выполнялись.
