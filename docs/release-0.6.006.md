# Pi Mobile 0.6.006 — compact UI, Queue & workflows

7 октября 2026. Billy прямо разрешил GitHub-релиз и затем отправку APK в «Парилка228». Этот выпуск содержит все согласованные UI-правки; live Pi/Orca/gateway не перезапускались, `/reload` и платных промптов не было. Изменение `server/Caddyfile` принадлежит другой работе и исключено из релизного коммита.

## Изменения

- Переписка на полном экране под плавающими шапкой и composer, с градиентами в обеих темах. Ответы ассистента без карточек; инструменты без бордеров/прямоугольной подсветки нажатия. Composer без нижней тени.
- `read` только под последним сообщением пользователя; новое неподтверждённое сообщение скрывает прежнюю подпись. Это принятие Pi, не завершение задачи. Баннер Older messages убран, история и догрузка сохранены.
- У группы инструментов — роллер имени последнего реального тула и счётчик. Без иконки, Tools, Running и общего Error; отдельные ошибки остаются видимыми в журнале.
- Каталог — компактные строки без карточек/чат-иконок/provider subtitle/idle-статуса. У настоящей работающей сессии остаётся спиннер. Workspace отличается типографикой и отступом от названий чатов.
- Шапка: хост, рядом слева иконка подключения или спиннер. Справа одинаковые refresh/settings цели48dp с glyph-box22dp и интервалом4dp. Статус связан с хостом интервалом8dp; черновик URL не подменяет подключённый адрес, credentials/path/query не отображаются.
- Usage — три компактные информационные строки и единая прокручиваемая панель со всеми квотами, честными cached/unavailable состояниями. Официальный SVG OpenAI преобразован в native vector без изменения пути/пропорций; происхождение и ограничения марки документированы.
- Settings открывается горизонтальным слайдом; возврат сохраняет прокрутку каталога и поля. Quick effort track33dp (1.5×), без Waiting/pending строки; ошибка над controls. ACK-ограничения и ручное закрытие панелей сохранены.
- Только активные workflow и самостоятельные агенты в отдельных dock. Workflow раскрывается в этапы → агенты с фактическими tools/tokens/time; инспекция остаётся read-only.
- Queue над composer для Follow Up, отправленных этим приложением во время работы Pi. ACK означает принятие; только новое каноническое user echo потребляет элемент. FIFO/дубликаты/восстановление Last known проверены; автоповтора, глобальной очереди и выдуманных cancel/reorder API нет.
- Wire-команды, package, сертификат, зависимости и приватные форматы совместимы. Токены не включены в APK. Для этого UI-релиза не требуется перезапуск gateway или `/reload` Pi.

## Итоговые проверки именно релизной сборки

| Gate | Результат |
|---|---|
| `npm run quality` | 82 Node PASS; owner/import gates и10 immutable baseline hashes PASS |
| JDK17 `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug` | BUILD SUCCESSFUL;185 JVM PASS,0 failures/errors; lint0 errors /150 warnings /3 information |
| Полный API35 ARM64 instrumentation | `OK (82 tests)`:81 synthetic PASS,1 existing live opt-in skipped |
| Узкий360dp / light2× | 6 focused catalog/header/Usage/settings/navigation PASS на0.6.006 |
| Узкий360dp / dark1.3× | Те же6 PASS на0.6.006 |
| Широкие UI матрицы до version bump | По34 PASS light2× / dark1.3×; последний header placement дополнительно прошёл по6 в трёх конфигурациях. Это предыдущий preview, не дополнительный полный release-run. |
| Визуальная проверка | Синтетические light/dark/large-type снимки и промежуточные позиции Settings slide просмотрены; baseline не заменялся |
| Upgrade опубликованной0.6.005→0.6.006 | `adb install -r`, без удаления/очистки: SHA256 всех9 существующих shared_prefs/no_backup файлов неизменны; versionCode12→13 |
| Подпись | apksigner: новый APK и скачанный опубликованный0.6.005 имеют точно одинаковый сертификат |

Первый release UI-run упал в test-only чтении устаревшего Compose accessibility-node. Исправлена только предоперационная фиксация anchor: bounded refresh/retry, без повторения действий и без ослабления допуска2px при history/live updates. Точечный тест и повторный полный82-case run прошли. Ожидание ACK не заменено временем и команды реальным агентам не отправлялись.

## Артефакт

| Поле | Значение |
|---|---|
| APK | `Pi-Mobile-0.6.006.apk` |
| Package / versionCode | `ru.billyhargrove.pimobile` /13 |
| Android | min26, target35 |
| Размер | 15473314 bytes |
| APK SHA256 | `b4811d49e6f5ba9cd2538504a477f6f30bed69360ef6e204918ccec7389f3cb5` |
| Certificate SHA256 | `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4` |

Персональная debug-сборка с прежней подписью, не Play Store. Поставляется `SHA256SUMS.txt`; устанавливать поверх0.6.005 без удаления приложения. Стабильный latest GitHub-релиз `v0.6.006` опубликован из commit `3c9eeff`: https://github.com/billyhargroveofficial/pi-mobile/releases/tag/v0.6.006. API digest/size, `SHA256SUMS.txt` и повторно скачанный публичный APK совпали с проверенной сборкой побайтно. APK отправлен в подтверждённый «Парилка228 Zoo Prison»; собственное сообщение и имя документа прочитаны обратно, файл повторно скачан из Telegram — размер15473314 и SHA256 точно совпали. Первый вызов send_file отклонил путь вне allowed roots и ничего не отправил; успешная отправка выполнена из штатного Telegram outbox. Дубликата доставки нет.

## Ограничения и воспроизведение

Физический телефон, его installer/Keystore/IME/галерея/микрофон, реальная потеря сети и новый STT-вызов этим прогоном не проверены. Queue не перечисляет отправления с других устройств/терминала. Для живой Orchestration нужен ранее документированный совместимый read-only observer; saved records не подменяют live activity. Релиз не объявляет закрытой физическую приёмку всей миграции.

Сборка: JDK17/SDK35; `npm run quality`, затем Android Gradle gate выше. Финальные Gradle команды использовали временно `-Dorg.gradle.jvmargs='-Xmx1g -XX:MaxMetaspaceSize=768m -XX:+UseG1GC -Dfile.encoding=UTF-8'`, без изменения памяти/зависимостей проекта. Проверять instrumentation `OK`, не только ADB exit code.

Локальные логи/артефакт: `/Users/billy/temp/pi-mobile-release-0.6.006/`; private-file hash evidence остаётся локально и не публикуется. UI/source rationale и предыдущие captures: [ui-redesign-20261007.md](ui-redesign-20261007.md), [ui-typography-20261007.md](ui-typography-20261007.md), [OpenAI asset provenance](ui-assets/openai/README.md). Эмулятор оставлен видимым/работающим по просьбе Billy, восстановлены light1×/1080×2400/density420.
