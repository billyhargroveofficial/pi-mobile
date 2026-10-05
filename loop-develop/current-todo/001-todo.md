# 001 — Agent-native Pi Mobile, Kotlin/Compose и доказуемая визуальная преемственность

**Статус:** цель поставлена 5 октября 2026; реализация миграции не начата. Это долговременный roadmap с последовательными milestone; первый исполнитель начинает с M0, а не переписывает всё за один заход.

## Goal

Перевести **весь Pi Mobile** на поддерживаемую Kotlin/Jetpack Compose реализацию Android и сделать репозиторий агентно-ориентированным по принципу `nareshka-mono`: тонкие composition roots, капсулы с явным владельцем, узкие публичные контракты, проверяемое направление зависимостей и инструкции для независимых агентов. **Сохранить то, что уже хорошо работает и выглядит**, улучшать подтверждённые дефекты дизайна по проверяемым сценариям; не допустить потери функциональности, истории, настроек, подписи и безопасности.

## Source Research Summary

**Decision question:** как перенести Java/XML Android и нынешний монолит gateway/extension в расширяемые feature-капсулы, не получив большой невыпускаемый rewrite и визуальную деградацию?

**Проверенное локальное состояние:** `pi-mobile` commit `3c08e10` / APK `0.6.003` (versionCode10). Android — один Gradle-модуль `android/app`, Java Views/XML: `ChatActivity`, `MainActivity`, `MessageAdapter`, `OrchestrationActivity`, `net/PiClient`, `net/HttpApi`, приватные кеши и настройки. Gateway — Node `server/gateway.mjs`, Pi-bridge — `extension/mobile.ts`; контракт в `docs/protocol.md`. Тестовый baseline: 64 Node, 131 JVM и 40 UI на API35. Действующий сертификат Android сохраняется; версии `0.6.000 → 0.6.001 → … → 0.6.100` с возрастающим Android versionCode. Reference architecture только для принципов: `/Users/billy/repos/nareshka-mono/{AGENTS.md,llms.txt,docs/architecture.md,domains/README.md,loop-develop/README.md}`. Не переносить оттуда React, Go, DB, деплой и проектные политики Николая.

**Существующий UI — обязательный baseline, а не объект тотальной замены:** нейтральная палитра и настраиваемый цвет пузырей; единая Usage-панель; один ряд статус/модель/effort/tier; диалог с раздельными progress и tool-группами, ручной zoom картинок; composer, диктовка, каталог, история, settings и новые workflow/agent drill-down. Исходные снимки эмулятора в игнорируемых `artifacts/design-0.6.001/` и `artifacts/design-0.6.003/`; перенести проверенные эталоны в воспроизводимый test/evidence pipeline до рефакторинга.

**Goals:** Android целиком Kotlin/Compose, ясное владение экраном/состоянием/контрактом, независимые тесты feature-капсул, быстрые безопасные изменения агента и более цельный дизайн при сохранении UX. Node gateway и Pi extension тоже разложить на ограниченные по ответственности капсулы без смены их runtime только ради стиля.

**Non-goals / границы:** не создавать второй Pi, не прерывать работающих агентов, не пересоздавать историю, не сбрасывать endpoint/token/draft/cache; не менять глобальные модели/effort, произвольно открывать серверный порт, переносить секреты в APK или URL. Не устраивать big-bang merge и не считать Compose автоматическим улучшением дизайна. `nareshka-mono` только читать, не править. Релизы и Telegram — после успешных проверок по актуальной воле Billy; цель сама по себе не выпускает APK.

**Статус-кво:** оставить Java/XML проще сейчас, но дальнейшие функции продолжают раздувать Activity, адаптер, gateway и межслойную связанность.

**Минимальный безопасный путь:** сначала инвентаризация поведения, design tokens и контрактов, затем Kotlin/Compose вместе с View-interop в вертикальном слайсе; каждый milestone сохраняет исполняемый release и убирает заменённый legacy-код после parity-gate.

