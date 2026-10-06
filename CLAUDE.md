# Crux — working rules

Project context, stack and current state are in [HANDOFF.md](HANDOFF.md).

## Branches and commits
- **Always work and commit on `dev`.** Before committing, make sure `dev` is checked out
  (`git switch dev`; create it from `main` if it doesn't exist). Never commit directly to `main`.
- Commit as you go, without asking, and push `dev` to `origin` after committing.
  Pushes to `dev` run the CI workflow (unit tests + release build).
- **Only merge into `main` when the user explicitly says so** (e.g. "release", "merge to main",
  "ship it"). Every push to `main` publishes a GitHub release, so never merge or push to
  `main` on your own initiative.

## Versioning
- The app version lives in [version.properties](version.properties) (`VERSION_NAME=major.minor.patch`).
  `versionCode` is derived from it in [app/build.gradle.kts](app/build.gradle.kts)
  (major * 10000 + minor * 100 + patch, so minor and patch stay below 100). Don't edit either by hand
  in Gradle.
- **Bump the version only as part of a release the user asked for**, never on `dev` commits.
  Default to a patch bump; use minor or major when the user says so, or ask if the change set
  clearly warrants it.

## Release procedure (only when the user says so)
1. On `dev`: make sure the work is committed and `.\crux.cmd test` passes.
2. Bump `VERSION_NAME` in `version.properties` and commit on `dev`: `Bump version to X.Y.Z`.
3. Push `dev`, then merge into `main` and push:
   `git switch main; git pull; git merge --no-ff dev -m "Release vX.Y.Z"; git push origin main; git switch dev`
4. The **Release** workflow ([.github/workflows/release.yml](.github/workflows/release.yml)) runs the
   unit tests, builds a signed release APK and publishes it as release `vX.Y.Z` with the APK
   attached. Check it with `gh run watch` / `gh release view vX.Y.Z` and report the release link.
   If `main` is pushed without a version bump, the workflow publishes a prerelease
   `vX.Y.Z-build.N` instead of overwriting the existing tag.

## Release signing
Release APKs are signed in CI from repository secrets: `CRUX_KEYSTORE_BASE64` (the keystore,
base64-encoded), `CRUX_KEYSTORE_PASSWORD`, `CRUX_KEY_ALIAS`, `CRUX_KEY_PASSWORD`. Locally,
without `CRUX_KEYSTORE_FILE` set, release builds fall back to the debug key. Keep the same
keystore forever: Android refuses to update an app signed with a different key.
