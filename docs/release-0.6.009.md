# Pi Mobile 0.6.009 — сохранённые компьютеры и настоящий release-вариант

8 октября2026. Billy прямо разрешил выпуск сохранённых endpoints и потребовал
release-сборку вместо debug; правило записано в канонический
`/Users/billy/.agents/AGENTS.md` (Pi symlink сохранён) и проектный `AGENTS.md`.
После публикации он отдельно разрешил запустить Orca и настроить workspace у брата.
Рабочие Pi/Orca/gateway Billy не перезапускаются; чужой `server/Caddyfile` исключён.
Секреты пользователя, device-token и материалы подписи не входят в Git/APK/отчёт.

## Возможность

До32 сохранённых компьютеров, для каждого имя, URL и отдельно зашифрованный токен.
Нажатие хоста в шапке → Computers: выбор, Add, Edit. Settings: Saved computers,
Save & connect, подтверждаемое Forget. Пустой token field сохраняет только секрет
этого же канонического адреса; новый endpoint требует собственный токен.
Старое подключение переносится лениво, с теми же cipher/URL и приватным outbox.
Один мобильный socket; переключение не останавливает удалённые задачи, не
переносит старые каталог/HTTP/launch/ACK/cache/Activity navigation на другой хост,
не отправляет промпт и не повторяет неизвестный результат.
[Владельцы, поведение и pre-bump evidence](multi-endpoint-20261008.md).

## Release, не debug

`assembleRelease`, BuildConfig.DEBUG=false, android:debuggable=false/отсутствует,
нет HttpLoggingInterceptor и нет debug-only cleartext overrides. `minifyEnabled`
и `shrinkResources` по-прежнему false: R8 и новую оптимизацию поведения этим
релизом не вводили. Release resource packaging сокращает пути XML; verifier
находит реальный networkSecurityConfig через manifest/resource table, а не
предполагает debug filename. Нативный ReleasePolicyTest проверяет именно
установленное приложение: флаги, HTTP client и NetworkSecurityPolicy.

Подпись и тип сборки — разные свойства. Ранее опубликованные APK подписаны
историческим Android debug-сертификатом. Для upgrade/AndroidKeyStore сохранены
**тот же private key/certificate**, а не создана несовместимая новая идентичность.
Для release signing используется отдельная приватная PKCS12-копия с новым
случайным паролем; конфигурация вне Git, оба файла0600. Это не production/Play Store
certificate и не новая signing-key rotation. Его замена требует отдельной миграции.

`python3 scripts/build-release.py --previous <previous.apk> --output <artifact-dir>`
с JDK17/Android SDK запускает release JVM/build/instrumentation/lint, выравнивает
APK на16KiB и подписывает через приватный локальный config без паролей в argv/Git.
Test APK подписывается отдельно и остаётся только в локальном evidence.
`scripts/verify-release-apk.py` fail-closed проверяет фактический пакет, возрастающий
versionCode, прежний сертификат, non-debuggable manifest, TLS XML и zipalign.
Негативный контроль: debug0.6.009/code16 с release-подобным именем отклонён.
Публикуются только явно указанные app APK и SHA256SUMS, не wildcard *.apk.

## Проверки

- Node quality108 PASS; owner/import checks и10 immutable baseline PNG/hash PASS.
- Release JVM628 PASS; assembleRelease/assembleReleaseAndroidTest/lintRelease PASS,
  lint0 errors. Зависимости, package/minSdk26/targetSdk35 сохранены.
- Предыдущий публичный0.6.008/code15 установлен в API35 эмулятор без удаления.
  ReleaseUpgradeSeedTest создаёт лишь собственный legacy cipher/receipt fixture и
  сохраняет SHA256 всех10 найденных приватных shared_prefs/no_backup файлов. После `adb install -r`
  настоящего signed release0.6.009/code16 ReleaseUpgradeVerifyTest подтвердил
  неизменные приватные bytes, расшифровку прежним Keystore key, точное сохранение
  URL/envelope и старого host/session receipt. Далее сохранены два независимых
  профиля; оба токена читаются после переключений. Seed: OK(1); verify + реальная
  ReleasePolicyTest: OK(3). Удалены только собственные fixtures.
- Полный native прогон именно frozen release APK на API35 ARM64:194 PASS,
  3 explicit opt-in assumptions skipped (live relay и оба upgrade stages,
  уже проверенные отдельно), **OK(197 tests)**,857,497s;0 failures.
- Display/runtime matrix на том же signed release APK: **6 normal +6
  360dp/font2/light +6 360dp/font1.3/dark PASS**, по34,5/35,4/34,2s. Четыре
  реальные form/picker/forget сценария и два release-policy gate в каждом режиме.
  Шесть representative PNG просмотрены; всего15 feature captures сохранены,
  эталонные baseline PNG не менялись.
- Pre-bump feature evidence: полный192-case debug run +21 affected после
  forget-return guard +12 normal/font/theme captures; это не выдаётся за
  release-variant тесты. Физический телефон и реальные удалённые Pi-команды
  ими не проверялись.

## Зафиксированный APK

Package `ru.billyhargrove.pimobile`, versionName0.6.009, versionCode16.
`Pi-Mobile-0.6.009.apk`:12 091 655 bytes, SHA256
`04f1c32bff4d17afeead1a980d68837d4ac9a7bb2d5bf3766efc6680b89a4d35`.
Certificate SHA256:
`86c0c25a38f5c9231196fb5b647cda6ed8938eb8a0c392dcd00a31a74a6b52a4`.
Перестроение debug-контроля не меняет frozen release artifact.
Это уменьшение размера относительно debug не доказывает FPS/CPU/battery improvement.
Evidence: `/Users/billy/temp/pi-mobile-release-0.6.009/`.

## Публикация и границы

Публикация выполняется только после final release gates; результаты public
APK/checksum/latest/tag verification дописываются после фактического успеха.
Физический телефон, реальный микрофон/IME/вложения/background и live Pi команды
остаются отдельной приёмкой. Проверки не запускают платные промпты и не закрывают
рабочие Pi. Приватные ключи/токены не публикуются; HTTPS/WSS/certificate checks
не отключаются. Инструментальные screenshots — синтетические, baseline не заменяется.
