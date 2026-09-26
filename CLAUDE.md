# CLAUDE.md

Condensed operating rules for this repo. Full context: `AGENTS.md` (releasing,
structure), `docs/` (API, architecture, roadmap).

## What this is

**Greenwood School +** — Kotlin/Jetpack Compose Android app (single `:app`
module, namespace `school.greenwood.plus`), an enhanced client for the Boti API
of Greenwood School. Ships as side-loaded APKs via GitHub Releases — no store.

## Non-negotiable rules

1. **Never commit to `main`.** Feature branch + PR, always.
2. **Never merge a PR or publish a stable release without the user's explicit
   go-ahead.** Only a feature-branch `--prerelease` beta may ship as part of
   requested work.
3. **French text keeps every accent** (é è ê ë à â ç î ï ô û ù ü œ), including
   capitalized words (« École », « élèves ») and commit messages. Agent-facing
   docs are English. Before finishing, re-read every French string you touched.
4. **Protocol facts only from** `docs/api/BOTI-API.md` / `ENDPOINT-MAP.md`.
   Anything marked UNVERIFIED needs a live read-only check before touching a
   user-facing path. Never invent request shapes.
5. **Never log tokens or request params.** No secrets in the tree.

## Verify (definition of done)

```bash
./gradlew :app:assembleDebug       # must succeed
./gradlew :app:testDebugUnitTest   # must pass, zero failures
```

No PR merges before both pass. JDK 17+, Android SDK platform 37,
`local.properties` points at the SDK (exists, not committed).

## Release flow (exact commands)

Signed with the **debug keystore** — never switch keystores/certs; every
published version uses cert SHA-256 `775498f7…`, so updates install in place.

1. **Bump version on the feature branch** (not directly on `main`):
   `app/build.gradle.kts` → `versionCode` +1, `versionName` to the new tag;
   fold `CHANGELOG.md`'s top section into the dated entry. Commit titled
   « Préparer la version X.Y.Z ».

2. **Build, align, sign** (build-tools are NOT on `PATH` — full paths):

```bash
./gradlew :app:assembleRelease
~/Android/Sdk/build-tools/37.0.0/zipalign -f 4 \
    app/build/outputs/apk/release/app-release.apk /tmp/aligned.apk
~/Android/Sdk/build-tools/37.0.0/apksigner sign \
    --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out GWS-vX.Y.Z.apk /tmp/aligned.apk
```

3. **Verify before publishing** (all three must check out):

```bash
~/Android/Sdk/build-tools/37.0.0/aapt dump badging GWS-vX.Y.Z.apk | head -2
# → versionCode/versionName as bumped
~/Android/Sdk/build-tools/37.0.0/apksigner verify --print-certs GWS-vX.Y.Z.apk
# → SHA-256 digest must start with 775498f7
sha256sum GWS-vX.Y.Z.apk   # goes into the release notes
```

4. **Publish betas** (unmerged branches) — `--prerelease` is mandatory: the
   issue #46 update checker reads it to keep betas out of the stable channel.

```bash
gh release create vX.Y.Z-beta.N --target <branch-built-from> --prerelease \
    --title "GWS+ X.Y.Z-beta.N — <sous-titre français>" \
    --notes-file /tmp/notes.md GWS-vX.Y.Z-beta.N.apk
```

5. **Always verify the release body is non-empty** (long heredocs can fail
   silently):

```bash
gh release view vX.Y.Z --json body --jq '.body | length'
# if 0: gh release edit vX.Y.Z --notes-file /tmp/notes.md
```

Notes format: built-from commit + PRs, feature bullets,
« s'installe au-dessus de la X.Y.Z sans désinstallation », SHA-256. Betas add
a « Préversion (bêta) » header.

## Tooling gotchas

- **`--notes-file` from a temp file**, never a long heredoc on the command
  line (fails silently → empty release body, step 5 catches it).
- **APK naming**: `GWS-vX.Y.Z.apk` (or `GWS-vX.Y.Z-beta.N.apk`) at repo root.
- **Mandatory beta before every stable**: no `X.Y.Z` ships without a
  `X.Y.Z-beta.1`+ built from the feature branch, released with `--prerelease`,
  and **installed on the test device** first. Gradle passing ≠ works on device.
- **Device testing**: Xiaomi phone via adb on the main PC (see memory
  `device-testing` for tap coordinates). Symptom « opens Chrome » = crash.
- **Live API probing**: GET-only discipline, `probe.py` + boti-client toolkit
  (see memory `live-api-probing`). Never POST unverified shapes.
- **Parallel sessions**: never two sessions on one checkout — worktrees or
  coordination (see memory `parallel-sessions`).

## Priority guardrail

Current top priority (README): **Android back-gesture navigation** — system
back must behave correctly in every section. Do not regress it.
