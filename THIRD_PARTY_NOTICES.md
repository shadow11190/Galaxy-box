# Data and platform sources

Galaxy box code and native vector artwork are original except for the explicitly attributed Apache-2.0 liquid navigation adaptation below. No code, artwork, marketing-name database, or proprietary sensor matching table from DevCheck or the user-provided Xiaomi toolbox has been included.

## Samsung marketing names

`app/src/main/assets/samsung-device-names.tsv` contains 3,376 distinct Samsung factual model / device / marketing-name records filtered from Google's publicly available Google Play supported-devices CSV, retrieved 2026-10-19. This is an offline factual lookup, not a live hardware measurement or a complete catalogue of all Samsung devices. The project's MIT code licence does not assert ownership of Google's source data or Samsung product names.

- Source: https://storage.googleapis.com/play_public/supported_devices.csv
- Human-readable source: https://storage.googleapis.com/play_public/supported_devices.html
- Original downloaded CSV SHA-256: b323bd60142dd665a486ea3d7456c3fbcbaf1d2db444b008053366dbc11f5cd4
- Machine-readable provenance: `app/src/main/assets/device-catalog-source.json`

Chip code translations cite Qualcomm or Samsung official specification pages in `ChipNames.kt`. The short name and process fields are product facts, separately marked as reference specifications. They do not override the runtime GPU renderer or infer the chip from the phone's marketing name alone.

Thermal reading is independently implemented against the public Linux thermal sysfs ABI: https://docs.kernel.org/driver-api/thermal/sysfs-api.html . No Linux source code is copied.

v0.3.1 also reads the documented hwmon ABI (https://docs.kernel.org/hwmon/sysfs-interface.html). The KGSL temp attribute's milli-Celsius unit is verified against the public Android kernel reference at https://android.googlesource.com/kernel/msm/+/730cf84987cc34f1f0e53cbdd4153a2227563dc7/drivers/gpu/msm/kgsl_pwrctrl.c . No kernel implementation code is copied. The reference does not establish that a particular Samsung firmware exposes that attribute.

Android and AndroidX dependencies retain their respective licences. Samsung, Galaxy, Snapdragon, Exynos, Adreno, and other product names belong to their respective owners; the project is independent and unaffiliated.

## Backdrop 1.0.0 (AndroidLiquidGlass)

The app links unmodified `io.github.kyant0:backdrop:1.0.0` by Kyant, Apache License 2.0. The full licence is included at `app/src/main/assets/licenses/Backdrop-Apache-2.0.txt`. From v0.6.3, `LiquidNavigation.kt` adapts the upstream catalog's LiquidBottomTabs optical layering and DampedDragAnimation interaction approach at tag 1.0.0, commit `eada9a8600142c1bd6a5c5fc143d06f599cdfaef`. Source headers and the in-app `Backdrop-Catalog-NOTICE.txt` identify the adaptation and modifications. This third-party adaptation retains Apache-2.0 terms. Galaxy box colours, native icons, background contours and widget artwork remain original. No upstream demo icons or screenshots are bundled.

- Tagged source: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.0
- Official docs: https://kyant.gitbook.io/backdrop
- Licence: https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.0/LICENSE
- API compatibility and capture design: GLASS-WIDGETS.md

Haze is no longer linked in v0.6.0; its old licence file is retained for the historical distribution record.

The Xiaomi toolbox APK and DevCheck 6.60 are inspected only as behavioural references. No executable code, resources, icons or proprietary sensor tables from either APK are redistributed. The screen-protector drawing and temperature pipeline are independently implemented. Public Qualcomm GPUSS name evidence and runtime SoC manufacturer are marked medium confidence; a screenshot does not establish sensor location, root mode, or this app's runtime permissions.

## Apache Commons ZIP reader

`org.apache.commons:commons-compress:1.28.0` reads seekable ZIP / ZIP64 documents. Its resolved runtime dependencies are Commons IO 2.20.0, Lang 3.18.0 and Codec 1.19.0. All are Apache License 2.0; original LICENSE and NOTICE files from their Maven JARs are reproduced in `app/src/main/assets/licenses/Apache-Commons-LICENSE-NOTICE.txt` and accessible in About.

- API: https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.Builder.html
- Project: https://commons.apache.org/proper/commons-compress/

Robolectric 4.16.1 and AndroidX Compose UI test libraries are test-only dependencies, absent from the application runtime APK. Render tests use Android 14 / API 34 native graphics on this Windows host; they do not establish Samsung hardware or launcher behaviour.


## Shizuku API / Provider 13.1.5

固定依賴 dev.rikka.shizuku:api:13.1.5 與 provider:13.1.5，MIT License。上游：https://github.com/RikkaApps/Shizuku-API 。授權原文隨 App assets/licenses/Shizuku-API-MIT.txt 一併提供，關於頁可檢視。僅使用官方 SDK 與自行實作的唯讀 UserService。
