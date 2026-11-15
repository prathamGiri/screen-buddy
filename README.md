# Screen Buddy

A pixel companion that lives on top of your Android screen. Today that is Billu Rani, a ginger
tabby; dogs and bunnies are planned.

She wanders along the bottom edge, now and then grooms herself or scratches behind her ear,
hops onto the keyboard while you type, and when you leave her alone she yawns, naps, and wakes
with a stretch. Tap her to pet her,
drag her to carry her, and let go mid-swipe to toss her. Every hour or two of screen time a
ball of wool rolls in; she plays with it for a minute or two, then bats it off the screen.

Every behaviour can be switched off in the app, and you can give her any name you like.

## Install

Open `app-debug.apk` on the phone (it is built by every run under the repository's **Actions**
tab, as `screen-buddy-apk`), open Screen Buddy, tap **Invite Billu Rani onto your screen**, and
allow it to display over other apps.

Screen Buddy uses only the "Display over other apps" permission and a quiet notification that
keeps it running. It has no internet permission and never reads what is on your screen; it
only learns that the screen was touched, not where.

## Google Play

`docs/play-store/LAUNCH.md` walks through the launch: privacy policy hosting, the upload key,
every Play Console form with answers, the store listing and the closed test. The privacy policy
is `docs/privacy-policy.html`, served at https://prathamgiri.com/screen-buddy/privacy-policy.html.

## Build

```
./gradlew :app:assembleDebug     # test APK; needs the Android SDK
./gradlew :app:bundleRelease     # Play bundle; signed when the UPLOAD_* variables are set
./gradlew -PnoAndroid :core:test # behaviour and sprite tests, no SDK needed
```

## Layout

- `core/` – plain Kotlin, with unit tests:
  - `Sprites.kt` – every frame, drawn in code. The sprite test writes a preview sheet to
    `core/build/sprites.png`.
  - `CatBrain.kt` – behaviour and physics. She follows a routine: mostly sitting and
    strolling, an occasional groom or ear scratch, a yawn before every nap, and a stretch only
    when she wakes from a nap by herself.
  - `Behavior.kt` – the behaviours the user can switch off.
  - `Species.kt` – the companions the app knows about; only the cat is available so far.
- `app/` – Android:
  - `OverlayService` – foreground service holding the overlay window.
  - `CatHost` – overlay windows and the frame loop. `SpriteView` draws a sprite and handles
    touches. `KeyboardWatcher` finds the top of the on-screen keyboard.
  - `MainActivity` – the settings screen, with a live preview (`PreviewView`) and the house
    style (`Lux`).