**Альтернатива:** одномоментно переписать Android и Node gateway. Она быстрее выглядит на схеме, но ухудшает проверяемость, ломает подпись/состояние/UX при откате и скрывает регрессии; отклонена. Изменить рекомендацию можно только при измеренном техническом блокере interop либо проверенной невозможности обеспечить паритет инкрементально. Confidence высокая для процесса, конкретные module/API решения исследовать на M0 против **актуальных официальных Android/Compose документов**, а не угадывать версии заранее.

## Product Shape и архитектурная граница

```text
Pi in Orca ← unchanged local Unix bridge ← host gateway (loopback/HTTPS)
   │                                            │
   │ live transcript, agent/workflow states     │ auth/sync/history/media/speech
   └──────────────── canonical versioned wire ──┘
                                                  │ HTTPS/WSS + bearer
Android thin app shell → features/{catalog,chat,orchestration,history,settings,voice,updates}
                       ↓ public feature APIs only
                 core/{protocol,session,storage,design-system,media}
```

Это **карта владения**, не приказ немедленно создать 12 Gradle-модулей. Начать с package boundaries и доказуемых модульных seam; физические Gradle modules добавлять при реальной пользе от независимой сборки/границ. Feature владеет состоянием и UI; core — только повторно используемые примитивы; app shell — только композиция, навигация и lifecycle. Контракты ошибок, reconnect/resume, однократной отправки, agent ownership, read-only inspection и server-pushed capabilities должны иметь одного владельца и тесты на обеих сторонах wire. Никаких параллельных Java+Kotlin копий бизнес-логики на годы.

Существующий порядок и плотность экранов — исходная цель. Менять иерархию, анимации, отступы или цвета лишь при доказанной пользе; новый UI проверять на тёмной/светлой теме, 360dp, увеличенном шрифте, IME, статус/gesture insets, длинных строках и реальных данных. Accessibility и отсутствие отсечения важнее декоративных эффектов.

## Implementation Checklist / milestones

1. **M0: зафиксировать поведение и визуальный baseline.** Боль Billy: красивый текущий экран легко случайно испортить. Создать инвентарь экранов/состояний/ownership, воспроизводимые screenshot tests и smoke на API35 + узком/крупном шрифте; измерить текущие границы импортов и latency/память. Уточнить совместимые Kotlin/Compose/AGP по официальным документам перед изменением build.
2. **M1: канон для агентов и контрактов.** Боль: агентам трудно безопасно найти владельца функции. Ввести компактный `AGENTS.md`, `llms.txt`, индекс `docs/architecture`, scoped feature-паспорта только по реально существующему коду и fail-closed проверки импортов/route-to-owner. Не копировать 1:1 инструментальную сложность Nareshka.
3. **M2: Kotlin/Compose foundation без изменения внешнего вида.** Боль: Java Activity переполнены UI-состоянием. Добавить Kotlin, Compose interop, app design tokens и один вертикальный слайс с обратимым флагом; проверить cold start, accessibility, UI/скриншот-паритет и поддержку старых настроек.
4. **M3: chat/attachments/agent reader вертикальными слайсами.** Боль: длинные ходы, zoom, optimistic bubbles и внутренний диалог агента хрупки. Перенести модель состояния, list/scroll/reconnect, tool groups, markdown/media и composer без изменения wire-семантики; тестировать неизвестные ACK, дубли, image resize metadata, parent/child navigation и старые сообщения. Удалять legacy-экран после parity, не держать две логики.
5. **M4: каталог, история, Usage, voice, settings, updater.** Боль: изменение одного экрана не должно ломать другой. Перенести оставшиеся пользовательские journeys по одному, сохранять существующие данные/подпись/versionCode, постепенно улучшать типографику, плотность и анимацию при честном before/after.
6. **M5: gateway/extension и wire ownership.** Боль: новый UI трудно расширять, когда backend смешивает auth, жизненный цикл, историю, медиа и workflows. Выделить reader/controller и bounded modules по протоколу; замерить совместимость старого клиента/нового gateway и наоборот, не менять auth/Unix-socket trust boundary; не трогать live Pi ради миграции.
7. **M6: end-to-end hardening и уборка.** Боль: скрытый dual-stack и тестовые швы делают архитектуру фиктивной. Удалить доказанно недостижимые XML/Java классы/устаревшие adapters, включить lint/API/architecture checks, проверить Android upgrade path, Mac/Linux relay, cold/warm reconnect, nested workflows и физический телефон; обновить docs и rollback/runbook.

