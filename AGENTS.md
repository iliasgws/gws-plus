# AGENTS.md

Guidance for AI agents (and humans) working in the **gws-plus** repository — the home of **Greenwood School +**, an enhanced version of the Greenwood School app.

## Project overview

Greenwood School + is an application that lets parents of pupils follow their children's school activity in real time:

- 📔 Cahier de liaison et de devoirs (homework & liaison notebook)
- 📰 Actualités de l'école (school news)
- 📁 Espace documents (document space)
- 📝 Suivi des demandes administratives (administrative request tracking)
- ✉️ Contacter l'administration de l'école en ligne (online contact with school administration)

**Current status:** the first application milestone exists — a Kotlin/Jetpack Compose app (single `:app` module) implementing the DESIGN.md foundation: « Le registre » theme, session + network layer for the Boti API, 4-tab navigation with per-tab back stacks, login/onboarding, and the Registre/Devoirs/Documents/Messages/Demandes screens, plus a dedicated Conversation screen (issue #10, PR 1) and the message composer (PR 2): reply in a thread, new thread (sujet + category from server `themes[]`), attachments (SAF, 1 MB limit on the new-thread path) and voice messages (RECORD_AUDIO at runtime, MediaRecorder m4a), optimistic send with failed-retry. The composer is gated by a session kill switch **default off**: the POST `nouveau-message` fields are statically verified from the official bundle (see `TASKS.md` issue-#10 section) but one real send must be validated by the user before the default flips — an unverified send would make a parent believe the school was notified. Creating demandes remains unwired (write-path fields unverified).

**Current priority (from README):** fix Android back-gesture navigation — opening the homework section and then using the Android system back gesture must behave correctly in every section of the app. Do not regress this behaviour.

## Language rules

- **User-facing text is French.** Every French word MUST keep its proper accents: é, è, ê, ë, à, â, ç, î, ï, ô, û, ù, ü, ÿ, œ. Never strip accents from French text — not in UI strings, not in documentation, not in commit messages, not in PR titles. This includes capitalized words: write « École », not « Ecole »; « élèves », not « eleves ».
- **Agent-facing documentation (including this file) is English**, so any agent can work with it regardless of locale.
- When editing existing French content, preserve existing accents and fix any that are missing. Before submitting, re-read all French text you touched and check every word for correct accents.

## Repository structure

Current contents (update this section whenever files are added or removed):

| Path | Purpose |
| --- | --- |
| `README.md` | Project description + build instructions, in French |
| `app-description.png` | Screenshot/illustration of the app description |
| `AGENTS.md` | This file — rules for AI agents |
| `DESIGN.md` | Design document « Le registre » (visual direction, navigation contract, scope) |
| `TASKS.md` | Living checklist of the current milestone (issue #8) |
| `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradlew`, `gradle/wrapper/` | Gradle 9.6 build (AGP 9.4.1, Kotlin 2.4.20, built-in Kotlin — no `kotlin.android` plugin) |
| `app/` | The Android application (`:app` module), namespace `school.greenwood.plus` |
| `app/src/main/java/school/greenwood/plus/` | Sources — key entries below |
| `…/GwsApplication.kt` | Manual DI container (`AppContainer`) |
| `…/MainActivity.kt` | Single activity, edge-to-edge, Compose |
| `…/ui/AppNav.kt` | Root state (onboarding → connexion → registre) + 4-tab shell, back-stack contract |
| `…/ui/AppViewModels.kt` | One ViewModel per screen |
| `…/ui/theme/` | « Le registre » tokens: colors, Fraunces/Bricolage/Public Sans type, 20/12/6 shapes |
| `…/ui/components/Components.kt` | Shared composables (GwsCard, Puce, EmptyState, ErrorInline, GwsAvatar…) |
| `…/ui/screens/` | Login, Onboarding, registre (+ Post detail), devoirs, documents, messages (+ Conversation detail), demandes |
| `…/data/api/` | BotiApi/BotiClient (generic GET/POST + envelope), BotiEnvelope, MediaUrls (single-decode) |
| `…/data/session/SessionStore.kt` | DataStore session (keyToken, user, eleves; never passwords) |
| `…/data/repo/` | Repositories + Normalizers (raw JSON → domain models) |
| `…/logic/CeSoir.kt` | The focal card's due-date window (Friday → Monday) |
| `…/util/` | Dates (tolerant parsing), Html, Fichiers (download + FileProvider), Audio (message playback) |
| `app/src/test/` | Unit tests (dates, CeSoir, media URLs, envelope, message normalizers) |
| `app/fonts-licenses/` | OFL texts for the bundled fonts |
| `docs/` | `BOTI-API.md` (protocol), `ENDPOINT-MAP.md` (observed shapes), `endpoints.md` (100-endpoint inventory), `SECURITY-NOTES.md` |

## Ground rules for agents

1. **Verify, don't invent.** Base every statement and change on the actual repository state. If something isn't in the repo yet, say so instead of guessing.
2. **Respect the language rules above.** French content keeps its accents; agent-facing docs stay in English.
3. **Naming.** The project is called « Greenwood School » (renamed from the legacy name in commit `2c372fc`). Use the current name everywhere.
4. **Branch + pull request workflow.** Make every change on a feature branch and open a pull request — this matches the existing history (PRs #1, #2, #3). Never commit directly to `main`.
5. **Commit and PR style.** Short, imperative messages, consistently in one language per commit (French or English), e.g. « Corriger la navigation par geste de retour Android » or "Fix Android back-gesture navigation".
6. **No secrets.** Never commit credentials, API keys, tokens, or other sensitive data.
7. **Stay surgical.** Make precise, complete changes that fully address the task; avoid unrelated changes and do not fix unrelated pre-existing issues.
8. **Keep documentation in sync.** When you add or change features, update `README.md` (including « Priorité actuelle ») and this file's structure section in the same PR.

## How to verify work

The app builds. These commands must pass before a PR can be merged (JDK 17+, Android SDK with platform 37, `local.properties` pointing at the SDK):

```bash
./gradlew :app:assembleDebug       # must succeed
./gradlew :app:testDebugUnitTest   # must pass, zero failures
```

Additional checks before opening a PR:

- **Accent audit** on every French string you touched (see Language rules) — UI strings live in Kotlin sources, screens under `ui/screens/`.
- **No secrets / no personal data** in the tree; never log tokens or request params (see `docs/SECURITY-NOTES.md`, F3).
- **Protocol facts** must come from `docs/BOTI-API.md` / `docs/ENDPOINT-MAP.md`; anything marked UNVERIFIED there needs a live check before being wired into a user-facing path.
- Reviewing the diff for accidental scope creep.
