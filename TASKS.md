# TASKS — Issue #8 « Start building the actual app »

Living checklist for the first application milestone. Agent-facing doc (English).
Probe findings are distilled into `docs/BOTI-API.md` and `docs/ENDPOINT-MAP.md`;
raw probe responses stay out of the repository (personal data).

## Legend

`[x]` done · `[~]` in progress · `[ ]` todo · `(!)` needs a decision or a real-device check

## 1. Groundwork

- [x] Read DESIGN.md, AGENTS.md, docs/BOTI-API.md, greenwood-school-re protocol docs
- [x] Live shape probe (own parent account, GET only) → `~/dev/shape-probe/*.json`
- [x] Resolve DESIGN.md §4 open question: `date_remise` = due date (ISO `yyyy-mm-dd`),
      `date`/`publication` = publication. Client-side filtering (server `date` param ignored).
- [x] Catch the `devoirs_date_v2` misread: POST = *work-submission* endpoint (« Travail envoyé »),
      never use it for the date selector; GET `devoirs` (no date param) returns
      `devoirs_remettre` + `devoirs_ancien` with `date_remise`.
- [x] Catch `objects` misread: it is the lost-and-found feed, not the document space
      (documents = `ressources_v2` / `bibliotheque` / `cartable_numeriques`).
- [x] Verify library versions live (maven-metadata): AGP 9.4.1 (built-in Kotlin),
      Kotlin 2.4.20, Gradle 9.6.0, compileSdk 37, compose-bom 2026.09.00,
      navigation 2.10.1, retrofit 3.0.0, okhttp 5.5.0, datastore 1.2.1, coil3 3.6.3
      (the whole current AndroidX wave requires compileSdk 37 + AGP ≥ 9.1)
- [x] Download Fraunces + Bricolage Grotesque + Public Sans (variable TTFs, OFL texts kept)
- [x] Local toolchain: JDK 21 (Temurin) + Android SDK cmdline-tools → platform 37.2, build-tools 37
- [x] Update docs (delegated — see §10)

## 2. Build files & manifest

- [x] `.gitignore` (Android + local.properties + secrets)
- [x] `settings.gradle.kts`, root `build.gradle.kts`, `gradle.properties`
- [x] `app/build.gradle.kts` (compileSdk 37, minSdk 26, R8 for release)
- [x] Gradle wrapper (9.6.0) committed
- [x] `AndroidManifest.xml`: INTERNET only, `enableOnBackInvokedCallback`, FileProvider
- [x] Launcher icon (adaptive: ink monogram + red-pen dot, sage ground)

## 3. Theme « Le registre »

- [x] Palette tokens (ink/paper/page/sage/redPen/chalk, light + dark)
- [x] Extended tokens via CompositionLocal (`RegistreTheme.colors`)
- [x] Type: Fraunces display (candidate) + Bricolage candidate + Public Sans (locked),
      28/17/15/13 hierarchy, tabular digits helper
- [x] Shapes: pages 20 / controls 12 / annotations 6
- [x] `GwsPlusTheme` wrapper

## 4. Data layer

- [x] Retrofit client: generic GET/POST (endpoint as path), envelope unwrap,
      `disconnect:true` → global session-expiry event, `error:true` → typed error
- [x] Session params: `key`, `user_id`, `parent_id`, `eleve_id`, `versionCode`,
      `versionNumber`, `paltform` (sic, never "fixed"), `lang`
- [x] Media URL resolution (Google viewer → real URL, decode exactly once)
- [x] DataStore session store (keyToken, userId, parent, eleves, selected eleve,
      remember, onboarding-seen)
- [x] Normalizers: devoirs (buckets remettre/ancien), nouveautes, messages,
      demandes, absences, ressources, contact — tolerant to missing fields
- [x] `admin_nouveautes` streamed description extractor (android.util.JsonReader,
      bounded memory, 19 MB response) with per-session cache
- [x] Repositories: Auth, Registre, Devoirs, Documents, Messages, Demandes

## 5. Navigation (priority n°1 of the project)

- [x] Single NavHost, 4 tab destinations, per-tab stacks via saveState/restoreState
- [x] Back from a tab root → Registre; back from Registre → exit; details push/pop cleanly
- [x] Predictive back enabled end to end (`enableOnBackInvokedCallback` + edge-to-edge)
- [x] Session expiry → jump to login from anywhere

## 6. Screens

- [x] Onboarding: 3 sober pages, first launch only
- [x] Login: phone + password + « retenir », forgot-password, inline red-pen errors
- [x] Registre: date header (Fraunces), child avatar + switcher, « Ce soir » focal card,
      chronological feed (nouveautes / devoirs donnés / absences / messages), empty states
- [x] « Ce soir » window: échéance ∈ (today, next school day], Friday→Monday
- [x] Devoirs: day selector (client-side filter on `date_remise`), attachments download +
      open PDF via FileProvider
- [x] Post detail: full body (streamed `admin_nouveautes`), images, attachments
- [x] Documents: ressources_v2 list by matière + search
      (!) this account only sees quizzes today — real-device review of empty richness
- [x] Messages: conversations as threads (is_self direction), contact card
      (tel / facebook / site) with dial intent
- [ ] Sending a message — blocked (!) POST `nouveau-message` field names unverified;
      shipping an unverified write path risks silent loss of parent messages.
      Compose UI hidden until one verified test.
- [x] Demandes: list with statut + reponses, reachable from Registre
- [x] Child switcher: local switch (eleve_id param) — (!) server pick_enfants POST
      field names unverified, pending verification

## 7. Tests

- [x] Written: Dates parser, CeSoir window, Media URL single-decode, Envelope unwrap
- [x] Green on this machine — 28 tests, 0 failures (`testDebugUnitTest`)

## 8. Quality gates before PR

- [x] `assembleDebug` green (app-debug.apk, 21.7 Mo)
- [x] `testDebugUnitTest` green (28 tests, 0 failures)
- [x] Accent audit on all French strings (é è ê à ç œ …) — one a11y label fixed
- [x] No secrets / no personal data in the tree; logs carry no tokens
- [x] README + AGENTS.md structure & verify sections updated

## 9. Delivery

- [ ] Branch, commits in French, imperative
- [ ] PR referencing issue #8, describing what is verified vs unverified

## 10. Docs (done by a subagent)

- [x] `docs/BOTI-API.md` corrections: devoirs_date_v2 POST = submission,
      date param ignored on GET devoirs, objects = lost & found,
      acces_check = session bootstrap (returns fresh keyToken)
- [x] `docs/ENDPOINT-MAP.md`: complete parent-relevant endpoint map with
      observed shapes (from `~/dev/shape-probe`), no personal data
- [x] `DESIGN.md` §4 surgical corrections (devoirs date semantics, documents sources)
