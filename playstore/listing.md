# DiaRoutine — Google Play listing

Everything to paste into Play Console, in the order the Console asks for it.
Graphics are in this folder: `icon-512.png`, `feature-graphic.png` and `screenshots/`.

---

## Main store listing

**App name** (30 max)

```
DiaRoutine: Diabetes Planner
```

**Short description** (80 max)

```
Medication reminders, glucose log and one daily timeline for your diabetes day.
```

**Full description** (4,000 max)

```
Managing diabetes is more than checking numbers. It's remembering the morning tablet, the evening injection, the fasting check, the walk, the water, the refill and the next clinic visit — every day. DiaRoutine brings it all into one calm daily timeline, so you can see what's done, what's due and what's next at a glance.

YOUR DAY IN ONE TIMELINE
• Medication doses, glucose checks, your daily walk and appointments, in time order
• See what's done, due now, overdue or missed, and catch up with one tap
• Look back at past days and plan ahead with the week strip

MEDICATION REMINDERS
• Add each medication as prescribed: name, strength, form, dose and times
• A reminder when each dose is due, with "Mark as taken" right in the notification
• Log as-needed medication whenever you take it
• Track your supply and get a reminder before you run out

GLUCOSE LOG
• Log readings in mg/dL or mmol/L in seconds, with meal context and notes
• See every reading against the target range your care team set
• Planned daily checks with reminders
• 7-day trend, average and time in range

WATER, STEPS AND ACTIVITY
• Tap to add a glass of water
• Record steps and active minutes against your daily goals

APPOINTMENTS
• Keep check-ups, lab tests and eye or foot exams in one place
• Reminders a day or a few hours before

WEEKLY SUMMARY
• How consistently you took your medication, day by day
• Current and longest streaks
• Glucose time in range, lows and highs, and habit trends

CAREGIVER MODE
• Send a plain-text weekly summary to a family member or carer by message, email or any app you choose
• Nothing is ever sent automatically

PRIVATE BY DESIGN
• No account, no ads, no tracking
• Your health information stays on your phone
• Optional app lock with your fingerprint, face or screen lock

DIAROUTINE PREMIUM — FREE FOR 3 MONTHS
• Encrypted weekly backup to your own Google Drive, protected by a recovery key only you hold
• Missed-dose follow-ups, and a 10-minute snooze on every dose reminder
• 30- and 90-day insights for adherence, glucose and habits
Eligible new subscribers get 3 months free, then the subscription renews automatically. Cancel anytime in Google Play.

A short welcome and guided tour show you around. Comfortable in light and dark mode, with large touch targets and screen-reader labels throughout.

IMPORTANT
DiaRoutine is an organizer for the routine your healthcare team gives you. It is not a medical device and does not provide medical advice, diagnosis or treatment. Always follow your healthcare professional's instructions. In an emergency, contact your local emergency services.
```

**Graphics**

| Asset | File | Spec |
|---|---|---|
| App icon | `icon-512.png` | 512 × 512 PNG |
| Feature graphic | `feature-graphic.png` | 1024 × 500 PNG |
| Phone screenshots | `screenshots/phone/01…08` | 8 × 1080 × 1920 PNG (9:16) |
| 7-inch tablet screenshots | `screenshots/tablet-7in/01…08` | 8 × 1200 × 1920 PNG (10:16) |
| 10-inch tablet screenshots | `screenshots/tablet-10in/01…08` | 8 × 1600 × 2560 PNG (10:16) |

Upload each set in file order. On tablets the app shows as a centred column (max 720 dp),
which is what the tablet screenshots show.

---

## Store settings

| Field | Value |
|---|---|
| App or game | App |
| Category | Medical |
| Tags (pick up to 5 offered) | Diabetes, Medication reminder, Health tracker, Blood sugar, Daily planner |
| Contact email | coolnjeru@gmail.com |
| Website | https://njerucyrus.github.io/diaroutine/ |
| Phone | (optional — leave empty) |

---

