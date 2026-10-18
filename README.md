# صلاة غزة · Gaza Prayer

Prayer times for the Gaza Strip from the official 365-day table, with the adhan,
a home-screen widget and dark mode. Offline, no location, no trackers. Arabic UI.

تطبيق أوقات الصلاة لقطاع غزة من الجدول الرسمي، مع الأذان والويدجت والوضع الداكن.
يعمل دون إنترنت ودون تحديد الموقع، ولا يجمع أي بيانات.

## Features

- Official Gaza table (`data/gaza_prayer_times_year.csv`, UTC+2) embedded as
  `GazaTable.java`; Egyptian-method calculation (Fajr 19.5°, Isha 17.5°, Maghrib +3 min)
  only for 29 February.
- Summer/winter time from the `Asia/Gaza` tz database, with a manual UTC+2 / UTC+3 override.
- First adhan (Fajr − 30 min, − 60 in Ramadan) and North America Fajr (ISNA 15°).
- Per event: full adhan (foreground service, alarm stream, stop button), short notification, or off.
- Widget with a self-running countdown; colours, transparency and text size are configurable.
- Per-prayer minute offsets, Hijri date correction, light/dark theme.
- After Isha the list shows tomorrow's times.

## Build

```sh
./gradlew assembleRelease
```

Requires JDK 17 and the Android SDK (compileSdk 34). Plain Java, no third-party libraries.

If the CSV changes, regenerate the table:

```sh
python3 tools/gen_table.py data/gaza_prayer_times_year.csv app/src/main/java/ps/gaza/prayer/GazaTable.java
```

## Releases

Pushing a tag `vX.Y` builds the APK on GitHub Actions and attaches it to a release.
To sign it, add the repository secrets `KEYSTORE_B64` (base64 PKCS12) and `KEYSTORE_PASSWORD`.

## Author

Adel Hajjaj · https://github.com/adelomer12

## License

GPL-3.0-or-later. See `LICENSE`.
