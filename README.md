# MirraFit – native Android app (Kotlin, Jetpack Compose)

Workout tracker in a dark navy design with the red → orange → gold Mirra gradient: plan, workout with sets
(kg/reps), personal records (PR), rest timer, history, statistics, backup export/import.
Vibration runs directly through Android (permission `VIBRATE` is declared in the manifest).

## What's new in 2.1

**Design (reworked from scratch)**
- Flat, calm layout: large left-aligned screen titles, grouped panels with dividers instead of nested cards.
- Colors stay (navy, orange, gold, red), but with clear roles: orange = action/active, gold = personal records,
  green = done. The logo gradient is only used for the main button and progress lines.
- "Scoreboard" numbers in Barlow Condensed (workout clock, rest timer, stats, kg/reps fields).
  The font is bundled in `app/src/main/res/font` (SIL Open Font License, see `third_party/`).
- No more hexagon shapes. Every area uses a fitting icon instead: dumbbell (training), trophy (records),
  flame (streak), timer (rest), flag (weekly goal), drag handle (sorting), trend arrows (progress).
- Navigation bar with icon pills, rest timer bar with its remaining time as a line along the top edge.

**Sorting**
- Every exercise in the workout has up and down buttons.
- "Reihenfolge" in the workout header opens a list that can be sorted by drag & drop (hold the handle and drag)
  or with the arrows, plus "Wie im Plan" to restore the plan order.
- The plan editor uses the same drag & drop list (with up/down buttons, tap a row to edit it).

**Workout**
- Set table with a "Vorher" column (last workout's set), large kg/reps fields and a check box per set
  (green = done, gold with trophy = new record).
- Current exercise is marked with an orange edge, finished exercises collapse, the list glides to the next one.
- Quick adjust for the active set (kg ±2.5, reps ±1), keyboard flow kg → reps → Done checks the set.
- Progression hint when all target reps were reached last time.

**Home, history, stats** (from 2.0, redesigned)
- Weekly goal with Mon–Sun overview and streak, next workout with exercise/set/minute figures.
- Plan preview with the last top set, history grouped by week/month, undo for deleting.
- Stats: totals, 8-week chart (volume or count), consistency grid, muscle groups, exercise details with chart.

The data format is unchanged. Older backups and backups from the HTML app import as before.

## APK size

Icons come from `material-icons-extended`. The **release** build runs R8 and keeps only the icons that are
used. Select the *release* variant (Build → Select Build Variant) and run Build → Build APK(s); it is signed with
the debug key, so it installs directly. Debug builds work too but are noticeably larger.

## Build the app

1. Install **Android Studio** (https://developer.android.com/studio).
2. Start Android Studio → **Open** → select this folder (`mirrafit-android`).
3. On first open Android Studio downloads Gradle and Android SDK 35 automatically (takes a few minutes, needs internet).
   Confirm if it asks about an SDK download or "Trust project".
4. **Build → Build Bundle(s) / APK(s) → Build APK(s)**.
   "locate" appears at the bottom right: that's where `app-debug.apk` is.
5. Copy the APK to the phone and install it (allow "install from unknown sources" once).

Installing over version 1.0 keeps all data (same app ID `de.mirrafit.app`, same signing key on the same machine).
If Android refuses the update because of a different signature, export a backup in the old version first,
uninstall, install 2.0 and import the backup.

Directly to a connected phone: enable USB debugging on the phone and press the green ▶ button in Android Studio.

## Notes

- The rest timer uses an Android alarm. Sound and vibration also work with the screen off.
  In battery saver mode Android may delay the end by a few seconds.
- The screen stays on during a workout.
- Data is stored only on the device. Android backup (Google) normally includes it.

## Changing colors

All colors are defined at the top of `app/src/main/java/de/mirrafit/app/Components.kt` (`CBg`, `CCard`, `COrange`, ...).