## App content (Policy → App content)

**Privacy policy URL**

```
https://njerucyrus.github.io/diaroutine/privacy.html
```

**App access** — *All functionality is available without any access restrictions* (no login).

**Ads** — *No, my app does not contain ads.*

**Content rating** (IARC questionnaire) — Category: *All other app types*. Answer **Yes** to *digital purchases*
(the Premium subscription) and **No** to everything else (violence, sexuality, language, controlled substances,
gambling, user interaction/communication, sharing location). Prescription-medication reminders are not
"controlled substance" content. Expected result: Everyone / PEGI 3, with "In-app purchases".

**Target audience** — *18 and over*. (Selecting under-13 brings in the Families policy; 13–17 can be added later.)
Not designed to appeal to children.

**Data safety**

Data leaves the phone only in the optional, end-to-end encrypted Google Drive backup (Premium). Declare it — the
conservative answer, and always accepted:

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** |
| Is all of the user data collected by your app encrypted in transit? | **Yes** (HTTPS, and encrypted on the device before upload) |
| Do you provide a way for users to request that their data is deleted? | **Yes** — *Delete backup* in the app, or Google Drive → Manage apps |

| Data type | Collected | Shared | Optional? | Purpose |
|---|---|---|---|---|
| Personal info → Name | Yes | No | Yes (only with backup on) | App functionality (backup) |
| Personal info → Other info (caregiver name and contact) | Yes | No | Yes | App functionality (backup) |
| Health and fitness → Health info (medications, glucose, appointments) | Yes | No | Yes | App functionality (backup) |
| Health and fitness → Fitness info (steps, active minutes) | Yes | No | Yes | App functionality (backup) |

Everything else (location, contacts, photos, device IDs, app activity, crash logs): **not collected**. Purchases are
handled entirely by Google Play. Sharing a summary is user-initiated through the Android share sheet, which Play does
not count as sharing. (Play's guidance can exempt end-to-end encrypted data from disclosure; declaring it as above
is the safe choice.)

**Health apps declaration** — select:
- Medication and treatment management
- Disease and condition management
- Activity and fitness (steps and active minutes)

Is the app a regulated medical device? **No.** It does not diagnose, treat or recommend doses; it reminds and logs
what the user enters. (The disclaimer is shown during onboarding, in Profile → About, and in the Terms of Use.)

**Government app / Financial features / News app** — No / None / No.

**Permissions** — No special declarations needed:
- `POST_NOTIFICATIONS` — reminders
- `SCHEDULE_EXACT_ALARM` — optional on-time medication reminders, granted by the user in system settings (core functionality)
- `RECEIVE_BOOT_COMPLETED` — re-arm reminders after a restart
- `USE_BIOMETRIC` / `USE_FINGERPRINT` (added by androidx.biometric) — optional app lock; the system verifies, the app only gets success or failure
- `INTERNET` — only for the optional encrypted Google Drive backup; `com.android.vending.BILLING` (added by Play Billing) — the Premium subscription
- `WAKE_LOCK`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE` — added by WorkManager for the weekly backup job; no foreground-service type is used, so no declaration is needed

---

## Release

**Release name**: `1.0.0 (1)`

**Release notes** (500 max, `en-US`)

```
First release of DiaRoutine:
• A daily timeline of doses, glucose checks, walks and appointments
• Medication reminders with "Mark as taken"
• Glucose log (mg/dL or mmol/L), water and activity tracking
• Appointment and refill reminders, weekly summary, caregiver sharing
• App lock with fingerprint, face or screen lock
• Premium, free for 3 months: encrypted Google Drive backup, missed-dose follow-ups, 30- and 90-day insights
```

**Upload**: `androidApp/build/outputs/bundle/release/androidApp-release.aab`
(enrol in Play App Signing when prompted — Google then holds the app signing key and your upload key can be reset if lost).

Recommended path: Internal testing → Closed testing (new personal developer accounts must run a closed test with
at least 12 testers for 14 days before applying for production) → Production.
