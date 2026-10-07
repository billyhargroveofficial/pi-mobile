# Pi Mobile 0.6.007 — stable scrolling, smooth catch-up & architecture

7 октября 2026. Billy прямо запросил push и GitHub-релиз. Релиз объединяет
последовательные локальные архитектурные слайсы после0.6.006. Изменение
`server/Caddyfile` принадлежит другой работе и исключено из релизного коммита.

## Изменения

- При чтении выше конца переписки новые ответы, tools, streaming и догрузка истории
  сохраняют стабильный key и pixel offset видимой строки.
- После возвращения новые и изменённые события проявляются без изменения геометрии
  строк. Follow-tail плавно достигает измеренного конца; у читающего историю
  появляется New activity. Отключение системных анимаций соблюдается.
- Same-epoch reconnect с доказанным overlap сохраняет ранее подгруженный prefix.
  Новая epoch/отсутствие overlap не выдаются за непрерывную историю.
- Chat header/composer/workflow dock, Usage/history и model/effort panels разделены
  на platform hosts, владельцев состояния, pure projections и Compose screens.
  Архитектурные проверки закрепляют владельцев и направление зависимостей.
- Static agent counters, quota/model capabilities и повторяющийся Markdown
  вычисляются без повторной работы на тиках/перерисовках. Изображения и inflight
  запросы разделены по credentials; закрытый renderer не меняет чужой binding.
- ACK/data требуют принадлежащий запрос и session. Возврат на экран согласует
  завершившиеся во время остановки запросы без автоматического replay.
- Effort корректно откатывается к подтверждённому значению; новые отчёты поля
  выигрывают у старого ACK. Ошибка закрытой панели показывается в чате.
- Mutation receipt cache разделяется gateway и extension, ограничен1000; крупные
  history/document/MCP read replies не сохраняются. Malformed metadata фильтруются.

Подробности: [чат и arrivals](review-architecture-20261007.md),
[Usage/history](architecture-catalog-20261007.md),
[model/effort](architecture-configuration-20261007.md),
[read-only orchestration](architecture-work-20261007.md).

## Проверки именно0.6.007

- Node quality:93 PASS; owner/import contracts и10 immutable PNG hashes PASS.
- JVM:284 PASS,0 failures/errors/skipped; debug/androidTest build PASS.
- Lint:0 errors/149 warnings, против150 предупреждений до configuration slice.
- Подпись совпадает с APK, скачанным из опубликованного0.6.006.
- Upgrade опубликованной0.6.006→0.6.007 через `adb install -r`, без удаления/clear:
  SHA256 всех9 существующих shared_prefs/no_backup файлов неизменны; code13→14.
- Полная API35 ARM64 instrumentation:99 synthetic PASS,1 existing live opt-in
  skipped (`OK (100 tests)`),321s, именно на0.6.007.
- Финальная display matrix:29/29 dark360dp/font1.3 и29/29 light360dp/font2,
  всего58 focused PASS на том же0.6.007 APK; normal покрыт полным прогоном.
  Проверены anchors/arrivals/reduced-motion, panels/gestures, history/search,
  dock counters/workflow, Settings/navigation и keyboard. Model/catalog/effort
  screenshots просмотрены в normal/dark/large; baseline не заменялся.
- Actual hardware catch-up:16 разных PixelCopy кадров в каждом из трёх режимов;
  smooth tail дал36/36/37 разных viewport positions (normal/dark/large).
  Native capability counter: registry1/capability arrays2 после edits/Apply/ACK/search.
  Это frame/read-count evidence, не FPS/battery/network benchmark.

Для публикации сохранён именно зафиксированный APK, прошедший полный UI прогон
и обе display matrix. После проверки удалены лишние EOF blank lines в трёх новых
Kotlin-файлах. Cosmetic rebuild успешен; ZIP diff затронул только classes4/9.dex.
Resolved инструкции и структура classes/fields/methods этих DEX совпадают после
исключения debugger line/locals metadata, file offsets и source-file indices.
Digest публикуемого проверенного APK не менялся; новая cosmetic сборка не подменяет
его. Логи сравнения остаются в локальной папке релиза.

## Артефакт

| Поле | Значение |
|---|---|
| APK | `Pi-Mobile-0.6.007.apk` |
| Package / versionCode | `ru.billyhargrove.pimobile` /14 |
| Android | min26, target35 |
| Размер | 15538909 bytes |
| APK SHA256 | `9a72beca5250008206d7d5c6dbb31cee11dfee20e9f261f5838b9670a2e19f73` |
| Certificate SHA256 | `86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4` |

Персональная debug-сборка с прежней подписью, не Play Store. Устанавливать поверх
0.6.006 без удаления приложения. Рядом поставляется `SHA256SUMS.txt`.

## Границы проверки

Физический телефон, его IME/installer/галерея/микрофон и реальные сетевые сбои
этим выпуском не проверены. Native сценарии используют synthetic fixtures,
не отправляют платные промпты и не меняют настоящие Pi-сессии. Текущие live
Pi/Orca/gateway не обновлялись и не перезапускались; push host-кода не означает
его deployment. Package, сертификат, зависимости и приватные форматы сохранены.
Новая Telegram-доставка не входит в этот запрос.

Сборка: JDK17/SDK35, `npm run quality` и
`android/gradlew -p android testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`.
Временные Gradle JVM flags: Xmx1g/MaxMetaspaceSize768m; настройки проекта не менялись.
Логи, APK, upgrade hashes и screenshots:
`/Users/billy/temp/pi-mobile-release-0.6.007/`; private hash evidence не публикуется.
