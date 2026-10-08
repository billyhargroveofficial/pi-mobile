# Декомпозиция обновлятора — 7 октября 2026

Локальный слайс после опубликованной0.6.007: обновлятор разделён без изменения
версии/code14, зависимостей, подписи, протокола и формата приватных данных.
`AppUpdates` сократился с181 до43 строк и сохраняет API каталога: `status`,
`check(manual)`, `resume()`, `close()`. Общее число строк не является метрикой
скорости: добавлены явные границы и проверки жизненного цикла.

## Владельцы и поведение

- Внутренний `features/updater/UpdateSession` владеет состоянием и идентичностью
  запросов через внедрённый main-thread port. Успешный актуальный check сохраняет
  время проверки; auto-check по-прежнему ограничен шестью часами и учитывает
  отложенную версию. Manual-check может снова предложить её.
- `features/updater/UpdateScreen` содержит только исходную Compose-геометрию
  предложения, прогресса и разрешения. `AppUpdates` связывает модальный host,
  preferences и lifecycle с портами; каталог продолжает читать Compose-состояние.
- Независимый `net/ReleaseClient` не получает gateway/session credentials.
  Сохраняются fixed GitHub endpoint,1MiB metadata limit, максимум5 download
  requests с явной проверкой HTTPS/host/port,64KiB stream buffer, точный размер
  метаданных (максимум32MiB) и обязательный SHA256.
- `ui/UpdateInstaller` сохраняет прежние проверки packageName, строго большего
  versionCode и точного непустого набора SHA256 сертификатов. APK проверяется
  перед сохранением и снова перед ACTION_VIEW; FileProvider раскрывает только
  `cache/updates`. Системное подтверждение Android сохраняется.

Cancel/close сначала снимают текущий request ticket, затем отменяют его HTTP call.
Поздний прогресс, повторное завершение и старый ответ не могут снять busy нового
запроса, изменить его диалог, записать preferences закрытого host или открыть
установщик. Прогресс монотонен и ограничен0–100. Синхронные результаты/ошибки портов
не оставляют состояние busy.

Каждая загрузка владеет отдельным `download-*.part`; finally удаляет только её
файл. Общий для процесса registry сохраняет активные partials разных host/client.
При следующей загрузке очищаются только известные orphan names после process
death, включая прежний `download.part`; посторонние cache-файлы сохраняются.
Проверенный APK заменяется rename в той же директории; прежний `update.apk`
сохраняется при ошибке размера/digest/package-проверки. Гонка mkdir учитывает
параллельное создание каталога.

«Later»/Back на разрешении установки снимают pending action. Только явное
«Open settings» разрешает `resume()` продолжить установку; отказ возвращает
диалог с повторным «Open settings» и «Later». Pending action потребляется до
вызова Android, поэтому повторный resume и ошибка installer/settings не приводят
к скрытым повторным попыткам. Реальная установка всё ещё требует явной загрузки.

`owners.json` регистрирует новые владельцы и sourceContracts. State не может
импортировать Android/UI/net/store, java.net/OkHttp или свой screen; screen не
может обращаться к transport/platform. Source guard дополняет компилятор и
поведенческие проверки и не обещает полного анализа Kotlin/reflection.

## Проверка

- `npm run quality`:94 Node PASS; owner/import checks и10 исходных baseline SHA256
  сохранены. Добавлена отрицательная проверка updater state/screen границ.
- JVM:317 PASS,0 failures/errors/skips. Новые33 проверки:17 UpdateSession и16
  ReleaseClient. Есть controlled duplicate/late callbacks, cancel/retry/close,
  explicit permission return, синхронные ошибки, HTTP bounds/redirect trust,
  exact-size/digest/package rejection, сохранение previous APK, cancel во время
  чтения и до worker/main delivery, cleanup orphan/active partials двух clients.
  HTTP работает через synthetic interceptor, без реальной сети.
- Debug/androidTest сборки успешны; lint0 errors/149 прежних warnings.
- Native API35:24/24 PASS, по8 проверок в1080×2400/font1/light,
  945×2100 (360dp при density420)/font1.3/dark и360dp/font2/light. Шесть
  UpdaterUiTest проверяют production host/state, явные controls, Cancel/Back,
  late results после cancel/close, settings deny/accept/once, installer failure,
  отказ same-version/invalid APK. Дополнительно проходят существующие
  partial-APK/FileProvider и settings/Usage hierarchy проверки. Это focused
  matrix, не повтор полного99-case synthetic UI suite предыдущего релиза.
  Первый dark-прогон обнаружил ранний assert до публикации Compose semantics;
  fixture теперь ждёт конкретный узел (до3s), а screenshot — два Choreographer
  кадра. Финальные три режима прошли с этим ожиданием.
- До изменения production-кода исходные offer/permission были открыты с
  synthetic actions и сняты на Android (1/1 baseline). Финальное matched-сравнение
  новых offer/permission в normal:0 изменённых app pixels для обоих диалогов;
  отличается только clock системной status bar (y54–82, из сравнения исключены
  верхние100px). Девять новых PNG (offer/progress42%/permission ×3 режима)
  просмотрены: тексты/controls читаемы, font2 переносит Download update в две
  строки и сохраняет tappable control.

Host evidence: `/Users/billy/temp/pi-mobile-updater-20261007/` — gate logs,
old/new screenshots, native logs и durable checklist. Final local debug APK
SHA256: `23aaf11d0e70f556a6a102c8aa7f6a0ab60aa95f1295e7c0736d74fde302b535`.
Это локальная сборка, не новая опубликованная версия. AAPT подтвердил package,
versionName0.6.007/versionCode14; apksigner подтвердил прежний certificate SHA256
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Эмулятор восстановлен до1080×2400/font1/light и остановлен после проверки.

## Границы проверки

Нативные updater-сценарии используют production state/modal host с controlled
ports и счётчиками settings/install. Отдельно PackageManager читает настоящий
установленный APK из `.part`, verifier отвергает same-version и invalid archive,
FileProvider читает scoped файл и отвергает private files. Системный installer и
экран изменения разрешений не запускались. Физический телефон, реальный future
release/download/upgrade и process-kill во время HTTP не проверялись; orphan
cleanup моделируется оставленными файлами, конкуренция — двумя synthetic clients.
Новых push/release/Telegram delivery/live Pi/Orca/gateway изменений нет. Чужой
`server/Caddyfile` сохранён с исходным SHA256.
