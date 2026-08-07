# WiFi Overlay Switcher

Приложение для Android: оверлейное окно со списком WiFi-сетей, вызываемое ярлыком,
плиткой в шторке или adb-командой. Использует root (Magisk) для подключения к сетям
через `cmd wifi`.

## Сборка

Сборка идёт автоматически на GitHub Actions при пуше в `main`
(`.github/workflows/build.yml`). Готовый `app-debug.apk` появляется во вкладке
**Actions → (последний прогон) → Artifacts → wifi-overlay-debug-apk**.

Собрать вручную (на любой машине с JDK 17 + Android SDK):

```bash
./gradlew assembleDebug   # либо gradle assembleDebug, если нет wrapper-а
```

APK окажется в `app/build/outputs/apk/debug/app-debug.apk`.

## Установка и выдача прав (одной пачкой команд через adb)

```bash
adb install -r app-debug.apk

# Разрешение "поверх других окон" (AppOps, не через pm grant)
adb shell appops set com.iskol.wifioverlay SYSTEM_ALERT_WINDOW allow

# Обычные runtime-разрешения
adb shell pm grant com.iskol.wifioverlay android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.iskol.wifioverlay android.permission.NEARBY_WIFI_DEVICES
adb shell pm grant com.iskol.wifioverlay android.permission.POST_NOTIFICATIONS
```

Если `appops`-команда недоступна из adb-shell без root — то же самое можно сделать
вручную: Настройки → Приложения → WiFi Overlay → "Поверх других окон" → включить
(в приложении есть экран-помощник, который откроет нужный системный экран сам).

## Способы вызова оверлея

1. **Ярлык на рабочем столе** — иконка приложения, `MainActivity` невидима и сразу
   открывает оверлей.
2. **Плитка в шторке** — добавь плитку "WiFi" через редактирование быстрых настроек.
3. **adb/shell** (в том числе из Magisk/Termux-скриптов):
   ```bash
   adb shell am broadcast -a com.iskol.wifioverlay.SHOW_OVERLAY -p com.iskol.wifioverlay
   adb shell am broadcast -a com.iskol.wifioverlay.HIDE_OVERLAY -p com.iskol.wifioverlay
   ```

## Важный риск: `cmd wifi` на HyperOS

Подключение к сетям реализовано через root-shell команды `cmd wifi connect-network`,
`cmd wifi list-networks`, `cmd wifi status` — это часть AOSP `WifiShellCommand`
(появилась в Android 11). На HyperOS/MIUI прошивках Xiaomi иногда режет или меняет
поведение системных shell-команд.

**Если после сборки подключение не работает** — пришли мне вывод диагностических команд,
выполненных на телефоне через root-shell (например, Termux + `su`, или `adb shell` при
включённой root-отладке):

```bash
su -c "cmd wifi status"
su -c "cmd wifi list-networks"
su -c "cmd wifi connect-network \"ИмяСети\" wpa2 \"пароль\""
```

По их выводу я пойму, работает ли команда на твоей прошивке как в чистом AOSP,
и если нет — перепишу `RootWifiControl.kt` под альтернативу (например, прямая работа
с `wpa_supplicant`/`WifiConfigStore.xml` или вызов `ConnectivityManager` от имени
system-процесса через root).

## Структура проекта

- `MainActivity` — невидимая точка входа для ярлыка.
- `PermissionSetupActivity` — экран выдачи разрешений (оверлей + скан).
- `OverlayService` — foreground-сервис, рисует оверлейное окно поверх экрана.
- `WifiTileService` — плитка в шторке.
- `ShowOverlayReceiver` — приём adb/shell команд.
- `RootWifiControl` — вся работа с WiFi через `su -c "cmd wifi ..."`.
