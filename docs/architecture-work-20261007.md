# Архитектура для следующих фич — 7 октября 2026

## Рамки

Billy попросил декомпозицию и оптимизацию вместо работы над иконкой. Это последовательные вертикальные изменения существующего продукта, не новый агент, новая UI-иерархия или повторный big-bang перенос. Опубликованный baseline — **0.6.006/versionCode13**. Этот слайс не меняет версию, wire-протокол, сертификат, зависимости или приватные форматы и не публикует APK. Live Pi/Orca/gateway не затрагиваются; чужое изменение `server/Caddyfile` не относится к этой работе.

## Сделано: read-only orchestration

```text
OrchestrationActivity (Android lifecycle, Intent navigation, credentials/IO adapter)
    ├── OrchestrationSession (read-only state + injected Transport)
    │       ├── OrchestrationProjection (snapshot → branches/phases/static metrics)
    │       └── core/TranscriptPresentation (existing shared transcript policy)
    └── OrchestrationScreen (Compose + owner actions + navigation callbacks)
            └── ui/PiTranscript (public renderer WITHOUT command actions)
```

- `OrchestrationActivity` сократился с363 до82 строк. Это показатель отделения ответственности, **не показатель ускорения**. Shell сохраняет Intent/facade entry points, viewport и системные inset'ы. Новые владельцы находятся в `features/orchestration/`; других Activity/host-компонентов слайс не переписывает.
- `OrchestrationSession` — единственный владелец загрузки, notice/subtitle, списка, transcript/cursor/source, disclosure и follow-tail revision. Transport умеет только проверить доступность, читать, поставить/снять таймер. В нём нет команд, токена, Context или Activity. IO-adapter возвращает результат на main thread.
- Scroll geometry остаётся у UI. Владелец получает только `readerAtTail`, поэтому JVM-тесты проверяют paging/follow-tail без Android Views. UI и TranscriptPresentation не получают дублирующую политику истории или раскрытия.
- Stop/restart generation, request identity и single-flight защищают от старых/повторных completion и отменённых poll callbacks. Повторный `start()` не размножает polling. Ошибка сохраняет последний снимок и честно показывает unavailable; отсутствие credentials не запускает запросы.
- `OrchestrationProjection` сохраняет порядок фаз/агентов, Other agents, транзитивных потомков, завершение циклических parent-ссылок, saved/truncation notices и прежний clone-error fallback. Descendant projection не изменяет исходный снимок.
- Новые классы `internal`, не новые cross-feature API. Точки входа остаются `OrchestrationActivity`/`ui/OrchestrationEntry`; другой feature не может импортировать реализацию инспекции.

### Машинные границы

`architecture/owners.json` регистрирует три новых файла и их `sourceContracts`. `tools/check-architecture.mjs` дополнительно проверяет:

- `platform-free-state`: без Android/AndroidX UI, net/store/media/UI imports; Compose runtime для наблюдаемого состояния разрешён;
- `pure-projection`: без Android/Compose/coroutine/runtime transport/storage/media/UI imports;
- `presentation-only`: без Android host, HTTP/WSS, executors и store imports; единственный явно разрешённый net leaf — `MediaLoader`.

Направление между profiled sources также проверяется: state может использовать projection, screen — state/projection, но projection не может потреблять state/screen, а state — screen, включая same-package вызовы. Неизвестный контракт или несуществующий владелец — ошибка. Guard учитывает alias/wildcard/qualified references, игнорирует комментарии/литералы и сохраняет Kotlin interpolation expressions. Это **bounded source guard**, а не полная семантическая модель Kotlin, reflection analysis или доказательство отсутствия всех side effects. Тип read-only Transport, compiler и сценарные тесты дополняют его. Не добавлять исключение ради обхода нового нарушения.

## Измеренная оптимизация

До изменения `WorkflowCard` при каждом elapsed-time tick вновь фильтровал агентов и суммировал JSON tool/token counters. Android-тест с настоящим Compose ticker и CountedAgent зарегистрировал **4 → 6** чтений счётчиков при обновлении времени; тест на отсутствие лишнего сканирования воспроизводимо падал до оптимизации.

Теперь `OrchestrationProjection` группирует агентов и собирает immutable workflow aggregates один раз на принятый снимок. Карточка обновляет только duration, используя готовую строку метрик. Тот же тест показывает **2 → 2**, причём ожидает реального изменения elapsed-time текста. Отсутствующие счётчики не становятся выдуманными нулями, известный0 сохраняется, отрицательные значения остаются ограничены прежним правилом; новые снимки заменяют, не накапливают суммы.

Это устранение измеренной повторной работы. Улучшение FPS, latency, батареи или памяти на телефоне **не измерялось** и не заявляется. Poll interval2500ms и сетевой протокол не меняются.

## Проверки

