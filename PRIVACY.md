# Privacy Policy — WashWatch

_Last updated: October 26, 2026_

This policy covers the Android app **WashWatch** (`space.mathewvarghese.washwatch`), published by Mathew Varghese. The app has two modes: Monitor mode for a phone on the washer or dryer, and Alerts mode for your own phone. It also covers the older, separate **WashWatch Alerts** app (`space.mathewvarghese.washwatchalerts`), which is no longer updated.

## Summary

The apps do not collect, store or sell any personal information. There are no accounts, no ads and no analytics or tracking.

## What the apps do with data

**Monitor mode (phone on the machine)**
- Reads the phone's **accelerometer** while you have monitoring switched on, to detect when a washer or dryer starts and stops. Sensor readings are processed on the device only and are never stored or sent anywhere.
- When a cycle starts or finishes, it sends a short alert to the public notification service **ntfy.sh** on a randomly generated topic name. The alert contains only the appliance name (e.g. "Washing Machine"), the event (started / done / not started) and the cycle duration.

**Alerts mode (your phone)**
- Connects to **ntfy.sh** to receive the alerts for the topic you paired with, and shows them as notifications.
- Uses the **camera** only to scan the pairing QR code shown by the phone in Monitor mode. Images are processed on the device and are not saved or transmitted.
- Keeps a short list of recent alerts on the device so it can show history and skip duplicates.

Settings (appliance choice, thresholds, the paired topic name, alert history) are stored only on your device. Uninstalling the app deletes them.

## Third-party service: ntfy.sh

Alerts are relayed through [ntfy.sh](https://ntfy.sh), a public push-notification service. Like any web service, ntfy.sh receives your device's IP address when the apps connect to it, and it briefly caches messages so a disconnected phone can catch up. See the [ntfy.sh privacy policy](https://ntfy.sh/docs/privacy/). Anyone who knows a topic name can read its messages, so the app generates a random 16-character name; you can generate a new one at any time with **New code** in Monitor mode.

## Children

The apps are not directed at children and do not knowingly collect information from anyone.

## Changes

If this policy changes, the updated version will be posted at this address with a new date.

## Contact

Questions: mathewvarghesemanu@gmail.com
