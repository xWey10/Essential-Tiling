# Third-party build components

- Xposed API 82, rovo89 / Tungstwenty: https://api.xposed.info/ and https://github.com/rovo89/XposedBridge. Apache License 2.0. Compile-only API JAR; not packaged into the application. Upstream notice is in `app/libs/NOTICE-Xposed.txt`. JAR SHA-256: `f48c635f1c7469fdec0e00ad2ea0b7a6b2f5b55065784a35b7ca3a84615e8e25`.
- Gradle Wrapper 8.9: https://gradle.org/ — Apache License 2.0. Distribution checksum is pinned in wrapper properties.
- JUnit 4.13.2 and Robolectric 4.14.1 are test-only dependencies retrieved by Gradle; they and their transitive dependencies are not in the APK.

The supplied NothingSystemUI APK, resources and decompiled source files are not redistributed in this project. Hooks refer to the installed implementation by its actual class and member names.


## Material Components and AndroidX

The settings app uses Material Components for Android 1.12.0 (https://github.com/material-components/material-components-android), AppCompat 1.7.0 and their AndroidX dependencies (https://android.googlesource.com/platform/frameworks/support/), under the Apache License 2.0. They are resolved by Gradle from Google Maven. Versions are pinned for compileSdk 35 compatibility. Material widgets run in the settings app; host SystemUI views retain their platform implementations.
