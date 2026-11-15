# Launching Screen Buddy on Google Play

Everything needed to get from here to a production release, in order. Answers for each Play
Console form are written out so they can be pasted in. Policies change, so if a form asks
something not covered here, check the linked Google help page rather than guessing.

## What is already done in the app

| Requirement | Status |
|---|---|
| Target Android 16 (API 36), required for new apps since 31 Aug 2026 | Done |
| Signed release bundle (`.aab`) from CI, shrunk with R8 | Done once the upload key secrets are added (step 2) |
| Every upload has a higher version code | Done: CI uses its run number |
| Privacy policy, linked inside the app | Written (`docs/privacy-policy.html`); goes live in step 1 |
| No Internet permission, no data collection, no ads, no third-party SDKs | Done |
| Foreground service declares its type (`specialUse`) with a reason in the manifest | Done |
| Service does not crash if Android refuses to restart it in the background | Done |

## 1. Put the privacy policy online at prathamgiri.com

The app links to **<https://prathamgiri.com/screen-buddy/privacy-policy.html>**, so the policy
must be served at exactly that address. That link is built into the app; keep it working for as
long as any version of the app is installed, and don't move it without shipping an update.

**If prathamgiri.com is a GitHub Pages user site** (a repo named `prathamGiri.github.io` with a
custom domain), this happens on its own: GitHub serves every project site under your domain.
1. Merge this branch into `main`.
2. In this repo: **Settings → Pages → Deploy from a branch → `main`, folder `/docs` → Save**.
3. `docs/` then appears at `https://prathamgiri.com/screen-buddy/`.

**If the portfolio is hosted anywhere else** (Vercel, Netlify, a web host…), copy
`docs/privacy-policy.html` into your portfolio so it is served at `/screen-buddy/privacy-policy.html`
(for example `public/screen-buddy/privacy-policy.html` in most site generators). Optionally copy
`docs/index.html` to `/screen-buddy/index.html` too, for a small app page.

Either way, open the link on your phone and check it loads without a login, before submitting.

## 2. Create the upload key and give it to CI

Google Play App Signing (on by default for new apps) keeps the real signing key. You sign
uploads with your own **upload key**; if you ever lose it, Google can reset it.

On your computer (needs Java; Android Studio includes it):

```
keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 upload.jks > upload.jks.txt      # macOS: base64 -i upload.jks -o upload.jks.txt
```

Then on GitHub, **Settings → Secrets and variables → Actions → New repository secret**, add:

| Secret | Value |
|---|---|
| `UPLOAD_KEYSTORE_BASE64` | contents of `upload.jks.txt` |
| `UPLOAD_KEYSTORE_PASSWORD` | the keystore password you chose |
| `UPLOAD_KEY_ALIAS` | `upload` |
| `UPLOAD_KEY_PASSWORD` | the key password you chose (often the same) |

Keep `upload.jks` and the passwords somewhere safe (a password manager), never in the repo.
From the next push, each Actions run has a second artifact, **`screen-buddy-play-bundle`**,
containing `app-release.aab`. That is the file you upload to Play.

## 3. Create the app in Play Console

**Home → Create app**: name `Screen Buddy`, default language, **App**, **Free**, accept the
declarations.

## 4. App content (Policy → App content)

**Privacy policy**
`https://prathamgiri.com/screen-buddy/privacy-policy.html`

**App access**
"All functionality is available without any special access." (No login. The app asks for
"Display over other apps", which reviewers can grant themselves.)

**Ads**
"No, my app does not contain ads."

**Content rating** (IARC questionnaire)
Category: *All other app types*. Answer **No** to violence, fear, sexuality, language,
controlled substances, crude humour, gambling, user-to-user interaction, sharing location and
digital purchases. Expect *Everyone* / *PEGI 3*.

**Target audience and content**
Select **13–15, 16–17 and 18+**. Do not select ages under 13: that brings in the Families
policy and its extra review.
"Could your store listing unintentionally appeal to children?" Answer honestly. A cute cat may
lead Google to ask questions. The app already meets the main Families expectations (no data
collection, no ads, no links out except the privacy policy), so this is low risk either way.

