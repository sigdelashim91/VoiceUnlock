# Voice Unlock

Wakes the screen and dismisses a swipe-only lock when it hears the saved voice say the phrase (default: "open phone").

## Build
1. Install Android Studio, File > Open > this folder, wait for Gradle sync.
2. Plug in the phone (USB debugging on) or use Build > Build APK(s), then install the APK.

## Set up on dad's phone (open the app, go top to bottom)
1. Download voice models (needs internet, one time).
2. Give permissions: Microphone, Notifications, Display over other apps, Battery "Don't optimize".
3. Dad says the phrase 5 times. 
4. Start service. A notification "Voice Unlock is listening" stays on.

Tuning: after speaking, the app shows "Last match distance". Set the strictness limit slightly above dad's usual distance and below other people's.

## Limits
- Only works with NO secure lock (None or Swipe). Android never lets apps bypass PIN/pattern/fingerprint.
- Phrase must be English words (small English model). Pick something easy, e.g. "open phone" or "hello phone".
- Listening all the time uses battery (CPU wake lock + mic).
- After a reboot it starts by itself (needs "Display over other apps" allowed, plus Autostart enabled in the phone's own settings on Xiaomi/Oppo/Vivo/Samsung).
- Some brands (Xiaomi, Oppo, Vivo, Samsung) also need "Autostart"/"Unrestricted battery" for the app in system settings.
