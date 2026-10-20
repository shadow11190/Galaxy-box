# Data and platform sources

2026-10-20 CPU detail revision additionally extracts unanimously matching core counts, reference clock-group sizes and architecture labels from the pinned LineageOS Wiki files, and factual CPU specifications from individually cited Qualcomm, Samsung and MediaTek product pages. Detailed curated fields and their URLs are preserved in catalog/cpu-details-curated.tsv; the reviewed MediaTek CPU section and original-page SHA-256 are recorded in mediatek-cpu-detail-review.json. No proprietary DevCheck/AIDA64 matching database is included. Public specifications never replace runtime CPU flags or frequencies.

Galaxy box code and its native navigation/background artwork are original except for the explicitly attributed Apache-2.0 liquid navigation adaptation below. The v0.7.0 device-card brand marks are third-party artwork listed separately below. No code, artwork, marketing-name database, or proprietary sensor matching table from DevCheck or the user-provided Xiaomi toolbox has been included.

## Device-card brand marks · v0.7.0

The 32 transparent marks in `app/src/main/res/drawable/brand_*` and `drawable-nodpi/brand_*` come from [Simple Icons](https://github.com/simple-icons/simple-icons) at commit `c71c69e3a27e4436936b831c72643e6513b9f49b` (CC0-1.0) or individually linked [Wikimedia Commons](https://commons.wikimedia.org/) logo files. `branding/BRAND-ASSETS-0.7.0.tsv` records each source URL and installed resource. Simple Icons paths were converted to Android vector drawables; Commons SVG thumbnails were saved as transparent PNGs. The UI tints marks for contrast without adding a background. Logos and brand names remain trademarks of their owners; their presence does not imply endorsement or affiliation. Unknown device/brand pairs show no mark.

## Samsung marketing names

`app/src/main/assets/samsung-device-names.tsv` contains 3,376 distinct Samsung factual model / device / marketing-name records filtered from Google's publicly available Google Play supported-devices CSV, retrieved 2026-10-19. This is an offline factual lookup, not a live hardware measurement or a complete catalogue of all Samsung devices. The project's MIT code licence does not assert ownership of Google's source data or Samsung product names.

- Source: https://storage.googleapis.com/play_public/supported_devices.csv
- Human-readable source: https://storage.googleapis.com/play_public/supported_devices.html
- Original downloaded CSV SHA-256: b323bd60142dd665a486ea3d7456c3fbcbaf1d2db444b008053366dbc11f5cd4
- Machine-readable provenance: `app/src/main/assets/device-catalog-source.json`

Chip code translations cite Qualcomm or Samsung official specification pages in `ChipNames.kt`. The short name and process fields are product facts, separately marked as reference specifications. They do not override the runtime GPU renderer or infer the chip from the phone's marketing name alone.

### 2026-10-20 local catalogue revision (version remains 0.6.7)

The current Google CSV snapshot hash and date supersede the historical snapshot above; see `app/src/main/assets/device-catalog-source.json` and `hardware-catalog-coverage.json`. All public brands with nonempty brand/model/device/name fields are included. This is not a census of every Android device ever released.

Additional processor and exact-model SoC facts are derived from LineageOS Wiki, fixed commit `56a157866af6232edf646ae0f3e1b300e509c8e5`, and individually cited vendor specification / Samsung Security Target documents. The Wiki's MIT copyright and permission notice (Copyright 2016 Tom Johnson) is reproduced unmodified in `assets/licenses/LineageOS-wiki-MIT.txt`, with thanks to LineageOS contributors and links accessible in About. Every imported Wiki fact retains its source filename and commit URL. Device data adaptations consist of extracting name/model/codename/SoC and consistent CPU description fields, preserving regional full models, indexing exact aliases, and retaining ambiguous candidates. Additional reviewed official CPU/process facts and their source references are recorded in catalog/processor-fact-review.json. No Wiki installation instructions or scripts are executed or bundled.

Curated source records are in `catalog/`; generated data and coverage are reproducible with `tools/build_hardware_catalog.py`. Qualcomm, MediaTek, Exynos, XRING, Tensor, Kirin and other names remain their owners' product facts and trademarks. Product specifications are separate from measurements. The consulted PyTorch cpuinfo and Linux source files are research references only: their implementation code is not incorporated in the app.

Thermal reading is independently implemented against the public Linux thermal sysfs ABI: https://docs.kernel.org/driver-api/thermal/sysfs-api.html . No Linux source code is copied.

v0.3.1 also reads the documented hwmon ABI (https://docs.kernel.org/hwmon/sysfs-interface.html). The KGSL temp attribute's milli-Celsius unit is verified against the public Android kernel reference at https://android.googlesource.com/kernel/msm/+/730cf84987cc34f1f0e53cbdd4153a2227563dc7/drivers/gpu/msm/kgsl_pwrctrl.c . No kernel implementation code is copied. The reference does not establish that a particular Samsung firmware exposes that attribute.

Android and AndroidX dependencies retain their respective licences. Samsung, Galaxy, Snapdragon, Exynos, Adreno, and other product names belong to their respective owners; the project is independent and unaffiliated.

## Backdrop 1.0.0 (AndroidLiquidGlass)

The app links unmodified `io.github.kyant0:backdrop:1.0.0` by Kyant, Apache License 2.0. The full licence is included at `app/src/main/assets/licenses/Backdrop-Apache-2.0.txt`. From v0.6.3, `LiquidNavigation.kt` adapts the upstream catalog's LiquidBottomTabs optical layering and DampedDragAnimation interaction approach at tag 1.0.0, commit `eada9a8600142c1bd6a5c5fc143d06f599cdfaef`. Source headers and the in-app `Backdrop-Catalog-NOTICE.txt` identify the adaptation and modifications. This third-party adaptation retains Apache-2.0 terms. Galaxy box colours, native icons, background contours and widget artwork remain original. No upstream demo icons or screenshots are bundled.

- Tagged source: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.0
- Official docs: https://kyant.gitbook.io/backdrop
- Licence: https://github.com/Kyant0/AndroidLiquidGlass/blob/1.0.0/LICENSE
- API compatibility and capture design: GLASS-WIDGETS.md

2026-10-20 navigation revision retains the original v0.6.7 optical panel/glyph
capture, chromatic refraction and magnification, while removing the panel's
solid tint/gradient and reserving unclipped overflow. One independently
implemented motion controller synchronizes direct dragging, interrupted spring
settling and pages. Foreground refresh-rate policy is separate from Backdrop.

Haze is no longer linked in v0.6.0; its old licence file is retained for the historical distribution record.

The Xiaomi toolbox APK and DevCheck 6.60 are inspected only as behavioural references. No executable code, resources, icons or proprietary sensor tables from either APK are redistributed. The screen-protector drawing and temperature pipeline are independently implemented. Public Qualcomm GPUSS name evidence and runtime SoC manufacturer are marked medium confidence; a screenshot does not establish sensor location, root mode, or this app's runtime permissions.

## Apache Commons ZIP reader

`org.apache.commons:commons-compress:1.28.0` reads seekable ZIP / ZIP64 documents. Its resolved runtime dependencies are Commons IO 2.20.0, Lang 3.18.0 and Codec 1.19.0. All are Apache License 2.0; original LICENSE and NOTICE files from their Maven JARs are reproduced in `app/src/main/assets/licenses/Apache-Commons-LICENSE-NOTICE.txt` and accessible in About.

- API: https://commons.apache.org/proper/commons-compress/apidocs/org/apache/commons/compress/archivers/zip/ZipFile.Builder.html
- Project: https://commons.apache.org/proper/commons-compress/

Robolectric 4.16.1 and AndroidX Compose UI test libraries are test-only dependencies, absent from the application runtime APK. Render tests use Android 14 / API 34 native graphics on this Windows host; they do not establish Samsung hardware or launcher behaviour.


## Shizuku API / Provider 13.1.5

固定依賴 dev.rikka.shizuku:api:13.1.5 與 provider:13.1.5，MIT License。上游：https://github.com/RikkaApps/Shizuku-API 。授權原文隨 App assets/licenses/Shizuku-API-MIT.txt 一併提供，關於頁可檢視。僅使用官方 SDK 與自行實作的唯讀 UserService。
