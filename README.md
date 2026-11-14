# Screen Buddy

A pixel cat that lives on top of your Android screen. It wanders along the bottom edge, runs
after things you tap, climbs onto the keyboard, naps when you leave it alone, and can be petted,
carried and tossed.

## Install

1. Download `screen-buddy-debug-apk` from the latest run under the repository's **Actions** tab
   (or build it yourself, below) and install `app-debug.apk` on your phone.
2. Open Screen Buddy and tap **Open accessibility settings**, then turn on Screen Buddy.
   - On Android 13+ a sideloaded app is blocked from this setting at first. Open the app's
     **App info** page, tap the three-dot menu, choose **Allow restricted settings**, and retry.

The cat needs the accessibility service for two things: drawing over other apps, and learning
where on the screen you tapped. It uses only the position of taps and the keyboard; it never
reads, stores or sends what is on your screen, and the app has no internet permission.

## Build

```
./gradlew :app:assembleDebug     # needs the Android SDK
./gradlew -PnoAndroid :core:test # behaviour and sprite tests, no SDK needed
```

## Layout

- `core/` – plain Kotlin: the sprites (drawn in code, `Sprites.kt`) and the cat's behaviour and
  physics (`CatBrain.kt`), with unit tests. The sprite test writes a preview sheet to
  `core/build/sprites.png`.
- `app/` – the Android app: `BuddyService` (overlay window, tap positions, frame loop),
  `CatView` (drawing and touch), `MainActivity` (setup and settings).
