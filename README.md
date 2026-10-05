# Focus Taper (Android)

Pomodoro timer with two tapering plans for a dwindling attention span:

- **Full day:** 60/10 · 50/10 · 40/10 · 30/10 · 20/10 · 10 / long break
- **Easy start:** the same, starting at 50

It also has the classic plans (25/5 ×4, 50/10 ×3, 90/20 ×2) and a custom plan (work, break, repeat ×N).

## How it works

| Piece | File | Role |
|---|---|---|
| Plans + state machine | `Plans.kt`, `Engine.kt` | Pure Kotlin, no Android types, unit-tested (`EngineTest.kt`). |
| Persistence | `Store.kt` | SharedPreferences; the one source of truth. |
| Alarm + notification | `Notifier.kt` | `AlarmManager.setAlarmClock` at the block's end time. Ongoing notification uses the system Chronometer in countdown mode, so the countdown ticks in the shade and lock screen without the app running. |
| Receiver | `TimerReceiver.kt` | Block end, Pause/Next buttons, re-arm after reboot/update. |
| UI | `MainActivity.kt`, `TimelineView.kt` | Framework views only (no AndroidX, no Compose). |

While running, the app stores the block's **end time**, not a counter. Remaining time is always `endAt − now`, so it can't drift, and after a reboot or a killed process it catches up by chaining from the scheduled end.

No foreground service is needed: the exact alarm wakes the receiver, and the notification's countdown is drawn by the system.

## Build

**GitHub Actions:** push to `main` on GitHub. The workflow runs the tests, builds the APK, uploads it as an artifact and publishes it as the `latest` release.

**Locally** (JDK 17 + Android SDK with platform 34):

```sh
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # or set ANDROID_HOME
./gradlew testDebugUnitTest assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without signing secrets, the release APK is signed with the debug key. On CI that key is new on every run, so each update needs an uninstall first. To avoid that, make one key and add it as secrets:

```sh
keytool -genkeypair -v -keystore release.jks -alias focustaper -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 release.jks   # → secret KEYSTORE_B64; also KEYSTORE_PASSWORD, KEY_ALIAS=focustaper, KEY_PASSWORD
```

## Permissions

- **Notifications:** asked for on first launch (Android 13+).
- **Exact alarms:** `USE_EXACT_ALARM` is granted at install. If it gets revoked, the app shows a warning with a button to settings.
- **Run at startup:** re-arms the alarm after a reboot.

Work and break alerts use separate notification channels, so you can give them different sounds under *Notification sounds…* in the app.
