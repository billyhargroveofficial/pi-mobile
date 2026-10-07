# Ревью и архитектурные изменения Pi Mobile — 7 октября 2026

Цель Billy: ревью репозитория, декомпозиция, оптимизация и переиспользование
компонентов для расширяемости и стабильности приложения. Эта работа продолжает
существующую структуру владельцев после завершённой миграции Compose. В ходе работы Billy
добавил требования: не сдвигать чтение при новых событиях и плавно показывать
обновления после возврата в приложение.

## Проверенные проблемы и исправления

| Проблема | Последствие | Изменение и проверка |
|---|---|---|
| `ChatActivity.onStop` отключает listener; `ChatSession` сохраняет свои request IDs, хотя клиент уже может обработать ACK/таймаут | После возвращения composer/effort/history/document могли оставаться в ожидании; состояние Sending могло не завершиться | `ChatSession.start` сверяет свои запросы с текущим ledger. Исчезнувший запрос становится unknown, живой остаётся pending. Не отправляет его снова и не выдумывает acceptance. JVM: detached/live tickets, поздний авторитетный ACK. |
| `PiClient` передавал `ack.data` и пополнял history cache до проверки pending request/session | Чужой, повторный или неизвестный data reply мог попасть в consumer/cache | Data dispatch и history prepend допускаются только для текущего scoped ticket. Поздний обычный ACK по-прежнему разрешён: outbox может принять подтверждение uncertain receipt. Native test использует настоящий frame dispatcher с синтетическими ticket/frame. |
| При явном `PiClient.connect` старая сокетная identity инвалидировалась без немедленного завершения pending | Запрос от старого подключения ещё ждал 20 секунд | Замена соединения сначала завершает старые tickets как uncertain. Чистый `net/PendingCommands` владеет deadline/identity/cancellation; чужой ACK и уже поставленный отменённый таймер не завершают новое ожидание. |
| `MediaLoader` кэшировал и объединял in-flight запросы по URL | После смены token та же ссылка могла вернуть прежние bytes/ошибку без новой авторизации | Ключ SHA256 использует существующую схему `ConversationCache.key(endpoint, token, resource)`. Native test блокирует старый HTTP-запрос, меняет синтетический token и доказывает отдельные загрузки red/blue и правильный последующий cache hit. |
| `MarkdownRenderer` объединял только уже завершённые parses; tag содержал только source | Одновременные одинаковые leaves разбирались повторно; новый renderer мог пропустить повторный bind при том же source | In-flight fan-out через слабые ссылки; tag связывает renderer identity и source. Старый результат/закрытый renderer не перезаписывает новый bind, неуспешный parse допускает дальнейшую попытку. Настоящий native parse counter и проверки streaming/rebinding/disposal. |
| `ActiveOrchestration` заново собирал static tool/token counters при каждом duration tick | Лишний обход JSON в постоянно обновляющемся dock | Counters собираются по принятому snapshot; ticker вычисляет только duration. Общий `core/AgentMetrics` используется dock и workflow detail, отличает unknown от actual zero и защищает сумму от переполнения. Native counter ждёт реального изменения duration. |
| Pi extension сохранял history/document/MCP replies в `seen`; catch-path не ограничивал размер Map | До 1000 крупных history/document payloads и неограниченная серия ошибок занимали память; MCP мог стать устаревшим | Общий чистый `contracts/CommandReceipts` используется gateway и extension: только mutation receipts, FIFO ≤1000 и для success, и для failure. Read replies не сохраняются. End-to-end MCP test меняет источник между чтениями с тем же request ID. |
| `session-projection` предполагал, что все models/turns — объекты | Null/scalar row мог исключением закрыть зарегистрированный Unix bridge; бесконечные/отрицательные метрики попадали в wire | Allowlist projection фильтрует неправильные rows, ограничивает строки, finite nonnegative counters и epoch; models truncation сообщается явно. Node malformed-schema tests. |
| `orchestration` превращал absent/invalid tool/token counters в zero и предполагал object phases | UI показывал несуществующий ноль; неправильная фаза могла сломать мост | Optional finite counters сохраняют unknown; null/scalar/array phases отбрасываются. Node tests различают unknown/zero и проверяют malformed bridge packet. |
| `WorkTimeline` менял key ответа при переходе final → progress | LazyColumn терял identity читаемой строки при перестроении turn | Message identity сохраняется независимо от phase. Совместимость прежних `progress:` viewport anchors при восстановлении. JVM + actual UI key/pixel-offset checks при поздних tools, новом answer и streaming. |
| Полный reconnect snapshot заменял paginated cache текущим хвостом | Уже подгруженный prefix/anchor исчезал; продолжение истории шло от нового tail cursor | `ConversationFrames` сохраняет proven prefix на same-epoch paginated overlap; incoming tail авторитетен, удалённые в tail строки не возвращаются. PiClient передаёт merged snapshot/metadata всем consumers. Новая epoch, полный/пустой snapshot и отсутствие overlap не выдумывают непрерывность. JVM cache tests. |
| Новые/изменённые строки и catch-up tail появлялись одномоментно | Возврат не объяснял пользователю новые события; follow-tail прыгал | ChatSession маркирует arrival batch; shared PiTranscript меняет только opacity, не layout. Streaming chunks не перезапускают reveal. При follow-tail движение использует measured extent и animation; при чтении выше конца позиция остаётся, рядом со стрелкой появляется `New activity`. System animation disable соблюдается. Actual stop/start, hardware PixelCopy frames, Choreographer viewport positions и reduced-motion UI checks. |

