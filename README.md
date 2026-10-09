# G9 Control

**An open-source, account-free app to configure the Legion Gaming Controller G9 on the Lenovo Legion Y700 Gen 3 tablet.**

[Español](README.es.md)

> **Not affiliated with Lenovo.** "Legion" and "Lenovo" are trademarks of Lenovo. This is an independent, unofficial app. It talks to the controller over USB with a protocol reconstructed by studying the official app and testing the controller; it contains no code or assets from the official app.

> **This app is vibe-coded.** Every line of code, the protocol research and this README were written by Claude (Anthropic's AI) through Claude Code. I didn't write the code: I described what I wanted, tested every build on my own tablet and controller, and reported what worked and what didn't.

![Home](docs/screenshots/home.png)

## Why

The official Legion app works, but it requires an account and comes with a launcher and a lot of extras. G9 Control does one thing: configure the controller. No account, no internet access, no ads.

It also does a few things the official app can't:

- **Switches to DS5 mode by software.** The official app sends a mode that leaves the G9 in Xbox mode.
- **Switches mode automatically per game** (X-Input or DS5), with an on-screen guide to get back to Android mode.
- **Records macros with the controller itself**, with a timeline editor, and lets back buttons copy Home or stick diagonals.
- **Profiles** with file backup, **stick drift measurement**, and **full navigation with the controller**.
- **Careful with the controller's memory**: it only writes what changed, after a short pause, and never writes in a loop.

## Features

| | |
|---|---|
| **Lights** | Both light strips and the Home button: static, breathing, rainbow, off; color, speed and brightness. |
| **Sticks and triggers** | Inner and outer dead zones with a live preview, trigger ranges, hair trigger, drift measurement. |
| **Vibration** | Five levels per side, test with the triggers. |
| **Buttons** | Button test, A↔B / X↔Y swap, D-pad diagonal lock, left stick ↔ D-pad swap. |
| **Paddles** | Assign L4, R4, L5, R5 to any button (including Home and stick directions) or to a macro. |
| **Profiles** | Save, apply and back up complete configurations. |
| **Games** | Choose which mode each app opens in; the controller switches when you open it. |
| **Quick settings** | Android, X-Input and DS5 tiles. |
| **Languages** | English and Spanish. |

<p>
<img src="docs/screenshots/lights.png" width="49%"> <img src="docs/screenshots/sticks.png" width="49%">
<img src="docs/screenshots/paddles.png" width="49%"> <img src="docs/screenshots/settings.png" width="49%">
</p>

## Requirements

- Lenovo Legion Y700 Gen 3 with the Legion Gaming Controller G9 (the controller is made for this tablet).
- Tested with controller firmware 0.52 on ZUI 17 (Android 15). Android 10 or later.

## Install

1. Download `G9Control-x.y.z.apk` from [Releases](../../releases).
2. Open it on the tablet. If Android asks, allow installing apps from that source.
3. Open G9 Control. A quick setup guides you through the rest:
   - Dock the tablet in the G9 in **Android mode** (dark blue indicator). If it's in X-Input (green) or DS5 (white), hold **View + Menu** for about 2 seconds.
   - When Android asks for USB access, check **Always** and accept.
   - Optional: turn on the **accessibility service** for automatic switching (see below).
   - Optional: add the **Android / X-Input / DS5** tiles to quick settings.

### About the accessibility service

Automatic switching per game, the View + Menu guide, the quick settings tiles (from X-Input/DS5) and the game panel (L3 + R3) use an accessibility service. It **only detects which app is on screen** (window changes) and sees the controller's buttons, to track View + Menu for the guide, to open and navigate the game panel and, only if you choose to, block buttons while a notice is on screen. From the game panel it can also take a screenshot and perform one gesture: the swipe that opens the tablet's Game Assistant (Lenovo ZUI) from its side tab. It doesn't read screen content, doesn't see what you type, and the app has no internet permission.

If Android shows **"Restricted setting"** when turning it on: Settings → Apps → G9 Control → ⋮ (top right) → **Allow restricted settings**, then try again.

## Limitations

- **No firmware updates.** That's intentional: a failed update can brick the controller. If you ever need one, use the official Legion app once.
- The app can only configure the controller in **Android mode**. In X-Input and DS5 modes the controller has no configuration channel, so switching back to Android is always done with View + Menu (the app guides you).
- In Android mode, games vibrate the tablet, not the controller (firmware behavior).

## Build from source

Android Studio (or just the JDK it ships with):

```
./gradlew assembleRelease
```

Without a `keystore.properties` file the release build is signed with the debug key. Protocol notes and everything that was tested are in [CONTEXTO.md](CONTEXTO.md) (in Spanish).

## License

[GPL-3.0](LICENSE). You can use, study, modify and share it; modified versions must stay open source under the same license.
