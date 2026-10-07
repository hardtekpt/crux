# Releasing

## Branches

- **`dev`**: all work lands here. Every push runs CI.
- **`main`**: only moves on a release. Every push to `main` publishes a GitHub release.

## Versioning

The version lives in [`version.properties`](../../version.properties) as `VERSION_NAME=major.minor.patch`.
`versionCode` is derived in `app/build.gradle.kts` as `major * 10000 + minor * 100 + patch`, so minor
and patch stay below 100. Default to a patch bump, and use minor or major for bigger changes.

## Procedure

1. On `dev`: everything committed, and `.\crux test` (or
   `./gradlew spotlessCheck verifyRoborazziDebug lintDebug`) passes.
2. Bump `VERSION_NAME`. Add the release notes as
   `fastlane/metadata/android/en-US/changelogs/<versionCode>.txt`: what changed for the climber, at
   most 500 characters (F-Droid shows it). Update [`CHANGELOG.md`](../../CHANGELOG.md): move
   *Unreleased* under the new version. Commit: `Bump version to X.Y.Z`.
3. Push `dev`, then merge into `main` and tag:

   ```sh
   git switch main && git pull
   git merge --no-ff dev -m "Release vX.Y.Z"
   git tag -a vX.Y.Z -m "Crux vX.Y.Z"
   git push --atomic origin main vX.Y.Z
   git switch dev
   ```

4. The Release workflow runs the device tests, checks, builds and signs the APK, verifies the
   signing certificate, and publishes release `vX.Y.Z` with:
   - `crux-vX.Y.Z.apk`
   - `crux-vX.Y.Z.apk.sha256`
   - `crux-vX.Y.Z-mapping.txt` (R8 mapping, to de-obfuscate stack traces)
   - Notes: the changelog file, then GitHub's generated notes.

   Watch it with `gh run watch`, and check the result with `gh release view vX.Y.Z`.

If `main` is pushed without a version bump, the workflow publishes a prerelease
`vX.Y.Z-build.N` instead of overwriting the existing release.

## Signing

Release APKs are signed in CI with the release keystore from repository secrets (see
[CI/CD](ci-cd.md#secrets)). **Keep the same keystore forever**: Android refuses to update an app
signed with a different key. The certificate's SHA-256 is pinned in `release.yml`
(`CRUX_CERT_SHA256`), in the README, and in the F-Droid recipe:

```
a28e87817c000b9f3c8dd2429803605e2d78f76eaf9c7c6356676bfe2b3ccc36
```

Locally, without `CRUX_KEYSTORE_FILE` set, release builds use the debug key.

## Distribution

- **GitHub Releases**: every release.
- **F-Droid**: planned, with Crux's own signature (reproducible builds). See [F-Droid](fdroid.md).
- **Google Play**: later. The fastlane metadata is already in the layout Play uses.
