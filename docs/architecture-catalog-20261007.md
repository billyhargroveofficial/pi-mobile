# Catalog: Usage и история — 7 октября 2026

По просьбе Billy продолжить оптимизацию и декомпозицию разделены два оставшихся
смешанных компонента каталога. Версия сохраняется0.6.006/versionCode13.

## Владельцы и направление зависимостей

`ui/UsageCards` уменьшен со169 до36 строк, `ui/ArchiveSheet` — со159 до47.
Это platform adapters: Android host/context, HTTP executor, main-thread callbacks
и отменяемые Handler timers. Их существующие конструкторы, методы и callers
сохранены. Остальной код находится внутри владельца catalog:

| Участок | State owner | Pure projection | Compose presentation |
| --- | --- | --- | --- |
| Usage | UsageSession | UsageProjection | UsageScreen |
| История | ArchiveSession | ArchiveProjection | ArchiveScreen |

State owners принимают injected transport/timer/clock ports. Android и HTTP в них
не импортируются; из Compose разрешён только runtime state. Проекции копируют
JSON в неизменяемые значения и не зависят от Compose. Экраны не знают токенов,
HTTP, Android Context или Handler. `owners.json` регистрирует все шесть internal
источников; source contracts запрещают transport/platform imports и обратное
направление state → screen и projection → state. Добавлена негативная Node
проверка этих границ. Основной CatalogSession сохраняет подтверждённые команды;
каких-либо дополнительных Gradle-модулей, DI или параллельных реализаций нет.

## Оптимизация и исправления

Usage готовит окна квот, primary limit, названия, абсолютные/относительные времена
и freshness при принятии ответа. Открытие/закрытие unified details больше не
сканирует mutable JSON. Unknown usage остаётся отличимым от reported0%; все
конечные неотрицательные квоты сохраняются, полоса остаётся ограничена0–100%.
Native counter на каждом из трёх display profiles: providers=1, windows=1 после
двух открытий/закрытий реального Compose sheet. Это проверка числа чтений схемы,
не измерение FPS/энергопотребления или HTTP-задержки.

Polling остаётся single-flight/60s. Identity каждой операции и каждого timer
предотвращает публикацию старого ответа после смены endpoint/token/stop/start,
повторное завершение и запуск отменённого poll после manual refresh. Неудачный
ответ показывает unavailable и сохраняет план следующей попытки.

Archive переносит250ms debounce, input/query, загрузку, ошибку/retry, cursor и
подтверждение удаления из renderer в owner. Каждому callback назначен ticket;
старый ответ не может снять busy новой загрузки или добавить чужую страницу.
Неверный, дробный, отрицательный, переполненный или не продвигающийся cursor
не открывает дальнейшую пагинацию. Ошибка страницы сохраняет загруженные строки;
explicit Retry использует прежний cursor/query.

Ключ date header теперь включает первую session в этом последовательном run.
Ранее раздельные группы с одной датой, включая malformed-date «Earlier», получали
одинаковый `date:$day` key. Новые ключи уникальны и сохраняются при append; порядок
данных сервера и dedup по session ID сохраняются. Даты/подписи готовятся при
принятии страницы вместо вычисления внутри LazyColumn.

Удаление по-прежнему требует явного permanent-delete confirmation. Ожидающий
результата ID нельзя отправить повторно через ещё один swipe/confirmation;
unknown result никогда не повторяется автоматически. Success обновляет текущий
query и инвалидирует текущую страницу; duplicate callback игнорируется. Failure
из старого query не переписывает ошибку нового. Dismiss инвалидирует все callbacks
и отменяет debounce. Native destructive test использует только synthetic callback.

## Проверки и пределы

- `npm run quality`:92/92 Node PASS, owner/import/source contracts PASS,
  SHA256 всех10 pinned baseline PNG совпадают.
- JVM:255/255 PASS, из них30 новых проверок двух owners и двух projections.
- Debug и androidTest APK: BUILD SUCCESSFUL; lint:0 errors/150 warnings
  (количество прежнее). Подпись, зависимости и version не изменены.
- Native API35:8/8 в normal1080×2400/light1.0,360dp/dark1.3 и360dp/light2.0,
  итого24 PASS. Включены production catalog/history/usage hierarchy, placeholders,
  одинаковая loaded/skeleton geometry, confirmation, date-group pagination и
  счётчик JSON reads после recomposition.
- Четыре пары before/after normal screenshots визуально проверены. Статичные
  pixels совпадают: исключены системные status/navigation bars, вращающийся
  running indicator и динамические fixture timestamps. Это не утверждение
  полного побитового совпадения исходных PNG.
- `git diff --check` PASS. Предыдущие изменения и пользовательский
  `server/Caddyfile` сохранены; его SHA256 совпадает с началом этого слайса.

Воспроизводимые сценарии: JVM UsageSessionTest/UsageProjectionTest,
ArchiveSessionTest/ArchiveProjectionTest, native CatalogDataUiTest и существующие
ExpressiveUiTest/DesignPreviewTest/RedesignUiTest. Локальные логи, матрица и
сравнения: `/Users/billy/temp/pi-mobile-catalog-20261007/`.

Этот gate проверяет bounded catalog slice. Полный native suite и live relay ранее
проверены в предыдущих слайсах; здесь повторены затронутые сценарии. Физический
телефон, настоящие квоты аккаунтов и настоящее удаление истории этим прогоном
не проверялись. Рабочие Pi/Orca/gateway не перезапускались, платных prompts,
публикации и отправки APK не было. Собственный headless emulator остановлен
после проверки с возвращением исходных display settings.