Для milestone M1–M6 оформлять новый конкретный slice record после проверки текущего кода; не стартовать всё параллельно. Каждое изменение закрывает одну пользовательскую боль и оставляет собираемое приложение. Если фича пока недоступна старой Pi-сессии — сообщать это, а не симулировать live-состояние.

## Target Files и границы

Сначала читать и инвентаризировать: `android/{settings.gradle,build.gradle,app/build.gradle}`, `android/app/src/main/{java,res,AndroidManifest.xml}`, `android/app/src/{test,androidTest}`, `extension/`, `server/`, `tests/`, `docs/{protocol.md,setup.md}`, `scripts/`. Reference читать только в `nareshka-mono`, туда не писать. Не переписывать `artifacts/`, реальные токены `~/.pi/agent/pi-mobile/`, системные файлы Orca/дочерних Pi; certificate/build version/Android applicationId менять только по отдельной явной задаче.

## Verification Commands / gates

- До правок: `npm test`; `cd android && ./gradlew testDebugUnitTest assembleDebug assembleDebugAndroidTest` с JDK17/SDK. Проверить чистый `git status` и baseline APK certificate/versionCode.
- После каждого слайса: focused Node/JVM/UI tests + полный baseline; API35 emulator via ADB/UiAutomator, снимки экрана и визуальное сравнение нормального/360dp/1.3×/тёмного состояния, IME и голосового/медийного journey. `git diff --check` и направленные architecture gates.
- При изменении gateway/extension: реальный локальный authenticated REST/WSS smoke, unauthorized/origin/path tests, проверка старого клиента/нового сервера; не отправлять платный prompt и не делать `/reload` активной Pi-сессии.
- Для релиза: проверка signed APK package/versionCode/cert/SHA256, upgrade поверх предыдущего APK без стирания данных, проверка ссылки релиза и только затем отправка в правильный чат с точным списком ограничений.

## Done Means

Все действующие Android экраны реализованы на Kotlin/Compose (Java/XML остаются лишь сознательно задокументированными platform interop в случае необходимости), отсутствует duplicated feature logic; архитектурные границы имеют машинные проверки и краткие agent-readable карты. Baseline поведение, приватные данные и паритет важных экранов доказаны на устройстве/эмуляторе, выбранные улучшения дизайна сравнимы с прежним UI. Node/Pi интеграция не теряет полномочий/ограничений; один проверенный APK обновляется поверх0.6.003 с той же подписью и подтверждённым откатом. Никаких утверждений «готово», если не пройдена физическая установка/реальные критические сценарии.

## Copy-Ready Goal Prompt

```text
/goal Реализуй цель из loop-develop/current-todo/001-todo.md. Начни ТОЛЬКО с M0: изучи текущий код Pi Mobile и архитектурный принцип /Users/billy/repos/nareshka-mono (только чтение), зафиксируй пользовательские и визуальные baseline/контракты до миграции, сделай минимальный проверяемый M0-слайс. Не мигрируй ещё весь проект и не меняй уже удачные экраны без измеримой причины. Не прерывай работающие Pi, не перезапускай Orca; не публикуй APK/релиз и не отправляй сообщения без актуального разрешения. После M0 отчитайся доказательствами, препятствиями и предложи следующий конкретный slice.
```
