Verge XVR, a Verge Android/Meta/Meta Wearables wallet app!
============================

- Requires Orbot to be running in the background: https://gitlab.com/guardianproject/orbot
- Connects to Electrum or ElectrumX servers over Tor, yay decentralized!
- App starts with a SOCKS5 proxy on `127.0.0.1:9050`
- Current Android API level in use: 36
- Unstoppable Domains resolution is supported for XVG sends via `crypto.XVG.address`
- Includes a dedicated `metaRelease` build variant for Meta / Horizon OS packaging without changing the standard Android release path
- Includes a separate `wearablesMeta` app module for future Meta AI glasses companion work without mixing that code into the Quest / Horizon headset app

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

Command line builds:

- Standard unsigned release APK:
  `./gradlew :wallet:assembleRelease`
- Meta unsigned APK:
  `./gradlew :wallet:assembleMetaRelease`

Generated APKs:

- Standard Android:
  `wallet/build/outputs/apk/release/wallet-release-unsigned.apk`
- Meta:
  `wallet/build/outputs/apk/metaRelease/wallet-metaRelease-unsigned.apk`

## Meta

The repo now has a separate `metaRelease` build variant so Meta packaging can evolve independently from the normal Android / Google Play path.

Use this when you want to:

- keep Meta-only manifest or resource changes out of `src/main`
- generate a Meta-targeted unsigned APK for sideloading or store submission prep
- preserve the existing `debug` and `release` variants for standard Android distribution
- keep room for broader Meta device branding beyond Quest-specific naming

Meta-only source set:

- `wallet/src/metaRelease/AndroidManifest.xml`
- `wallet/src/metaRelease/res/...`

For local Meta headset testing over USB:

- enable Developer Mode on the Quest
- connect the headset with a data-capable USB cable
- authorize USB debugging in-headset
- install a signed APK with `adb install -r <apk>`

If you are using Android Studio's signing wizard, select the `metaRelease` variant when generating a signed APK for Meta.

## Meta Wearables

The repo also includes a separate `wearablesMeta` Android app module intended for Meta AI glasses companion integrations.

Use this module when you want to:

- build Ray-Ban Meta / Oakley Meta companion functionality without coupling it to the Quest headset UI
- share wallet and account logic through `:core`
- keep Horizon OS windowing code inside `:wallet` and wearables session code inside a dedicated app target

Current module path:

- `wearablesMeta/`

Example command line build:

- `./gradlew :wearablesMeta:assembleDebug`

## GitHub Actions

The Android workflow now publishes two unsigned APK artifacts on CI:

- `tordroid` for the normal Android release APK
- `tordroid-meta` for the `metaRelease` APK

Note:
If you are attempting to build on a Lollipop emulator, use `Android 5.x armeabi-v7a`.
It will not build on an x86/x86_64 emulator there.

Original fork by Coinomi 2017
overhauled in 2017 to present by [justinvforvendetta](https://github.com/justinvforvendetta)



.
