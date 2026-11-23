# Firebase video channel

The phone home feed reads public records from the Realtime Database at `/videos`.
Google Sign-In authenticates with Firebase Authentication; the YouTube device-code
sign-in is not used by this home/upload flow. Startup does not restore YouTube web
session cookies or warm up YouTube's playback token service.

## Firebase setup

- Keep `mobile/google-services.json` local; it is ignored by Git. It must include
  the Android package `io.yash.meetube` and a Web OAuth client.
- In Firebase Authentication, enable the Google provider and add the app's signing
  certificate fingerprints (debug and release) in Firebase project settings.
- Publish [`mobile/database.rules.json`](../../mobile/database.rules.json) as the
  Realtime Database rules. Reads are public; writes require Firebase Auth and the
  record's `ownerId` to match the caller's UID. The app alone cannot enforce this.
- Cloudinary must have the unsigned upload preset `ml_default` enabled for the
  configured cloud. Unsigned preset names and cloud names are public identifiers,
  not credentials. Restrict allowed formats, file size, and upload folder in the
  Cloudinary preset/dashboard.

Video records contain `id`, `title`, `description`, `videoUrl`, `thumbnailUrl`,
`ownerId`, `ownerName`, `ownerPhoto`, and `createdAt`. The app uploads the chosen
gallery video and thumbnail to Cloudinary and stores each returned HTTPS URL in
the record. A failed database write can leave an unreferenced Cloudinary upload;
remove it from Cloudinary if that happens.

## Build identity

The stmobile release and debug builds both use `io.yash.meetube` so the same
Firebase Android client entry is selected. If a debug APK uses the local debug
signing key, Android cannot install it over a release APK signed with another key;
uninstalling the release app would remove its local app data.
