# Pi Mobile 0.6.004 — проверка релиза

7 октября 2026. Billy явно разрешил минимальный bump, GitHub-релиз и отправку APK в «Парилка228». Live Pi/Orca/gateway при финальных проверках не перезапускались; платные промпты не отправлялись.

## Изменения

Весь производственный Android-код — Kotlin: 82 файла / 5,537 строк; Java — 0. Основной чат/composer, каталог с общей боковой панелью, история, Usage, сгруппированные настройки, Orchestration и панели модели/effort/tier/диктовки/обновления используют Compose. Markwon/LaTeX и zoom остаются ограниченными нативными листьями; единственный экранный XML — кнопка закрытия image viewer. Владельцы состояния/команд — ChatSession/ChatOutbox и CatalogSession; направления импортов и отсутствие Java/Kotlin-дублей проверяются автоматически.

Дизайн следует присланным ChatGPT-референсам и effort-видео: чёрная тёмная тема, синие сообщения, округлый composer, общий drawer и grouped settings. Боковая панель сохраняет черновик/курсор. Effort использует фактические уровни провайдера, пружину/пиксельный шлейф и поддерживает pointer/cancel/disabled, keyboard/range и reduced motion. Исправлены лишняя пустота селектора модели, красные углы до свайпа и переносы кнопок при шрифте200%.

## Локальные результаты

- `npm run quality`: 78 Node cases, ownership/import/unique-source gates и SHA256 всех10 неизменных эталонов. Полный gate выполнен после переноса; после точечных Android-правок повторены только architecture/hash/diff.
- JVM: интегрированные170 cases прошли; после добавления отдельного catalog-regression case прошли19 focused CatalogSession/ChatSession (7+12). Это171 разных случая покрытия, а не повторный общий прогон171.
- `assembleDebug assembleDebugAndroidTest`: успешно. `lintDebug`: **0 errors**, 145 warnings и3 information (не заявляется отсутствие предупреждений). Lint теперь прекращает сборку при ошибках. Явная microphone-permission guard и API27-qualified navigation-bar themes исправляют найденные3 errors; затронутые voice/dark-header/light-settings cases прошли отдельно.
- API35 ARM64: первый интегрированный прогон56 synthetic cases дал50 PASS /6 failures; все6 прошли после исправления ожиданий фактической Compose-геометрии/scroll. Обе drawer/caret проверки входят в покрытие. После production UI-полировки затронутые3 случая прошли отдельно. Повторного полного прогона ради общей зелёной строки не было.
- 360dp: dark/font1.3× —10 cases PASS; light/font2× —9 PASS и Usage впоследствии PASS отдельно после исправления тестового жеста у системного края. Usage/settings затем повторены точечно на1.3× и2×; реальная горизонтальная навигация Usage доступна. Workflow/reader/tier проходят обе темы/крупный шрифт. Reduced-motion drag PASS вместе с ранее прошедшими cancel/disabled, keyboard и accessibility.
- Итоговые синтетические снимки визуально просмотрены: чат, drawer, Usage, settings, compact tier sheet, brain slider, voice и workflow/reader. Существующие baseline PNG/SHA256 не заменялись.
- **Финальный APK**: реальный Android HTTPS REST/WSS relay — `OK (1 test)`, авторизованный каталог и Connected. Только чтение, временный cache input удалён, токен остаётся зашифрованным в Keystore.
- Установка без удаления: **0.6.004 → 0.6.003 → 0.6.004** на API35. SHA256 приватных settings/outbox и 1 cache-файла одинаковы до/после; обе версии подключились с сохранённым Keystore-токеном. Финально установлен versionCode11.
- Эмулятор: hardware Metal GPU,2 cores/1.5GiB guest RAM; общая host memory больше guest RAM. После проверок выключен, ADB устройств не показывает. Gradle:2 workers, heap1GiB; Node:2 concurrent test processes.

## Проверенный артефакт

| Поле | Значение |
|---|---|
| APK | `Pi-Mobile-0.6.004.apk` |
| Package / versionCode | `ru.billyhargrove.pimobile` /11 |
| Android | min26, target35 |
| Размер | 17135448 bytes (<32MiB updater limit) |
| APK SHA256 | `b6967e899583aa065c71564eee41d96956226d36f1f0d7af362ec682e557617e` |
| Signing certificate SHA256 | `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4` |

Подпись точно совпадает с0.6.003; APK не содержит приватного device-token. Это персональная debug-сборка с прежней подписью, не Play Store. Updater выбирает стабильный latest GitHub-релиз и сверяет digest/package/versionCode/certificate; у APK точное ожидаемое имя. Публикация должна содержать этот APK и `SHA256SUMS.txt`; после загрузки сверить GitHub asset digest и повторно скачанный файл.

## Остаётся физическая приёмка

Телефон по USB отсутствует. Физическая установка, подтверждение Android installer из встроенного updater, сотовое подключение, IME/галерея и микрофон конкретного телефона этим прогоном не проверены. Linux runtime не развёртывался/не менялся в финальном релизном прогоне. Рабочие Pi не прерывались для проверки close/delete/send. Полная цель миграции не объявляется завершённой до физической приёмки.

## Evidence

Локальные логи и синтетические PNG: `/Users/billy/temp/pi-mobile-chat-compose-20261006/` — integrated-node-quality.txt, integrated-kotlin-build.txt, navigation-build.txt, integrated-compose-api35.txt, ui-failures-fixed.txt, visual-polish-ui.txt, matrix-360-font130-dark.txt, matrix-360-font200-light.txt, font130-usage-dark.txt, font200-usage-light.txt, font200-settings-final.txt, effort-reduced-drag-final.txt, lint-fixes-build-004.txt, lint-affected-voice-header-004.txt, lint-affected-light-settings-004.txt, relay-final-004.txt, upgrade-rollback-004.json, final-004-visuals/ и release-004/. Это evidence выполнения, не committed secrets/runtime.