**Data safety**
- Does your app collect or share any of the required user data types? **No**.
- That finishes the form: the listing will show "No data collected" and "No data shared".
  (Settings saved on the phone, and Android's own device backup, do not count as collection.)

**Advertising ID**
"Does your app use advertising ID?" **No**.

**Government apps / Financial features / Health / News**
Not applicable: answer No or "My app doesn't provide any…" to each.

**Foreground service permissions** (asked because the app targets Android 14+)
Type: **Special use**. Paste:

> Screen Buddy shows a small animated pet that stays on top of other apps until the user hides
> it. The foreground service keeps the pet's overlay window and its animation running while the
> user uses other apps; without it, Android stops the pet within minutes and it disappears. The
> service starts only when the user opens the app and chooses to show the pet. It shows an
> ongoing notification and stops as soon as the user turns the pet off in the app. It does no
> network or data processing.

Impact if the task were deferred or interrupted: "The pet disappears from the screen, which is
the whole purpose of the app."

Video: record 20–30 seconds on the phone (screen recorder): open Screen Buddy, switch
"On your screen" on, go to the home screen and another app with the cat visible, pull down the
notification, then switch the cat off. Upload to YouTube as **Unlisted** and paste the link.

**Display over other apps (`SYSTEM_ALERT_WINDOW`)**
There is no separate declaration form for it today. If a reviewer asks: "Drawing the pet over
other apps is the app's core and only feature; the overlay windows are small, show only the pet
and its ball, and never cover or read other apps' content."

## 5. Store listing (Grow → Store presence → Main store listing)

**App name** (max 30): `Screen Buddy`

**Short description** (max 80):
`A pixel cat who lives on your screen. She naps, grooms and chases wool.`

**Full description:**

```
Meet Billu Rani, a little pixel cat who lives on top of your screen.

She strolls along the bottom of your phone while you use it, sits and watches, and now and then
licks her paw or scratches behind her ear. Leave her alone for a while and she yawns, curls up
for a nap, and wakes with a long stretch. Every hour or two a ball of wool rolls in, and she
chases and bats it around before knocking it off the screen.

Tap her to pet her. Drag her to carry her somewhere new, or flick her to toss her across the
screen. When you type, she hops up onto the keyboard to keep you company.

Make her yours
• Rename her to anything you like
• Choose her size: petite, classic or grand
• Switch off anything you'd rather she didn't do: the ball of wool, grooming, napping,
  wandering, keyboard visits and more
• Hide her with one switch whenever you want a clear screen

Private by design
Screen Buddy has no internet access, no ads, no accounts and no tracking. It never reads what
is on your screen or what you type.

Screen Buddy needs permission to display over other apps, so she can live on top of them. It
shows a quiet notification while she is on your screen, as Android requires.

Dogs and bunnies are on their way.
```

**Graphics**

| Asset | File |
|---|---|
| App icon, 512×512 PNG | `docs/play-store/icon-512.png` |
| Feature graphic, 1024×500 | `docs/play-store/feature-graphic-1024x500.png` |
| Phone screenshots, 2–8 | Take on the phone (see below) |

Screenshots to take (portrait): the app's home screen; the cat sitting over your home screen;
the cat on the keyboard; the cat asleep; the cat with the ball. Avoid showing other apps'
personal content or notifications in them.

**Category:** Entertainment (or Personalization). **Contact email:** required and shown
publicly. **Website:** `https://prathamgiri.com/screen-buddy/` (or just `https://prathamgiri.com`).
A contact address on your own domain (for example `hello@prathamgiri.com`) looks more
trustworthy than a personal Gmail, if your domain provider offers email forwarding.

## 6. Closed test (personal accounts created after 13 Nov 2023)

Google requires **at least 12 testers opted in for 14 days in a row** before you can apply for
production.

1. **Test and release → Testing → Closed testing → Create track** (or use "Alpha").
2. Testers: add an email list or a Google Group with 12+ people (aim for 15–20, since some
   drop out).
3. Upload `app-release.aab` from the `screen-buddy-play-bundle` artifact, add release notes,
   roll out.
4. Send testers the opt-in link. Each must accept it with the same Google account they use on
   the phone and install from Play.
5. During the 14 days:
   - ask testers to send feedback through the Play listing's private feedback, or to you directly;
   - ship at least one update that acts on feedback;
   - note what you learned and what you changed. You will be asked.

**Production access questions:** answer in your own words. Points worth covering:
- How you recruited testers (friends, family, colleagues, a community).
- What they used: everyday use with the cat on screen, petting and carrying her, renaming,
  turning behaviours on and off, the keyboard and the ball.
- What feedback you got and what changed because of it (for example: timing of her
  behaviours, renaming, size options).
- Why it is ready: stable across phones, no crashes reported, behaviour tuned from feedback.

## 7. Every release after that

1. Raise `versionName` in `app/build.gradle.kts` (for example `1.1`). The version code goes up
   by itself.
2. Push, wait for the Actions run, download `screen-buddy-play-bundle`.
3. Upload to the track, add release notes, roll out.

## Things to keep in mind

- **Phone makers' battery savers** (Xiaomi, Samsung, Oppo, Vivo, Huawei and others) can stop
  background apps. If testers say the cat vanishes, that is the usual cause; an in-app tip to
  allow the app to run in the background would help.
- **Name clashes:** search Play for "Screen Buddy" before launch. If a well-known app already
  uses the name, a small change avoids confusion and trademark complaints.
- **The debug key in the repository** (`app/debug.keystore`) is for test APKs only. Never use it
  for Play; the release bundle is signed with your upload key.
- **Reviewers can be slow** for new accounts: allow a few days for each review.
