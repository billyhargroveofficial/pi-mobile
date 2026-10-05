# Pi Mobile — нативный Android-клиент

Независимое Android-приложение (Java, нативные View, без WebView) для Orca/Pi:
список рабочих пространств и живых сессий, живой транскрипт чата, отправка текста
и изображений, просмотр изображений с авторизованной загрузкой.

Контракт сервера — `../docs/protocol.md` (этот каталог его не меняет).

## Требования

| Компонент | Версия |
|---|---|
| JDK | 17 (`/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`) |
| Android SDK | `~/Library/Android/sdk` (platform 35, build-tools 35.0.0) |
| Gradle | 8.11.1 (через wrapper, уже в репозитории) |
| AGP | 8.9.2 |
| compileSdk / targetSdk / minSdk | 35 / 35 / 26 |
| OkHttp | 4.12.0 |

## Сборка

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home
export ANDROID_HOME=$HOME/Library/Android/sdk
cd android

./gradlew :app:testDebugUnitTest     # юнит-тесты (JVM, без эмулятора)
./gradlew :app:assembleDebug         # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:assembleRelease       # release (cleartext запрещён)
./gradlew :app:installDebug          # установка на запущенный эмулятор/устройство
```

`local.properties` не нужен: путь к SDK берётся из `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
Application id — `ru.billyhargrove.pimobile`.

Запуск UI-тестов на подготовленном эмуляторе `PiMobile_API35`:

```bash
adb devices && ./gradlew :app:installDebug
adb shell am start -n ru.billyhargrove.pimobile/.MainActivity
adb shell uiautomator dump /sdcard/ui.xml   # стабильные @+id и contentDescription
```

## Структура

```
app/src/main/java/ru/billyhargrove/pimobile/
├── PiApp.java              единственное соединение на процесс (переживает поворот)
├── MainActivity.java       форма подключения + каталог workspace/сессий
├── ChatActivity.java       транскрипт, композер, вложения, стоп
├── core/                   чистая логика без Android (всё под юнит-тестами)
│   ├── EndpointPolicy      валидация https/http, wsUrl, apiUrl
│   ├── MediaUrlPolicy      same-origin гейт для медиа + удаление dot-сегментов
│   ├── CatalogParser / SnapshotParser / MessagesParser / AckParser
│   ├── TranscriptStore     канонический транскрипт по id (snapshot + messages)
│   ├── TranscriptReconciler слияние с локальными отправками
│   ├── CommandBuilder      subscribe / prompt / abort
│   ├── ImageGuard, ImageMimeType, ImagePayload, TextSanitizer, PayloadCodec
│   └── SessionGrouping, CatalogRow, Session, Workspace, TerminalInfo
├── net/                    PiClient (WS), HttpApi (REST), MediaLoader, AppExecutors
├── media/                  ImagePreparer (ACTION_OPEN_DOCUMENT → payload), Attachment
├── store/                  SettingsStore, SecureTokenStore (AndroidKeyStore)
└── ui/                     CatalogAdapter, MessageAdapter, StatusUi
```

Каталог `app/src/test` содержит 100+ JVM-тестов на парсеры, политику URL, лимиты
изображений, канонический транскрипт и реконсиляцию.

## Протокол: что реализовано

* `GET /api/catalog` (REST, кнопка «Обновить») и `catalog` по WebSocket сразу после
  коннекта; сессии группируются по Orca-workspace, сессии вне Orca — по `cwd`,
  неподключённые терминалы — только чтение («требуется расширение»).
* `subscribe` → `snapshot` (полный транскрипт, заменяет видимый) на каждый
  subscribe/reconnect. Клиент **всегда** пере-подписывается после реконнекта.
* `{type:'messages',…}` — инкрементальный кадр: `messages` (новые/изменённые),
  `removedIds`, `status`, `connected`, `truncated`. `TranscriptStore` держит
  канонический порядок по `id`: изменённое сообщение обновляется **на месте**
  (его позиция не прыгает), новые добавляются в конец, `removedIds` удаляются.
  Отсутствующие в кадре поля не затирают уже известное состояние.
* `prompt` с `requestId` (UUID), `behavior: followUp | steer`, максимум 3
  изображения / 10 МБ (png, jpeg, webp). `abort` — «Стоп».
* Ack — это принятие, не завершение. Нет ack за 20 с или обрыв сокета → сообщение
  помечается «статус неизвестен» и **не отправляется повторно автоматически**;
  повтор — только явной кнопкой «Повторить вручную» (с предупреждением о дубликате).
* Изображения: `GET /api/sessions/<id>/media/<sha256>` на том же origin с
  заголовком `Authorization`; лимит 10 МБ, декодирование с ограничением размера.

### Порядок и производительность списка

`MessageAdapter.submit()` сначала пробует быстрый путь: если ключи (`id`)
совпадают префиксом, выполняются только `notifyItemChanged` для реально
изменившихся строк и `notifyItemRangeInserted` для хвоста — это случай
стриминга ответа. Иначе используется `DiffUtil` с ключами по `id`. Полной
пересборки списка нет, позиция скролла сохраняется; автоскролл происходит
только если изменился хвост и пользователь уже был внизу.

## Безопасность

* Токен хранится только в AES-256-GCM внутри AndroidKeyStore
  (`SecureTokenStore`), в открытом виде никогда не пишется, не логируется и не
  попадает в URL — `Authorization: Bearer` только в заголовке.
* `EndpointPolicy`: https всегда; `http` разрешён лишь в debug и только для
  `10.0.2.2`, `localhost`, `127.0.0.1`. В release cleartext запрещён
  (`network_security_config.xml`), debug-оверрайд лежит в
  `src/debug/res/xml/network_security_config.xml`.
* `MediaUrlPolicy` пропускает только URL того же origin (схема+хост+эффективный
  порт) и канонизирует путь по RFC 3986; иначе загрузка отклоняется **до**
  прикрепления заголовка — токен не уходит на сторонние хосты. Принимаются только
  `http`/`https`, `javascript:`/`data:`/`file:`/userinfo отклоняются.
* Никаких кастомных `TrustManager`/`HostnameVerifier` — доверие только системным
  сертификатам. Debug-логи OkHttp: уровень `BASIC` (без тел) + `redactHeader("Authorization")`.
* `allowBackup=false`, `dataExtractionRules`/`fullBackupContent` запрещают
  облачный бэкап и device-transfer. Секретов в `BuildConfig` нет.
* Текст из снимков рендерится как обычный текст (`TextSanitizer`: без
  управляющих символов, без парсинга HTML).

## Тестовые хуки (ADB/UIAutomator)

Стабильные идентификаторы: `connectUrlInput`, `connectTokenInput`,
`connectButton`, `healthCheckButton`, `disconnectButton`, `settingsButton`,
`refreshButton`, `connectionStatusText`, `catalogList`, `messageList`,
`composerInput`, `sendButton`, `stopButton`, `attachImageButton`,
`behaviorFollowUp`, `behaviorSteer`, `attachmentStrip`.

У интерактивных элементов заданы `contentDescription` (`cd_*` в `strings.xml`):
`cd_session_row` («Сессия: <название>»), `cd_terminal_row`, `cd_connection_status`,
`cd_send_button` и т. д.

## Ограничения

* Только чтение/отправка: нет редактирования файлов и произвольных путей.
* Состояние транскрипта живёт в памяти процесса; на диск ничего не пишется.
* QR/сканирование токена, несколько серверов и push-уведомления не реализованы.
