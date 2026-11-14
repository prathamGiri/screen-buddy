# Screen Buddy

A pixel cat that lives on top of your Android screen. It wanders along the bottom edge, runs
after things you tap, climbs onto the keyboard, naps when you leave it alone, and can be petted,
carried and tossed. Pull down the notification tray and a ball of wool drops in for it to play
with.

## Two builds

| | Lite | Full |
|---|---|---|
| Installs by opening the APK on the phone | Yes | No – Play Protect blocks it |
| Permission | Display over other apps | Accessibility service |
| Pet, carry, toss, wander, nap | Yes | Yes |
| Runs after things you tap | No – it only knows the screen was touched | Yes |
| Jumps onto the keyboard | On most phones (uses a workaround) | Yes |
| Wool ball | From a button on its notification or in the app | Drops in when the notification tray opens |

Both are built by every run under the repository's **Actions** tab (`screen-buddy-lite-apk`
and `screen-buddy-full-apk`) and can be installed side by side.

## Install

**Lite:** open `app-lite-debug.apk` on the phone, open Screen Buddy Lite, and allow it to
display over other apps.

**Full:** Google Play Protect refuses to install unknown apps that contain an accessibility
service when the APK is opened from a browser, chat or file manager. Install it from a computer
instead, with USB debugging turned on:

```
adb install app-full-debug.apk
```

Then open Screen Buddy and tap **Open accessibility settings**, and turn on Screen Buddy. If
Android says the setting is restricted, open the app's **App info** page, tap the three-dot
menu, choose **Allow restricted settings**, and retry.

The full build uses the accessibility service for two things: drawing over other apps, and
learning where on the screen you tapped. It uses only the position of taps and the keyboard; it
never reads, stores or sends what is on your screen. Neither build has internet permission.

## Build

```
./gradlew :app:assembleDebug     # both builds; needs the Android SDK
./gradlew -PnoAndroid :core:test # behaviour and sprite tests, no SDK needed
```

## Layout

- `core/` – plain Kotlin: the sprites (drawn in code, `Sprites.kt`) and the cat's behaviour and
  physics (`CatBrain.kt`), with unit tests. The sprite test writes a preview sheet to
  `core/build/sprites.png`.
- `app/src/main` – shared Android code: `CatHost` (overlay window and frame loop), `CatView`
  (drawing and touch), `MainActivity` (setup and settings).
- `app/src/full` – `BuddyService`, the accessibility service that reports tap positions.
- `app/src/lite` – `OverlayService`, a foreground service using a plain overlay window.
