# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

**This is the ZenXii Teacher (staff) app — one of five surfaces of the ZenXii ERP.** Full
cross-system architecture, contracts and deploy rules live in
`/Users/yuggi/AndroidStudioProjects/CLAUDE.md`. Read it before changing anything that touches
Firestore, push, or RBAC — most changes here need a matching change in the admin panel
(`~/Desktop/Zennxii_adminPanel`) or the Parent app.

Despite the name, this is the app for **all staff**, not just teachers. Non-teaching roles get their
modules through RBAC.

## Commands

```bash
./gradlew assembleDebug
./gradlew installDebug
./gradlew :app:testDebugUnitTest                                   # 4 JVM tests, green
./gradlew :app:testDebugUnitTest --tests "*HomeworkDateLogicTest"
./gradlew bundleRelease                                            # needs keystore.properties (gitignored)
```
Gradle 8.2 · JVM 17 · minSdk 24 · target/compileSdk 35 · Compose BOM 2024.02 · Hilt 2.50 + KSP.
`BASE_URL` = `https://www.zenxii.com/` (**host root** — `ApiService` uses relative paths, `AuthApi`
uses leading-slash paths; the legacy `/Grader/school/` and `/ZenX/school/` subpaths 404 here, unlike
in the Parent app).

## Layering

`data/firebase` (`FirestoreService`, `FirebaseAuthManager`) → `data/repository/firestore/*` (25 repos,
one per module) → `ui/<module>/` (screen + ViewModel). Hilt wiring is all in `di/AppModule.kt`;
REST goes through `data/remote/{ApiService, AuthApi, AuthInterceptor}`.

- `util/Constants.kt` — **the** source of collection names (`object Firestore`). Never inline a
  collection string. `object Firebase` holds legacy RTDB paths; prefer Firestore for new work.
- `util/ModuleGate.kt` + `util/RoleHelper.kt` — capability gating for navigation. `ModuleGate`
  **fails open** when capabilities haven't loaded, so it's UI only; Firestore rules are the real
  boundary. Self-service routes (own payslip/leave/attendance, profile, dashboard, search) are never
  gated.
- `service/FCMService.kt` + `util/DeepLinkBridge.kt` — push receipt and deep links. The app never
  sends push; it consumes what the Cloud Function dispatched from `pushRequests`.
- `data/model/firestore/` (70 files) mirrors the server document shapes — a field rename here is a
  cross-system contract change.
- Media uploads go through the dedicated uploaders (`StoryMediaUploader`, `GalleryMediaUploader`,
  `HomeworkAttachmentUploader`) and land under `schools/{schoolId}/...` in Storage.

## Local rules

- **Dialogs, sheets, forms and menus must scroll and fit** small screens and landscape: height cap,
  `verticalScroll` on the body, sticky footer, `imePadding`. Clipping is the most repeated UI bug in
  this app — check it on every new dialog.
- Timetable times are 12-hour strings (`"10:45AM"`); parse AM/PM.
- Login is a synthetic email `{userId.lowercase()}@schoolsync.app`; `schoolId` comes from the ID-token
  claims (`school_id` with a `schoolId` fallback), never from user input. A claims change needs a
  re-login to take effect.
- New Firestore queries usually need a composite index — the index lives in the panel repo
  (`firebase-rules/firestore.indexes.json`) and must be deployed **before** the app ships.
- Don't commit, push, or build a release without asking. Leave work UNCOMMITTED and say so.
