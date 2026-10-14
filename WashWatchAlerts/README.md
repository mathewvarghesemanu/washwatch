# WashWatch Alerts

Companion app for [WashWatch](../WashWatch). WashWatch runs on the phone stuck to the washer/dryer (the **host**); WashWatch Alerts runs on your everyday phone (the **client**) and buzzes when a cycle starts or finishes.

## How it works
The host publishes to a private, randomly named [ntfy.sh](https://ntfy.sh) topic (`washwatch-…`). This app subscribes to that topic over a long-lived HTTPS stream (`/<topic>/json`) from a foreground service and turns each message into a local notification. No account, no Firebase, no Google Play Services required.

- **Pairing:** scan the QR code the host shows under *Phone notifications → Pair a phone*. Scanning it with the stock camera app also works (the app handles `ntfy://ntfy.sh/washwatch-…` links), or type the pairing code by hand.
- **Missed alerts:** after a dropped connection or reboot the app asks ntfy for everything since the last alert it saw (ntfy.sh keeps messages ~12 hours), and skips duplicates.
- **Priorities:** "done" alerts (priority 4–5) use a high-importance channel; "started" alerts (1–2) a quiet one. Each can be tuned in Android's notification settings.
- **Reliability:** reconnects with backoff (2s → 60s) and immediately when the network returns; restarts after boot/app update. Allow "unrestricted battery" when the app asks so Doze doesn't delay alerts.

## Build
```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Note
ntfy.sh topics are public to anyone who knows the name; the host's random 16-character topic is the only secret. Tap "New code" on the host to rotate it (then re-scan here).
