# Pi Mobile 0.6.005 — UI хотфикс

7 октября 2026. Выпуск GitHub разрешён пользователем после эмуляторных и визуальных проверок. Отправка в Telegram не требуется. Live Pi/Orca/gateway не перезапускались и не менялись, платные промпты не отправлялись.

## Исправления

- Полностью удалён левый drawer и его компонент/владелец. Чат возвращается в каталог обычной кнопкой назад; History и Settings остаются отдельными прямыми действиями каталога.
- Composer не переключает `singleLine`/IME options при появлении и закрытии клавиатуры. После пользовательского закрытия снимается focus, текст и выделение остаются в owner state. Клавиатура открывается снова по нажатию пользователя, не от layout/stream recomposition. Компактная строка выровнена по фиксированному line height.
- Вместо `✓✓` — серый `read`,10sp, под пузырём. Появляется после принятия/канонического эха, не в состоянии SENDING. Accessibility явно сообщает: принятие Pi, не завершение задачи.
- Effort/tier/model панели не закрываются после выбора, Apply или ACK. Pending блокирует повторную отправку до ответа; после ответа можно изменить настройки ещё раз. Ошибки остаются внутри панели. Выбор модели открывает её sheet, после ручного закрытия возвращается актуальная effort-панель. В обеих панелях есть явное закрытие; outside/back — тоже пользовательское закрытие.
- Нет нижних Toast/Snackbar: notices принадлежат ChatSession и отображаются под шапкой с ручным закрытием; catalog errors — под шапкой, updater status — в строке настроек. Диктовка возвращает только черновик; во время транскрибации кнопка composer показывает спиннер и недоступна для отправки.
- Wire, host runtime, encrypted token/settings/outbox, package и signing certificate не менялись. Текущий production Android:81 Kotlin-файл, Java —0.

## Итоговые проверки

| Gate | Результат |
|---|---|
| `npm run quality` | **82/82 PASS**, ownership/import gates,10 неизменных baseline SHA256 |
| JVM `testDebugUnitTest` | **173/173 PASS**,0 skipped |
| `assembleDebug assembleDebugAndroidTest lintDebug` | BUILD SUCCESSFUL; lint **0 errors**,146 warnings/3 information |
| Полный API35 ARM64 UI suite итогового APK | **64 synthetic PASS**,1 live opt-in skipped; instrumentation сообщает `OK (65 tests)` |
| Узкий360dp / dark / font1.3× | **15/15 PASS**: HotfixUiTest, NavigationUiTest, DesignPreviewTest |
| Узкий360dp / light / font2× | **15/15 PASS**, те же реальные production UI fixtures |
| Визуальная проверка | Снимки каталога/settings, компактного и открытого composer, read-caption, effort/model/tier, spinner и inline error просмотрены в трёх конфигурациях. Эталоны не заменялись. |
| Upgrade/rollback | Итоговый0.6.005→0.6.004→0.6.005 через `adb install -r` / debug downgrade: SHA256 всех5 существующих файлов shared_prefs/no_backup одинаковы; финально установлен versionCode12 |
| APK signature | Проверена apksigner; сертификат точно совпадает с0.6.004 |

Восемь новых HotfixUiTest сценариев используют credential-free injected transport. Проверена именно системная IME стрелка вниз (`android:id/input_method_nav_back`), аппаратный Back и программное скрытие; после закрытия выдерживаются layout/stream recompositions без возврата клавиатуры и потери текста. Ошибка первого тестового нажатия стрелки оказалась неверной координатой: тест исправлен на настоящий системный accessibility-node. Последний полный прогон и обе матрицы зелёные после финальной UI-правки.

Релизные UI тесты не отправляют команды настоящим Pi. Live relay opt-in не включался. Эмулятор: host GPU,2 CPU,1536MB guest RAM; после проверок исходные display/font/theme восстановлены, эмулятор выключен. Gradle ограничен2 workers/1GiB; Node concurrency2.

## Проверенный артефакт

| Поле | Значение |
|---|---|
| APK | `Pi-Mobile-0.6.005.apk` |
| Package / versionCode | `ru.billyhargrove.pimobile` /12 |
| Android | min26, target35 |
| Размер | 15513413 bytes |
| APK SHA256 | `e60370694da6036abbbf75a9e992a583a751b1d23c2a85507371b6d83ba17919` |
| Signing certificate SHA256 | `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4` |

Это персональная debug-сборка с прежней подписью, не Play Store. `SHA256SUMS.txt` поставляется вместе с APK. Установить поверх0.6.004 без удаления приложения. Для UI хотфикса не требуется перезапуск gateway или `/reload` Pi.

## Ограничения

Физический телефон отсутствует: его IME/галерея/микрофон и системный installer через встроенный updater не проверены этим прогоном. Транскрибационный spinner и возврат в composer проверены синтетическим owner state, не новым реальным аудио/STT вызовом. Проверка SHA256 приватных файлов при upgrade не подменяет физическую Keystore/installer приёмку. Реальный network-loss/background recovery и Linux-чат остаются открытыми из прежнего acceptance audit. Этот хотфикс не объявляет всю цель миграции физически принятой.

## Воспроизведение и локальное evidence

```sh
npm run quality
cd android
./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w ru.billyhargrove.pimobile.test/androidx.test.runner.AndroidJUnitRunner
```

Использовать JDK17/SDK35. Проверять instrumentation `OK`, а не только ADB exit code. Для матриц:945×2100 при density420, font_scale1.3/night yes и font_scale2/night no; выбранные классы — HotfixUiTest, NavigationUiTest, DesignPreviewTest. Вернуть параметры эмулятора и остановить его после проверки.

Evidence: `/Users/billy/temp/pi-mobile-hotfix-20261007/`: build-final.txt, quality-final.txt, full-ui-final.txt, matrix-dark-font130-final.txt, matrix-light-font200-final.txt, visual-normal-final/, visual-dark-font130-final/, visual-light-font200-final/; private-file SHA256 comparison logs остаются локально и не публикуются.
