# AM Phone for Android

Companion app for the **AM Phone** FiveM resource. Shows the in-game phone on your real phone:
messages, contacts, calls (notifications), bank, garages and properties – even when your character is offline.

*Приложение към FiveM телефона **AM Phone**: съобщения, контакти, обаждания (известия), банка, гаражи и имоти
на истинския ти телефон – дори когато героят не е в играта.*

## Install / Инсталиране

1. Download **AMPhone.apk** from [Releases](../../releases/latest) / Изтегли **AMPhone.apk** от Releases.
2. Allow "Install unknown apps" / Разреши „Инсталиране от неизвестни източници“.
3. In the game: Phone → Settings → Real phone. Enter the address and the 6-digit code in the app.
   В играта: Телефон → Настройки → Истински телефон. Въведи адреса и 6-цифрения код в приложението.

You can save several servers. Notifications while the app is closed use a background connection
(you can turn it off in the app). On Samsung/Xiaomi phones, turn off battery restrictions for AM Phone.

## Build

GitHub Actions builds `AMPhone.apk` on every push to `main` and publishes it as a release.
For a stable signing key add repository secrets `AMPHONE_KEYSTORE_B64` (base64 of the .jks) and
`AMPHONE_KEYSTORE_PASSWORD` (alias `amphone`).

Local: `cd android && gradle assembleRelease` (Android SDK 34, JDK 17).

© 2026 AM. All rights reserved.
