# AGENTS.md

Guidance for AI agents (and humans) working in the **gws-plus** repository — the home of **Greenwood School +**, an enhanced version of the Greenwood School app.

## Project overview

Greenwood School + is an application that lets parents of pupils follow their children's school activity in real time:

- 📔 Cahier de liaison et de devoirs (homework & liaison notebook)
- 📰 Actualités de l'école (school news)
- 📁 Espace documents (document space)
- 📝 Suivi des demandes administratives (administrative request tracking)
- ✉️ Contacter l'administration de l'école en ligne (online contact with school administration)

**Current status:** the first application milestone exists — a Kotlin/Jetpack Compose app (single `:app` module) implementing the `docs/product/DESIGN.md` foundation: « Le registre » theme, session + network layer for the Boti API, 4-tab navigation with per-tab back stacks, login/onboarding, and the Registre/Devoirs/Documents/Messages/Demandes screens, plus a dedicated Conversation screen (issue #10, PR 1) and the message composer (PR 2): reply in a thread, new thread (sujet + category from server `themes[]`), attachments (SAF, 1 MB limit on the new-thread path) and voice messages (RECORD_AUDIO at runtime, MediaRecorder m4a), optimistic send with failed-retry. The composer is **ON by default** since the live send was validated (2026-09-19 — a real message reached the administration); the Messages title-bar icon is the on/off switch. The POST `nouveau-message` fields are statically verified from the official bundle and now exercised live (see `docs/product/ROADMAP.md` issue-#10 section). Creating demandes remains unwired (write-path fields unverified). The Documents tab filters by nature (Tout / Quiz / Documents) and quizzes open a play screen (issue #17): GET `quiz` live-probed read-only 2026-09-19 and the POST score submit validated live the same day (server scores and records the play itself; see `docs/product/ROADMAP.md` issue-#17 section). Sections open with useful structure immediately (issue #21): first loads draw pulsing skeletons shaped like the final content, warm opens show the last known data from a session-stamped memory cache (`data/cache/`, keyed by userId/eleveId, purged on login/logout) while the network refreshes in the background without flicker, and network failures never hide — known content stays under a non-blocking « Réessayer » banner. Data also refreshes itself when the app returns to the foreground after an absence longer than the duration set in the new Paramètres panel (default 5 minutes) — the known content stays visible during the silent refresh, while the quiz in play, the composer and login stay untouched.

**Current priority (from README):** fix Android back-gesture navigation — opening the homework section and then using the Android system back gesture must behave correctly in every section of the app. Do not regress this behaviour.

## Language rules

- **User-facing text is French.** Every French word MUST keep its proper accents: é, è, ê, ë, à, â, ç, î, ï, ô, û, ù, ü, ÿ, œ. Never strip accents from French text — not in UI strings, not in documentation, not in commit messages, not in PR titles. This includes capitalized words: write « École », not « Ecole »; « élèves », not « eleves ».
- **Agent-facing documentation (including this file) is English**, so any agent can work with it regardless of locale.
- When editing existing French content, preserve existing accents and fix any that are missing. Before submitting, re-read all French text you touched and check every word for correct accents.

## Repository structure

Current contents (update this section whenever files are added or removed):

| Path | Purpose |
| --- | --- |
| `README.md` | The front door — what the app is, screenshots, how to run it (French) |
| `CONTRIBUTING.md`, `CHANGELOG.md`, `LICENSE` | Standard project files (MIT) |
| `AGENTS.md` | This file — rules for AI agents |
| `docs/README.md` | Documentation index — start any doc dive here |
| `docs/product/` | `OVERVIEW.md` (what & why), `DESIGN.md` (« Le registre » design system), `ROADMAP.md` (living milestone + roadmap checklist) |
| `docs/development/` | `SETUP.md` (toolchain), `ARCHITECTURE.md` (layers), `NAVIGATION.md` (back-stack contract) |
| `docs/api/` | `BOTI-API.md` (protocol), `ENDPOINT-MAP.md` (observed shapes), `ENDPOINTS.md` (100-endpoint inventory) |
| `docs/security/` | `SECURITY-NOTES.md` |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/wrapper/` | Gradle 9.6 build (AGP 9.4.1, Kotlin 2.4.20, built-in Kotlin — no `kotlin.android` plugin) |
| `app/` | The Android application (`:app` module), namespace `school.greenwood.plus` |
| `app/src/main/java/school/greenwood/plus/` | Sources — key entries below |
| `…/GwsApplication.kt` | Manual DI container (`AppContainer`) |
| `…/MainActivity.kt` | Single activity, edge-to-edge, Compose |
| `…/ui/AppNav.kt` | Root state (onboarding → connexion → registre) + 6-tab shell (Registre, Cours, Devoirs, Documents, Messages, Plus — secondary sections live under Plus), back-stack contract |
| `…/ui/AppViewModels.kt` | One ViewModel per screen |
| `…/ui/theme/` | « École vivante » tokens: colors + per-tab accents, Bricolage/Public Sans type, 24/16/10 shapes, motion springs (Mouvement.kt) |
| `…/ui/components/` | Shared composables (Components.kt: GwsCard, Puce, EmptyState, skeletons…; CarteActualite.kt; BarreOnglets.kt: custom accent bottom bar) |
| `…/ui/screens/` | Login, Onboarding, registre (+ drawer menu, Post detail), actualites, plus (secondary sections menu), boutique (catalogue, product detail, order history), cours (Emploi du temps), devoirs, documents (+ Quiz play), messages (+ Conversation + composer), demandes, parametres (settings panel) |
| `…/data/api/` | BotiApi/BotiClient (generic GET/POST multipart + envelope), BotiEnvelope, MediaUrls (single-decode) |
| `…/data/session/SessionStore.kt` | DataStore session (keyToken, user, eleves; never passwords) + composer switch + actualisation-au-retour duration |
| `…/data/session/Veille.kt` | VeilleSession — foreground-return refresh signal (absence ≥ duration chosen in Paramètres) |
| `…/data/repo/` | Repositories + Normalizers (raw JSON → domain models) |
| `…/data/cache/` | Last-known-data memory caches, session-stamped (`MemoireSession`, `CachesSession` — issue #21) |
| `…/logic/CeSoir.kt` | The focal card's due-date window (Friday → Monday) |
| `…/util/` | Dates (tolerant parsing), Html, Fichiers (download + FileProvider + SAF staging), Audio (playback), EnregistreurAudio (MediaRecorder) |
| `app/src/test/` | Unit tests (dates, CeSoir, media URLs, envelope, message normalizers, composer data, quiz normalizers, post normalizers, cours normalizers, document filters, boutique, session cache, veille) |
| `app/fonts-licenses/` | OFL texts for the bundled fonts |

## Ground rules for agents

1. **Verify, don't invent.** Base every statement and change on the actual repository state. If something isn't in the repo yet, say so instead of guessing.
2. **Respect the language rules above.** French content keeps its accents; agent-facing docs stay in English.
3. **Naming.** The project is called « Greenwood School » (renamed from the legacy name in commit `2c372fc`). Use the current name everywhere.
4. **Branch + pull request workflow.** Make every change on a feature branch and open a pull request — this matches the existing history (PRs #1, #2, #3). Never commit directly to `main`.
5. **Commit and PR style.** Short, imperative messages, consistently in one language per commit (French or English), e.g. « Corriger la navigation par geste de retour Android » or "Fix Android back-gesture navigation".
6. **No secrets.** Never commit credentials, API keys, tokens, or other sensitive data.
7. **Stay surgical.** Make precise, complete changes that fully address the task; avoid unrelated changes and do not fix unrelated pre-existing issues.
8. **Keep documentation in sync.** When you add or change features, update `docs/product/ROADMAP.md` (living checklist), `CHANGELOG.md`, and this file's structure section in the same PR.

## How to verify work

The app builds. These commands must pass before a PR can be merged (JDK 17+, Android SDK with platform 37, `local.properties` pointing at the SDK):

```bash
./gradlew :app:assembleDebug       # must succeed
./gradlew :app:testDebugUnitTest   # must pass, zero failures
```

Additional checks before opening a PR:

- **Accent audit** on every French string you touched (see Language rules) — UI strings live in Kotlin sources, screens under `ui/screens/`.
- **No secrets / no personal data** in the tree; never log tokens or request params (see `docs/security/SECURITY-NOTES.md`, F3).
- **Protocol facts** must come from `docs/api/BOTI-API.md` / `docs/api/ENDPOINT-MAP.md`; anything marked UNVERIFIED there needs a live check before being wired into a user-facing path.
- Reviewing the diff for accidental scope creep.

## Releasing a version

Releases are **signed with the debug keystore** — every version ever published uses that same certificate (SHA-256 `775498f7…`), so an update installs over any previous one without uninstalling. Never switch keystores.

```bash
# 1. Version bump on a feature branch + PR titled « Préparer la version X.Y.Z »
#    (versionCode increment, versionName, CHANGELOG's « Non publié » section
#    becomes the dated entry) — never commit the bump directly to main.

# 2. Build, align, sign (build-tools binaries are NOT on PATH — use full paths)
./gradlew :app:assembleRelease
~/Android/Sdk/build-tools/37.0.0/zipalign -f 4 app/build/outputs/apk/release/app-release.apk /tmp/aligned.apk
~/Android/Sdk/build-tools/37.0.0/apksigner sign \
    --ks ~/.android/debug.keystore --ks-key-alias androiddebugkey \
    --ks-pass pass:android --key-pass pass:android \
    --out GWS-vX.Y.Z.apk /tmp/aligned.apk

# 3. Verify before publishing
~/Android/Sdk/build-tools/37.0.0/aapt dump badging GWS-vX.Y.Z.apk | head -2   # versionCode / versionName
~/Android/Sdk/build-tools/37.0.0/apksigner verify --print-certs GWS-vX.Y.Z.apk
sha256sum GWS-vX.Y.Z.apk
```

4. Publish with `gh`:

```bash
gh release create vX.Y.Z --target <branch-built-from> \
    --title "GWS+ X.Y.Z — <sous-titre français avec accents>" \
    --notes-file /tmp/notes.md GWS-vX.Y.Z.apk
```

- Notes follow the v0.2.2 format: built-from commit + PRs, feature bullets, « s'installe au-dessus de la X.Y.Z sans désinstallation », SHA-256.
- Write notes to a **temp file** and pass `--notes-file` (a long heredoc on the command line can fail silently).
- **After creating, always check the body is not empty** (`gh release view vX.Y.Z --json body --jq '.body | length'`); if it is, repair with `gh release edit vX.Y.Z --notes-file /tmp/notes.md`.
- Betas of unmerged branches are released with `--target <branch>` and a « Préversion (bêta) » header; see v0.6.0-beta.1…3 for examples.
