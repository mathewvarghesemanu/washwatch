# Google Play listing: WashWatch v1.1.0

Package `space.mathewvarghese.washwatch`. One app, two modes (Monitor and Alerts). The separate WashWatch Alerts app (`space.mathewvarghese.washwatchalerts`) is no longer updated and has no assets here.

## Graphics

| Asset | File | Size |
|---|---|---|
| App icon | `washwatch-icon-512.png` | 512 × 512 |
| Feature graphic | `washwatch-feature.png` | 1024 × 500 |
| Phone screenshots (upload in this order) | see below | 1080 × 1920 |

1. `shot-mode-picker.png`: first launch, "Where is this phone?"
2. `shot-washwatch-home.png`: Monitor mode, ready
3. `shot-washwatch-running.png`: Monitor mode, cycle running
4. `shot-washwatch-settings.png`: Monitor settings, sensitivity and pairing QR code
5. `shot-alerts-pair.png`: Alerts mode, pairing
6. `shot-alerts-listening.png`: Alerts mode, running-cycle timer and history
7. `shot-alerts-notifications.png`: "done" and "started" notifications
8. `shot-alerts-settings.png`: Alerts settings, sound / vibrate / alarm

`.preview-strip.png` is a contact sheet of all eight for review. It is not uploaded.

## Text

**App name** (30 max): WashWatch

**Short description** (80 max):
Stick a spare phone on the washer. Your phone buzzes when the laundry is done.

**Full description:**

Know when your laundry is done, without walking downstairs to check.

WashWatch turns a spare Android phone into a laundry sensor. Put it on your washing machine or dryer, and your everyday phone buzzes when the cycle finishes. No smart plug, no account, no extra hardware.

Install WashWatch on both phones and pick a mode on each:

• On the machine (Monitor mode): feels the washer or dryer vibrate, works out when a cycle starts and finishes, and sends an alert.
• With me (Alerts mode): receives those alerts and shows them as notifications, wherever you are.

Features
• Washing machine and dryer profiles, each with its own sensitivity and quiet period
• One-tap calibration with the machine off
• Pair the two phones by scanning a QR code, or type the pairing code
• Loud "done" alerts and quiet "started" alerts. Pick the sound, vibration, or an alarm that keeps ringing until you dismiss it
• A running-cycle timer that shows roughly when this load will finish, based on the last one
• History of recent cycles with start time, finish time and duration
• Catches up on alerts it missed after a dropped connection or a reboot
• Works without Google Play Services
• Light and dark themes

Privacy
No accounts, ads, analytics or tracking. The phones talk through a private, randomly named topic on the ntfy.sh push service. Alerts contain only the appliance name, the event and the cycle duration. Accelerometer readings never leave the phone on the machine.

**Privacy policy URL:** host `PRIVACY.md` (for example the GitHub file link) and paste that URL in the console.

## Foreground service declaration

Both services use `specialUse`. Video: `videos/WashWatch-foreground-services.mp4` (60 s). Upload it as an unlisted YouTube video and paste the link.

What the video shows, in order:
1. 0–20 s, Monitor mode: tap Start. The `VibrationMonitorService` notification ("Waiting for the washing machine to start…") appears in the shade. Tap Stop.
2. 20–33 s: switch to Alerts mode from the menu under the app name and pair by typing the code.
3. 33–60 s, Alerts mode: the `AlertService` notification ("Listening for WashWatch alerts · Connected") is in the shade, and a "Washing Machine done!" alert arrives through it.

Description to paste:

> WashWatch has two user-started foreground services.
> VibrationMonitorService (Monitor mode) reads the accelerometer only while the user has started monitoring a washer or dryer. It detects when the cycle finishes and must keep running with the screen off for the whole cycle, often over an hour.
> AlertService (Alerts mode) keeps a streaming connection open to ntfy.sh so laundry alerts from the user's paired phone arrive within seconds. The app does not use Firebase or Google Play Services, so it cannot rely on FCM high-priority messages. It runs only after the user has paired a phone, and stops when they unpair.
> Both show a persistent notification while running.

## Data safety (unchanged from v1.0)

- Data collected: none. Data shared: none.
- Data in transit is encrypted (HTTPS to ntfy.sh).
- Camera is used only on the device, to scan the pairing QR code.
