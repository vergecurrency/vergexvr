Verge Tor Wallet for Android
============================

- Requires Orbot to be running in the background: https://gitlab.com/guardianproject/orbot
- App starts with a SOCKS5 proxy on `127.0.0.1:9050`
- Current Android API level in use: 36
- Unstoppable Domains resolution is supported for XVG sends via `crypto.XVG.address`

Play Store emulator images sandbox traffic more heavily, so the app's attempts to connect through Orbot can fail there.
This is why Orbot can work on physical devices but fail inside modern AVD images.

Use a `Generic x86_64 - Android (AOSP)` system image, not Google Play.
Recommended AVD images:

- API 30 (Android 11) - AOSP x86_64
- API 29 (Android 10) - AOSP x86_64
- API 28 (Android 9) - AOSP x86_64
- API 23 (Android 6) - AOSP x86

Avoid these AVD image types:

- Google Play
- Google APIs

<p align="left">
  <a href="https://github.com/vergecurrency/tordroid/actions/workflows/android.yml">
  <img src="https://github.com/vergecurrency/tordroid/actions/workflows/android.yml/badge.svg">
  </a>
</p>

This app connects to a Verge Electrum server.
Check the status of XVG Electrum servers here:
https://1209k.com/bitcoin-eye/ele.php?chain=xvg

## Unstoppable Domains

The send screen can resolve supported web3 names such as `sunerok.wallet` to Verge addresses.

- Resolution endpoint:
  `https://api.unstoppabledomains.com/resolve/domains/`
- Verge record key:
  `crypto.XVG.address`
- Requests use the app's configured SOCKS proxy path on `127.0.0.1:9050`

To enable UD resolution during local builds, add this to your local Gradle properties:

```properties
UNSTOPPABLE_DOMAINS_API_TOKEN=your_token_here
```

The app injects that value into `BuildConfig.UNSTOPPABLE_DOMAINS_API_TOKEN` at build time.

## Building the app

Install Android Studio:
https://developer.android.com/sdk/installing/studio.html

Import `tordroid` by selecting `settings.gradle`.
After import finishes, open the SDK Manager.

Install Android 16 / API 36 build tools, while compiling this project with SDK 35 as currently configured.

Make sure JDK 17 is installed before building.
Oracle archive:
https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html

Then go to `File > Project Structure > SDK Location` and point the JDK path to that installation.

To work on ChromeOS Flex, download the image here:
https://dl.google.com/chromeos-flex/images/latest.bin.zip

For device testing:

- On a physical phone, enable Developer Options
- Enable USB Debugging
- Plug the device in
- Press the green play button in Android Studio and choose the target device

Note:
If you are attempting to build on a Lollipop emulator, use `Android 5.x armeabi-v7a`.
It will not build on an x86/x86_64 emulator there.

Original fork by Coinomi 2017
2017 to present by [justinvforvendetta](https://github.com/justinvforvendetta)
