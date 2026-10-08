# Pi Mobile 0.6.008 — changed-only work and bounded feature owners

8 октября 2026. Billy прямо запросил commit, push, GitHub-релиз и оценку оптимизации.
Релиз объединяет 15 последовательных архитектурных слайсов после0.6.007.
Постороннее изменение `server/Caddyfile` исключено. Live Pi/Orca/gateway не изменялись;
новой отправки в Telegram в этом запросе нет.

## Поведение и архитектура

Обновлятор, PCM recording/transcription, attachments, composer, canonical transcript,
outbox, viewport, configuration requests/panels, controls, history, documents,
active work и transcript images имеют ограниченных владельцев состояния, чистые
проекции и presentation-only экраны. Android hosts сохраняют окно, lifecycle и IO.
Публичные фасады, wire commands, приватный формат и зависимости сохранены.

Запрос резервируется перед портом; локальное закрытие отсекает поздние/повторные
результаты. Возврат согласует завершённое ожидание без replay. Подготовка вложений
соблюдает точный остаток byte budget и сохраняет успешный prefix при ошибке.
Отправка очищает только отправленный черновик, сохраняя уже начатый следующий.
Запоздалый cached anchor не заменяет ручную прокрутку; arrivals/catch-up продолжают
использовать реальные геометрию и кадры. Одинаковый URL с новым loader очищает
старые пиксели/ошибку; late decode не меняет новое изображение.

## Измеренная оптимизация последних слайсов

До/после измерено на сохранённых локальных APK соответствующих слайсов
(versionName0.6.007 до release bump). Статистика архитектуры и размер APK ниже
сравнивают опубликованную0.6.007 с итоговой0.6.008.

| Сценарий | До | После | Что измерено |
| --- | --- | --- | --- |
| Quick configuration, registry1000 моделей | 2000 | 2 | Чтения capability arrays: в1000 раз меньше / −99,9% |
| 40 частичных effort reports | 40 | 0 | Чтения registry |
| 40 metadata +40 пустых delta, неизменный ответ | 80 | 0 | Замены identity строки ответа; проекция и tool topology повторно используются |
| 40 frames, неизменные outbox receipts | 40 дополнительных | 0 дополнительных | Вызовы подготовки/JSON-сериализации сохранения, не disk writes |
| Очередной elapsed tick active work | +4 phase/+4 clock reads | +0/+0 | JSON reads на native UI; static counters повторно не читаются |

Сценарии и исходные before/after evidence: [panels](architecture-panels-20261008.md),
[transcript](architecture-transcript-20261008.md), [outbox](architecture-outbox-20261008.md),
[active work](architecture-active-work-20261008.md). Совпадение исходных данных по-прежнему
требует сравнения; устранение повторной проекции не делает каждое обновление O(1).
В outbox старый store уже сравнивал JSON перед записью: этот результат не доказывает
сокращение физических записей на диск. PCM meter держит максимум один pending callback
с последним RMS; это ограничение очереди работы, не замер CPU/FPS.

Декомпозиция: production Kotlin101→130 файлов, Java0. Координатор ChatSession365→290
строк (−20,5%); фасады AppUpdates181→43 (−76,2%), DictationRecorder97→46 (−52,6%),
OrchestrationEntry176→48 (−72,7%). Код перемещён к владельцам, это не сокращение
общего объёма программы. ChatActivity259→266 строк.

APK15 538 909→15 620 829 bytes: +81 920 / +0,5272%. Приложение не стало меньше;
заявлять общий процент ускорения, FPS, startup latency, CPU, heap или battery
на основании этих счётчиков нельзя. Эти показатели не измерялись.

## Проверки финального0.6.008

- Node quality108 PASS; owner/import contracts и10 immutable baseline PNG hashes PASS.
- JVM608 PASS,0 failures/errors/skipped; debug и androidTest build PASS.
- Lint0 errors/149 existing warnings/3 info.
- In-place upgrade опубликованной0.6.007→0.6.008 без clear/uninstall:
  все9 существующих shared_prefs/no_backup файлов сохраняют SHA256.
- Полный API35 ARM64 instrumentation:180 synthetic PASS,1 existing live opt-in
  assumption skipped (`RelayConnectionSmokeTest`; runner status−4), `OK (181 tests)`,
  567,461s. Raw stream проверен отдельно:0 failures/errors. Первичный host parser
  ожидал ignored-test code−3; исправлена классификация только этого доказанного skip.
- Финальная display matrix того же зафиксированного APK:39/39 dark360dp/font1.3
  (144,53s),39/39 light360dp/font2 (146,99s), density420. Normal покрыт полным
  прогоном. После уточнения fixture жеста дополнительно5/5 ActiveWork в normal.
  Initial dark run38/39: компактный док прокрутился, tap не вызвал переход.
  Test-only correction ждёт16 стабильных кадров и нажимает заголовок вне nested
  metrics scroller; production sources/APK SHA256 не изменились. Обе final matrix PASS.
- На финальном APK счётчики в таблице повторно подтверждены во всех трёх режимах.
  Catch-up:16/16/15 разных аппаратных кадров из16 captures; smooth tail:
  36/36/37 разных viewport positions (normal/dark/large). Reader answer/−620px
  сохраняется на30 измеренных кадрах delayed cache.
- Просмотрены51 финальных PNG (19 normal,16 dark,16 large): reader/composer,
  quick panel, updater/voice/MCP/documents, attachments/queue и portrait image.
  Baseline PNGs сохранены; это visual/frame/read-count evidence, не FPS benchmark.

## APK

Package `ru.billyhargrove.pimobile`; versionName0.6.008, versionCode15, minSdk26,
targetSdk35. Сертификат совпадает с опубликованным0.6.007:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.

Фиксированный `Pi-Mobile-0.6.008.apk`,15 620 829 bytes:
`d4312d652e188eee4e8a1303e2027d44fa6d033faee5a237711ce76de39c4d57`.
Evidence/logs/artifact: `/Users/billy/temp/pi-mobile-release-0.6.008/`.

Физический телефон, реальные microphone/IME/attachments/network/background recovery,
Linux-session parity и реальные remote-image callbacks остаются приёмочными gaps.
Synthetic API35 ARM64 и byte-preserving upgrade не заменяют эти проверки.
