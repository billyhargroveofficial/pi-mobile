# Проверки — 5 октября 2026

## Автотесты и сборка

- `npm test`: 5/5 PASS. Авторизация HTTP/WS, Origin, права файлов, image bounds/signatures, адресация одного владельца сессии, dedupe, offline, повторный владелец, socket lock; lifecycle расширения и команды.
- `./gradlew testDebugUnitTest assembleDebug`: BUILD SUCCESSFUL, 99 тестов, 0 ошибок/падений (13 классов).
- Финальная APK установлена в Android Emulator через `adb install -r`: Success.
- Проверены исходники на наличие фактического device-token: совпадений нет.

## Живой Pi, не заглушка

Два специально созданных Pi в разных Orca workspaces:

1. `Mobile integration test`, pi-mobile, session `01a10bc4-2e90-7048-ba60-6b820ed8888e`.
2. `Mobile isolation test`, harness-space, session `01a10bc7-f7ef-7240-97b8-4aaf0a9c4d5d`.

- Через мост отправлена зелёная PNG; настоящий Pi принял её и вызвал read для `artifacts/green-square.png`. Картинка из tool result отобразилась на Android; ответ `Green. MOBILE_IMAGE_OK` пришёл в ту же терминальную сессию. При первой попытке провайдер вернул overload, затем автоматический retry Pi завершился успешно.
- Через **реальный Android UI** отправлен текст: ответ `ANDROID_UI_OK` виден и в приложении, и в терминале.
- Через системный Android file picker выбрана PNG из Downloads и отправлена в чат: ответ `Green. ANDROID_IMAGE_OK` виден в обоих интерфейсах; исходная картинка видна в пузыре пользователя.
- Через **публичный WSS** отправлена команда второй сессии; получен `ISOLATED_SECOND_SESSION_OK`, первая сессия не изменилась. Разные workspace IDs подтверждены.
- Gateway перезапущен после правок: расширения и Android восстановили соединение. Команды не переотправлялись.
- Тестовая вкладка второй сессии закрыта; первая оставлена как демонстрация.

## HTTPS и сеть

- Caddy получил сертификат Let's Encrypt; `curl https://billyhargrove.ru/health` → HTTP200, `ssl_verify_result=0`.
- `GET /api/catalog` без токена → HTTP401.
- Android переключён на `https://billyhargrove.ru`: статус «Подключено», каталог и чат с картинками доступны после перезапуска gateway.
- Проверка проводилась с Mac и его Android Emulator. Отдельный физический телефон/сотовая сеть пока **не проверены**.
- На ZTE изменены только правила TCP80/443 на Mac192.168.1.5; закреплён его DHCP-адрес. Старые другие правила не затронуты. Пароль роутера в проект не записан. Chrome Shared после настройки остановлен.

## UI/эмулятор

- AVD `PiMobile_API35`, Android15/API35, ARM64 Pixel7, 1080×2400.
- Native RecyclerView, не PTY/WebView. Проверены safe insets, composer выше клавиатуры, отдельная строка кнопок, отправка и выбор изображения.
- Все workspaces остаются видимы, в том числе пустые. Не подключённые терминалы помечены «требуется расширение».
- Кэш bitmap ограничен12MB через LruCache; HTTP-редиректы отключены.
- Скриншоты (локальные, gitignored): `artifacts/android-public-https.png`, `artifacts/android-images-https.png`.
- Эмулятор оставлен открытым на демонстрационном чате. Остановить: `./scripts/emulator.sh stop`.

## Артефакт

`artifacts/Pi-Mobile-debug.apk` — debug, не production-signed релиз.

SHA256:
```
009c9041875f8d262e6526d45023e99680418531752deb4db8a2db946bbdb78a
```

Это функциональная проверка MVP, не полноценный security-аудит и не обещание отсутствия ошибок. Ограничения перечислены в README.
