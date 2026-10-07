# F-Droid

Crux is meant to be published on [F-Droid](https://f-droid.org) with **its own signature**. F-Droid
rebuilds the APK from source and checks that it matches the APK on the GitHub release, apart from
the signature. If it does, F-Droid ships the GitHub APK as it is. People can then switch between
F-Droid and GitHub installs without uninstalling or losing data.

## What's in place

- **Licence:** GPL-3.0-or-later. All dependencies are Apache-2.0 or BSD, and none are proprietary.
- **Store metadata** lives in `fastlane/metadata/android/en-US/`: title, descriptions, a changelog per
  versionCode, the icon, the feature graphic, and screenshots kept in sync by the screenshot tests.
- **No dependency report in the APK** (`dependenciesInfo { includeInApk = false }`).
- **Reproducible build.** The *Reproducible build* workflow builds the release APK twice from clean
  checkouts and compares them with `apksigcopier`.
- **No network at build time.** AboutLibraries runs in `offlineMode`.
- **Tags** look like `vX.Y.Z`, the form F-Droid's auto-update expects.

## Before submitting

1. The repo is public (done).
2. Make a release that includes the changes above (done: v0.2.0 is the first; v0.1.0 doesn't have
   them). The recipe below uses its `versionName`, `versionCode` and tag.
3. Build the recipe locally with fdroidserver (Docker), from a checkout of
   [fdroiddata](https://gitlab.com/fdroid/fdroiddata) with the file below saved as
   `metadata/com.hardtekpt.crux.yml`:

   ```sh
   docker run --rm -v "$PWD:/repo" -w /repo registry.gitlab.com/fdroid/fdroidserver:buildserver \
     sh -c "fdroid lint com.hardtekpt.crux && fdroid build -v -l com.hardtekpt.crux"
   ```

   The build uses F-Droid's JDK. If the APK doesn't match the GitHub one, check that the JDK
   matches CI's (Temurin 21) first.
4. Open a merge request to fdroiddata with the file. The review usually takes a few weeks.

## Recipe (`metadata/com.hardtekpt.crux.yml` in fdroiddata)

```yaml
Categories:
  - Sports & Health
License: GPL-3.0-or-later
AuthorName: Francisco Santos
SourceCode: https://github.com/hardtekpt/crux
IssueTracker: https://github.com/hardtekpt/crux/issues
Changelog: https://github.com/hardtekpt/crux/releases

AutoName: Crux

RepoType: git
Repo: https://github.com/hardtekpt/crux.git
Binaries: https://github.com/hardtekpt/crux/releases/download/v%v/crux-v%v.apk

Builds:
  - versionName: 0.2.0          # the first release with the F-Droid changes
    versionCode: 200            # major * 10000 + minor * 100 + patch
    commit: v0.2.0
    subdir: app
    # If the build server's default JDK can't run AGP 9.4, add a `sudo:` step that installs
    # JDK 21, copied from a current fdroiddata recipe that does the same.
    gradle:
      - yes

# SHA-256 of the release signing certificate (also in release.yml and the README).
AllowedAPKSigningKeys: a28e87817c000b9f3c8dd2429803605e2d78f76eaf9c7c6356676bfe2b3ccc36

AutoUpdateMode: Version
UpdateCheckMode: Tags ^v[0-9.]+$
CurrentVersion: 0.2.0
CurrentVersionCode: 200
```

## Anti-features

None are expected. Map tiles come from OpenStreetMap, a free network service, and address search
uses the phone's own geocoder. If reviewers ask, [PRIVACY.md](../../PRIVACY.md) describes both.
