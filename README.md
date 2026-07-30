# PIM — Please Inconvenience Me

Fight phone addiction by making distracting apps inconvenient.

Pick the apps you want to use less. When you open one, PIM stops you first: do a
small task — a bit of arithmetic, retype a string, tap a sequence of dots — and
you earn a timed session. When the session runs out, you decide again whether
you still need it. No shame, no lectures, just enough friction to turn autopilot
scrolling back into a conscious choice.

**Website:** [PleaseInconvenienceMe.com](https://pleaseinconvenienceme.com)
**Google Play:** [PIM: App Blocker & Screen Time](https://play.google.com/store/apps/details?id=com.pleaseinconvenienceme.pim)

## Features

- Three task types — math, typing, tapping — with difficulty levels, including a
  fully custom difficulty
- Per-app session lengths and per-app setting overrides
- Optional delay before the task appears, which can grow for repeated
  back-to-back sessions
- Session countdown overlay while you use a restricted app
- Optional global lock, so you can't quietly remove your own restrictions in a
  weak moment
- Seven-day usage stats per restricted app
- Dark mode

## Flavors

- `fdroid` — fully free, every feature, no billing code, shows a donate prompt
- `googlePlay` — the Play Store build (7-day trial, one-time purchase)

The About screen names which one you're running.

## Building

Needs a recent Android Studio / Android SDK (compileSdk 36, minSdk 26).

```
./gradlew assembleFdroidDebug
```

The APK lands in `app/build/outputs/apk/fdroid/debug/`.

## Privacy

PIM collects nothing. No analytics, no accounts, no network calls except Google
Play billing in the `googlePlay` flavor. Everything it knows — your restricted
apps, session history, settings — stays on the device.

## Contributing

This repository is a snapshot mirror: development happens in a private repo and
each release lands here as a single commit. The Play Store release and this
repository can therefore be a version apart for a while.

Bug reports and feature ideas in the issue tracker are genuinely read and
welcome. Pull requests generally can't be merged, since the code doesn't flow
back through this repo — please open an issue instead.

## License

[GPL-3.0](LICENSE). The bundled fonts are under the SIL Open Font License:
[Jost](OFL-Jost.txt) and [Playfair Display](OFL-PlayfairDisplay.txt).
