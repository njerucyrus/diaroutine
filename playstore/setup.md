# Setting up Premium and Google Drive backup

Two things only the account owner can do in Google's consoles. The app needs no keys or
client IDs built in: Play finds the subscription by its ID, and Google matches Drive sign-in
by the app's package name and signing certificate.

## 1. The Premium subscription (Google Play Console)

Play lets you create products once a build with the billing permission has been uploaded to
any track (internal testing is enough).

1. **Monetize with Play → Products → Subscriptions → Create subscription**
   - Product ID: `diaroutine_premium` (must match exactly)
   - Name: DiaRoutine Premium
2. **Add two base plans**, both *auto-renewing*:
   - `monthly` — billing period 1 month
   - `yearly` — billing period 1 year

   Set a price on each; Play suggests local prices for every country.
3. **Add the free trial to each base plan** — *Add offer*:
   - Offer ID: `free-trial-3m`
   - Eligibility: *New customer acquisition* → *Never had this subscription*
   - Phases: *Free trial*, duration **3 months**

   Activate the offers and the base plans. Play shows the trial only to people who are eligible,
   and the app's paywall says "Start 3 months free" only when Play offers it.
4. **Setup → License testing**: add the Gmail accounts you test with. They can subscribe with
   test cards, and their renewals and trials run on a much shorter clock.

To try Premium features before any of this exists, use a **debug build**: Profile → DiaRoutine
Premium → *Developer build: simulate Premium*. The switch isn't in release builds.

## 2. Google Drive backup (Google Cloud Console)

1. Create a project at [console.cloud.google.com](https://console.cloud.google.com), e.g. "DiaRoutine".
2. **APIs & Services → Library** → enable **Google Drive API**.
3. **Google Auth Platform → Branding** (the OAuth consent screen):
   - App name: DiaRoutine · Support email: coolnjeru@gmail.com · Logo: `icon-512.png` (resized to 120 px)
   - Home page: `https://njerucyrus.github.io/diaroutine/`
   - Privacy policy: `https://njerucyrus.github.io/diaroutine/privacy.html`
   - Terms of service: `https://njerucyrus.github.io/diaroutine/terms.html`
   - Authorized domain: `njerucyrus.github.io` — verify it in [Google Search Console](https://search.google.com/search-console)
     (the HTML-tag method works for a GitHub Pages site: add the tag to `docs/index.html` and push).
4. **Audience**: *External*. **Data access** → add the scope `https://www.googleapis.com/auth/drive.appdata`
   (the app's hidden folder only — a non-sensitive scope, so no security assessment is needed).
5. **Clients → Create client → Android**, once for each way the app is signed:

   | For | Package name | SHA-1 certificate fingerprint |
   |---|---|---|
   | Debug builds | `ai.macaws.diaroutine.debug` | `4A:4E:17:FA:31:87:67:69:3C:A8:53:2E:CB:42:93:8B:1C:35:71:C8` |
   | Release, signed with the upload key | `ai.macaws.diaroutine` | `84:2E:83:EA:7D:64:E6:40:AA:86:27:C4:9F:6C:F3:E0:4E:E2:E4:C9` |
   | Release, installed from Google Play | `ai.macaws.diaroutine` | Play Console → *Test and release → App integrity → App signing key certificate* → SHA-1 |

   The debug fingerprint is this Mac's debug keystore; another machine has its own.
6. **Audience → Publish app**. While the app is in *Testing*, only the test users you list
   (up to 100) can connect Drive; once published, anyone can.

Until this is done, *Set up backup* says "Google Drive backup isn't set up for this version of the app yet."

## Backup, in short

- The database is exported as JSON, gzipped, and encrypted on the phone with AES-256-GCM. The key is
  derived (HKDF-SHA256) from a random 160-bit **recovery key**, shown once as `XXXX-XXXX-…` (8 groups of 4).
- The recovery key is kept on the phone wrapped by an Android Keystore key, so the weekly job
  (WorkManager, on any network, battery not low) can run unattended.
- The encrypted file replaces the previous one in Drive's `appDataFolder`, tagged with a fingerprint
  of the key so the app can tell whether a backup was made with this phone's key.
- Restore needs the same Google account and the recovery key. Restoring is free; making backups needs Premium.