## Декомпозиция и точки расширения

```text
ChatActivity: lifecycle, credentials/transport adapter, navigation, platform IO
  └─ ChatSession: canonical transcript, draft, outbox and scoped actions
      └─ ChatScreen: reader/follow-tail + measured overlay canvas
          ├─ ChatHeader: title, status, notice and navigation callbacks
          ├─ ChatComposer: text/caret, attachments, delivery modal, anchor/IME geometry
          └─ ChatWorkDock: activity, Queue and skills from the same owners

ui/PiIconButton: reusable 48dp action/semantics/loading/surfaces
core/AgentMetrics: shared pure static aggregation
net/PendingCommands: injected deadline/cancellation; no sockets or Android types
contracts/CommandReceipts: shared runtime-independent mutation deduplication
```

`ChatScreen` уменьшен с419 до160 строк, включая новые требования к arrivals/
viewport. Это отделение ответственности; скорость измеряется отдельно. Новые presentation-компоненты `internal`; draft/caret/attachments/Queue
не копируются в новые state owners. Header/dock/screen/composer зарегистрированы
как `presentation-only`. Они не получают HttpApi, PiClient, executor или store.
Единственные разрешённые platform values — существующий media leaf и Rect для
экранного anchor. Граница не разрешает Activity/Context/HTTP imports. Shared
PiTranscript остаётся единственным renderer для main chat и read-only inspection.

Добавление фичи начинается с владельца сценария и маленького action/port. Новая
кнопка принимает callback; command, scoped ACK и uncertain policy остаются у owner.
Новый pure contract может потребляться host и extension, но реализации этих
runtime не импортируют друг друга. Inventory/guards обновляются вместе с кодом.

## Остальной репозиторий

Проверены направления зависимостей и текущие сценарии catalog/launch/resume/close/
delete, canonical transcript/history/outbox/cache, read-only orchestration,
voice, updater, host auth/Origin/media/projection/ownership и Pi extension. В
`npm run quality` присутствуют тесты безопасного resume/new, подмены session file,
symlink confinement, ограничений медиа, credentials allowlist, command validation,
read/write rate limits, bridge/session ownership и no-auto-replay. JVM покрывает
чистые policies/parser/state; instrumentation — реальные Compose/Android leaves.
Green gate проверяет эти сценарии, а не все возможные состояния продукта.

