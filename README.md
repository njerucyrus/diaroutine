# DiaRoutine

A daily planner for diabetes self-management: medication reminders, glucose
logging, water and activity, appointments and refills, weekly adherence, and a
caregiver share — all on one personalized daily timeline.

Built with Kotlin Multiplatform and Compose Multiplatform. Android ships first;
the code is laid out so iOS needs only its Xcode wrapper (see **iOS** below).

## Build and run

Needs JDK 17 and the Android SDK (`local.properties` with `sdk.dir=…`).

```sh
./gradlew :androidApp:assembleDebug        # APK in androidApp/build/outputs/apk/debug
./gradlew :androidApp:installDebug         # onto a connected device
./gradlew :shared:testAndroidHostTest      # routine logic tests, on the JVM
./gradlew :shared:desktopTest -Pdiaroutine.screenshots   # every screen to PNG, no device needed
```

The screenshot run writes `shared/build/screenshots/` from a realistic week of
sample data — useful for checking UI changes when no device or emulator is free.

## Layout

```text
shared/src/commonMain/kotlin/com/diaroutine/
├── app/          AppContainer (object graph, reminder sync), App + navigation shell
├── core/         time helpers, display formats
├── domain/       models and the routine logic: Timeline, Adherence, GlucoseStats,
│                 ReminderPlanner, WeeklyReport — plain Kotlin, unit tested
├── data/         Room database (entities, DAOs) and repositories
├── platform/     what the OS supplies: ReminderScheduler, PlatformServices
├── ui/           theme (DiaTheme), components, charts
└── features/     today · meds · track · insights · profile · onboarding
androidApp/       Application, MainActivity, alarms + notifications, receivers
```

Screens take immutable UI state and send actions; ViewModels combine repository
flows into that state. Everything is stored locally in Room. Nothing leaves the device unless the person shares a
summary themselves or turns on the encrypted backup.

## First run, tour and app lock

- **Onboarding** (`features/onboarding`): four intro slides, setup (name, units, optional
  example routine), then an app-lock offer and a notification-permission step — each shown
  only when it applies to the phone.
- **App tour** (`ui/tour`): a spotlight walkthrough of the Today screen that runs once after
  onboarding and can be replayed from Profile → About. Screens mark what the tour points at
  with `Modifier.tourTarget(...)`.
- **App lock** (`app/AppLock.kt`): when on, the app asks for fingerprint, face or the screen
  lock on launch and after being away longer than the chosen delay (immediately, 1 or 5
  minutes). The system prompt (`androidx.biometric`) does the verifying; the recents
  thumbnail is hidden while lock is on (Android 13+).

The database is at version 2 (lock and tour settings on the profile), reached from version 1
by a Room auto-migration; schemas are in `shared/schemas/`.

## Premium and backup

DiaRoutine Premium is a Google Play subscription (`diaroutine_premium`, monthly and yearly) with a
3-month free trial. It adds:

- **Encrypted Google Drive backup** (`backup/`, `androidApp/.../backup`): weekly and on demand,
  encrypted on the phone with a recovery key only the person holds. Restoring is always free.
- **Missed-dose follow-ups** (`ReminderPlanner`): reminded again every 15/30/60 minutes, up to twice,
  until a dose is answered — plus a 10-minute snooze on dose notifications.
- **30- and 90-day insights** (`Report`, `ReportPeriod`).

The store and Drive setup steps are in [playstore/setup.md](playstore/setup.md). Debug builds have
a "simulate Premium" switch in the paywall for trying the features before products exist.

## Reminders

`ReminderPlanner` turns the routine into the next 48 hours of reminders (doses,
glucose checks, the planned walk, appointments, refills). Android schedules them
with `AlarmManager`; they're re-planned whenever the routine changes, whenever a
reminder fires, and after a reboot or time change. Dose notifications have a
**Mark as taken** action. With "On-time alerts" allowed in Profile they fire on
the minute; otherwise within a ten-minute window.

## Release (Google Play)

App ID `ai.macaws.diaroutine` — permanent once uploaded. Publisher: Hudutech Solutions.

Release builds are signed with the upload key in `keystore/`, whose passwords are in
`keystore.properties`. **Both are git-ignored — back them up** (a password manager or
encrypted drive). With Play App Signing, Google holds the app signing key, and a lost
upload key can be reset through Play Console support.

```sh
./gradlew :androidApp:bundleRelease        # androidApp/build/outputs/bundle/release/androidApp-release.aab
./gradlew :shared:desktopTest -Pdiaroutine.screenshots --tests '*StoreAssetsTest'   # store graphics → shared/build/playstore
```

Bump `versionCode` (and `versionName`) in `androidApp/build.gradle.kts` for every upload.
Everything to paste into Play Console — listing text, Data safety, content rating, health
declaration, release notes — is in [playstore/listing.md](playstore/listing.md), with the
graphics beside it. Release notes for each version, with the Play "What's new" text, are in
[RELEASE_NOTES.md](RELEASE_NOTES.md); add the same notes to `docs/whats-new.html`.

## Website (GitHub Pages)

`docs/` is the public site: a landing page, release notes, the Privacy Policy and the Terms of Use, served at
https://njerucyrus.github.io/diaroutine/. To publish it, push this repository to
`github.com/njerucyrus/diaroutine` (it must be public for free Pages), then in the repo's
**Settings → Pages** choose *Deploy from a branch*, branch `main`, folder `/docs`.

The app links to these pages (Profile → About, and onboarding); the URLs are in
`shared/.../app/AppLinks.kt`. If the policies change, update their effective dates.

## iOS

The shared module already builds for `iosX64`, `iosArm64` and
`iosSimulatorArm64` (framework `Shared`). Still to add:

- `iosApp/` Xcode project hosting `ComposeUIViewController { App(container) }`
- `iosMain`: a Room builder pointing at the app's documents directory, a
  `UNUserNotificationCenter` `ReminderScheduler`, and `PlatformServices`
  (share sheet, notification settings)

The Compose stack is pinned to the last versions that support the Intel iOS
simulator; see `gradle/libs.versions.toml`.

## Licenses

The Outfit typeface is used under the SIL Open Font License (`licenses/Outfit-OFL.txt`).