- `npm run quality`: **85 Node PASS**, owner/import/source-contract checks PASS,10 immutable baseline hashes PASS.
- JVM: **211 PASS**, в том числе26 новых сценариев orchestration state/projection.
- `testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug`: BUILD SUCCESSFUL, lint0 errors/150 warnings/3 information. Новые зависимости и project-wide JVM settings не добавлялись; использован прежний CLI override `-Xmx1g -XX:MaxMetaspaceSize=768m`.
- Полный финальный API35 instrumentation: **82 synthetic PASS +1 opt-in live skipped**, итог `OK (83 tests)`. Отдельные9-case orchestration проверки прошли при normal light/dark,360dp/light/font2.0 и360dp/dark/font1.3.
- Семь actual before/after orchestration PNG на одинаковых1080×2400/density420/dark/font1.0 имеют **0 изменённых пикселей области приложения**, исключая OS status/navigation bars. Снимки и narrow/font/theme примеры просмотрены; immutable release baseline не заменён.
- `git diff --check`: PASS. Эмулятор восстановлен в1080×2400/density420/light/font1.0 и оставлен запущенным/видимым.

Это проверки локального архитектурного дерева, не публикация нового APK. Результаты опубликованного0.6.006 остаются отдельно в `release-0.6.006.md`.

Локальные логи/PNG/счётчики: `/Users/billy/temp/pi-mobile-architecture-20261007/`. Приёмка физического телефона, реальные сетевые обрывы и новая live relay-проверка в этот слайс не входят.

Промежуточные ошибки не скрыты:
- Непосредственная проверка после single tap иногда видела0 раскрытых групп до завершения Compose expansion. Добавлено такое же bounded ожидание появления, какое уже было для исчезновения при collapse; точные group count, preview и repeated-snapshot assertions сохраняются, tap не повторяется.
- Старый workflow-detail тест предполагал, что все нужные строки сразу помещаются в viewport. Он также падает на **опубликованном0.6.006** при360dp/2× font. Теперь проверяется реальная доступность каждой строки через bounded scrolling; merged row description недостаточен, title должен действительно войти в viewport. Production layout ради теста не изменялся.

## Следующие слайсы (не обещание готовности всего проекта)

| Приоритет | Узел / следующий слайс | Сохраняемый контракт и gate |
|---|---|---|
| 1 | `features/chat/ChatScreen`: выделить composer/attachments, active-work docks/Queue и overlay geometry в внутренние presentation-компоненты | Один ChatSession/ChatOutbox; никаких fetch/send внутри компонентов. UI: IME, draft/caret, attachments, Queue, narrow/font/theme, accessibility и сохранение anchor. Не размножать owner state ради сокращения файла. |
| 2 | `features/chat/ChatSession`: после чтения сценариев выделить scoped history/control/config policy, не дробя canonical transcript/outbox ownership | Foreign/unknown ACK, epoch reset, offline/uncertain receipts, ручной retry, occupied draft restore; reconnect не отправляет prompt. Новая фича получает узкий action/port, не доступ к PiClient из Composable. |
| 3 | `ui/PiTranscript` / `ui/MarkdownRenderer`: измерить число Markwon parse/render при неизменном тексте и frame/update workload | Оптимизировать только после реального counter/trace. Проверять streaming text, LaTeX/tables, selectable text, links, palette/font changes, recycled/offscreen leaves и read-only callbacks. Не кешировать лишь по message id. |
| 4 | `ui/UsageCards`, `ui/ArchiveSheet`: вынести IO/state generation в feature-owned injectable owners | Honest stale/unavailable, search/page single-flight, confirmation/scoped ACK. Compose остаётся renderer; persisted settings/credentials у shell/store. JVM state tests + actual sheets/insets/large-font UI. |
| 5 | `net/PiClient`: отделять receipt/subscription/reconnect policies только после transport characterization | Connection/cache/epoch/session ownership; поздний ACK/timeout и reconnect не повторяют mutation. Fake transport + bounded real read-only relay; не менять одновременно wire и клиент. |
| 6 | Host `server/gateway.mjs` / `extension/mobile.ts`: продолжать существующие bounded capability seams, если новая фича пересекает их | Auth/Origin/session scope перед routing; pure contracts отдельно. Node security/projection tests. Не разворачивать изменения на live-host ради теста. |

Несколько Gradle-модулей/DI-framework не вводятся «на будущее»: сначала конкретная независимая граница и выигрыш в enforceability/build isolation. Текущие packages, internal API, injected ports, inventory/guards и compiler дают проверяемые небольшие слайсы без новой инфраструктуры. Большой файл — сигнал для чтения ответственности, не достаточная причина дробить его или обещать оптимизацию.

## Шаблон добавления фичи

1. Назвать владельца и пользовательский сценарий; добавить источник в inventory/passport вместе с кодом.
2. State/actions у feature owner; transport/storage/platform operations через узкий injectable port. Navigation и credentials остаются у composition root.
3. Если нужны Pi mutations — существующий адресный validated command/ACK путь, capability guards, unknown/uncertain handling и явное подтверждение опасного действия. Никогда auto-replay.
4. JVM-тесты happy/error/stop/stale/foreign/offline/restore; Node wire/auth/scope, если затронут host.
5. `npm run quality`, JVM/build/lint, синтетический UI, narrow/font/theme/insets/IME по сценарию. Результат `OK (…)`, не только ADB exit code. Baseline hashes не перезаписывать.
6. Отдельное разрешение на release/deployment/внешнюю отправку. Размер класса не заменяет проверку поведения и profiling.