CatalogSession, ChatOutbox, OrchestrationSession/Projection, core/net/store/media
уже имеют разумных владельцев; дополнительных Gradle-модулей/DI и параллельных
реализаций не добавлено. При последующем запросе продолжить архитектурную работу
UsageCards/ArchiveSheet разделены на platform adapters и внутренние владельцы,
проекции и экраны; подробности и новые проверки в
[catalog slice](architecture-catalog-20261007.md).
Gateway — composition root для уже выделенных capabilities; в этой работе общая
receipt policy вынесена из двух runtime, остальные routes не переписываются ради
строк кода. Следующее выделение этих owners/routes следует конкретной новой фиче
или доказанной ошибке; это не незавершённый prerequisite текущего изменения.

## Проверки и границы доказательства

Проверки и временные результаты: `/Users/billy/temp/pi-mobile-review-20261007/`.
Node quality:91/91 PASS; ownership/import/source contracts и10 immutable PNG
hashes PASS. JVM:225/225 PASS. Debug + androidTest APK и lint собраны успешно;
lint0 errors/150 существующих warnings. Полный API35 instrumentation:93 synthetic
PASS/1 live opt-in skipped (`OK (94 tests)`),324s. После последнего epoch fix
повторены225 JVM и полная сборка/lint; итоговый APK дополнительно проверен в
normal/360dp font/theme matrix: по6 affected Redesign cases, metric ticker и5
arrival cases. Normal/dark12 PASS; font2 Redesign6 + ticker1 PASS,4 arrivals PASS,
одна fixture failure описана ниже. После visual capsule fix и исправления
измерения все5 arrivals снова PASS в каждом из3 display profiles (15/15). Шесть matched static before/after
PNG (conversation, empty, read-only, Queue, draft hidden keyboard, tools) имеют
0 изменённых app pixels; сравнение исключает status/nav bars. Immutable baseline
не перезаписывается.
Characterization: исходный native Markdown разбирал одинаковые leaves дважды;
исходный dock после корректного inset fixture перечитывал counters6→10 на реальном
clock tick. Новый runtime gate проверяет1 parse и2→2. Первый catch-up UI прогон
доказал hardware fade и stable reader, но дал только4 distinct viewport positions;
длительность движения увеличена: повторный gate дал36 промежуточных положений.
Итоговый catch-up дал36/36/37 промежуточных положений в normal/1.3× dark/2× light.
Hardware document capture дал16 различных кадров в каждом profile; проверено изменение только
opacity, без изменения строковой геометрии. Первоначальный viewport
unit regression с local receipt выявил ошибочный критерий firstRendered; отдельный
viewportLoaded сохраняет совместимость. Epoch regression дополнительно проверил отложенный reset disclosure до получения
нового snapshot и сохранение active-turn metadata. Все225 JVM checks прошли после
исправления; промежуточные failures не считаются готовой проверкой.

При2× helper первоначально соединял key предыдущей строки внутри content padding
с firstVisible offset другой строки. Итоговый helper берёт реальный firstVisible
item и его measured pixel offset; дополнительный assertion доказывает, что fixture
попала в requested message. Stable anchor проверен при late tools/answer,15 chunks,
настоящем history prepend, Activity CREATED→RESUMED, cached restore и motion off.
Визуальный просмотр выявил наложение подписи New activity на document; нейтральная
capsule отделяет её от текста, сохраняя geometry и48dp jump action. Политика
не добавляет permanent animation к обычным streaming chunks.

Последний RuntimeStabilityTest:7/7 PASS, включая настоящий PiClient frame dispatcher
для full + resumed snapshot prefix/cursor delivery и epoch reset. Его production
code тот же, что в final display matrix. Текущий итоговый debug APK находится в
`android/app/build/outputs/apk/debug/app-debug.apk`; это локальная проверочная сборка
с прежней версией, не опубликованный upgrade.

Live Pi/Orca/gateway не разворачиваются, не перегружаются и не получают промптов.
Чужая правка `server/Caddyfile` сохранена. Version0.6.006/code13, зависимости,
private formats и настройки подписи сохранены; публикации нет. Эта работа не утверждает
физическую phone/background/network acceptance, Linux-session parity, снижение
latency/FPS/энергопотребления или полный независимый security audit. Нативные
счётчики доказывают только устранение конкретной повторной работы. Lint warnings
старого дерева отдельно указываются в итоговых результатах; ошибки gate должны
быть устранены, а не скрыты suppression/baseline.
