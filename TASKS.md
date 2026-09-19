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

- [x] Branch `app-v1-foundation`, commits in French, imperative
- [x] PR #9 referencing issue #8, describing what is verified vs unverified

## 10. Docs (done by a subagent)

- [x] `docs/BOTI-API.md` corrections: devoirs_date_v2 POST = submission,
      date param ignored on GET devoirs, objects = lost & found,
      acces_check = session bootstrap (returns fresh keyToken)
- [x] `docs/ENDPOINT-MAP.md`: complete parent-relevant endpoint map with
      observed shapes (from `~/dev/shape-probe`), no personal data

# TASKS — Issue #10 « Messages tab misses sending option »

Living checklist for the message-composing milestone. Split in two PRs per
user decision (2026-09-19): PR 1 = conversation screen (read-only), PR 2 =
composer + sending + attachments + voice. The write path is verified
**statically** (bundle), not yet **live**.

## Verification of the write path (2026-09-19)

- [x] POST `nouveau-message` field names read from the official bundle
      (XAPK 2.4.14 still at `~/Downloads/Greenwood+School_2.4.14_APKPure.xapk`,
      chunks 1201/2345/9771.js). Reply: `ref, sujet, message, theme,
      files[], eleve_id, parent_id, key, audio, index` (index = conversation
      length before the optimistic push; response `.message` replaces the
      pending item). New message: `sujet, message, theme, eleve, file,
      eleve_id, parent_id, user_id, key` (+ `ref` when reply; the `file`
      JSON field is vestigial — use `files[]` parts for attachments).
- [x] ApiService FormData conventions: `"files[]"` = repeated literal part
      name, one part per file, filename preserved; `audio` sent as raw blob
      `{file: Blob, name: "audio_<epoch>.<ext>"}`; POSTs carry the fields +
      `key` only — **no** `paltform`/`versionCode` envelope.
- [x] `hidesend` is dead code in v2.4.14 — zero references across the
      bundle; the official app never reads it. Composer gated by an app
      setting instead (user decision: kill switch, default off).
- [ ] One live send before enabling the write path (user runs it — it
      reaches the school administration).

## PR 1 — Conversation screen (read-only) — branch `messages-conversation`

- [x] Normalizers: `vu_le` receipt, `audio` (tolerant: URL string or object,
      shape never observed non-null), consecutive duplicate messages dropped
      (same text/direction < 2 s apart, distinct `message_id` — never dedupe
      by `message_id`)
- [x] `Message` model gains `vuLe` + `audio`
- [x] `MessagesRepository.conversation(id)` — refetches the list (the server
      embeds conversations in GET `messages`; fresh fetch refreshes signed
      URLs that expire after 15–20 min)
- [x] `ConversationScreen` (new): bubbles (sage = administration, page +
      sage border = parent), `frenchLongDay` date separators, per-bubble
      timestamps, `vu_le` receipts on parent messages, inline attachments
      (download + open via existing `Fichiers`), audio playback (new
      `util/Audio.kt`, MediaPlayer streaming, silent on failure)
- [x] Route `conversation/{conversationId}`; message cards navigate instead
      of in-card expansion; contact card untouched
- [x] Tests: 10 new (dedupe rule, `vu_le`, audio forms) — 38 total, 0 failures
- [x] `assembleDebug` green
- [ ] (!) review on a real device at first install

## PR 2 — Composer, sending, attachments, voice — branch `messages-composer`

- [x] `BotiClient.post`: repeated file parts (`PartieFichier` — `files[]`
      literal name, one part per file, filename preserved; audio as `{file, name}`)
- [x] `MessagesRepository.envoyerRéponse / envoyerNouveau` — POST
      nouveau-message + response `.message` normalization; optimistic pending →
      failed-with-retry states (`MessageEnvoi`); `index` = thread length before
      the push; reply re-sends the thread's own `theme` (bundle: `theme:
      this.result.theme` — threads carry it, parsed tolerantly)
- [x] Composer row (attachment · input · mic · send) in `ConversationScreen`
      and `NouveauMessageScreen`, gated by the session kill switch (default
      off; `hidesend` documented, informational); toggle lives in the Messages
      title bar with an activation confirmation
- [x] Category selection driven by server `themes[]` (ids 8/9/10/11/13) in
      new-message mode; sujet field; reworked « Joindre l'administration »
      card (Écrire action when the composer is on; card hidden when the
      server returns nothing and the composer is off — fixes the blank card)
- [x] Attachments: SAF picker, staging in cacheDir (`Fichiers.copierDepuisSaf`),
      preview + remove, 1 MB toast (official limit, new-message path only)
- [x] Voice: `RECORD_AUDIO` in manifest + runtime prompt, MediaRecorder →
      m4a (`util/EnregistreurAudio.kt`), recording state integrated in the
      composer (pastille + chrono + annulation), playback for sent and received
      (`LecteurAudio`)
- [x] Tests: 6 new (themes parsing, thread theme, POST-response normalization,
      envoi status copy, mimes) — 44 total, 0 failures
- [ ] (!) one live send before flipping the default (user runs it — it reaches
      the school administration)
- [x] Docs: `ENDPOINT-MAP.md` nouveau-message section (bundle-verified
      fields + live-test result pending), unverified-table updates, README,
      AGENTS.md
- [x] `DESIGN.md` §4 surgical corrections (devoirs date semantics, documents sources)

# Roadmap — composer v2 (post-issue #10)

Wishlist from the first live test (2026-09-19), in no particular order.
Nothing here is wired — design + decisions first.

## Send queue with cancellation window

A sent message is irreversible once the POST lands — the parent cannot
recall it. A short send delay gives the writer a window to reread, edit or
cancel, which matches how careful these messages must be.

- [ ] Queued-send model: message sits in a local queue (`envoi planifié`)
      with a countdown instead of posting immediately
- [ ] Default delay 5 minutes, user-configurable (30 s / 1 / 2 / 5 / none)
- [ ] Queue UI in the conversation: pending row with countdown, edit,
      cancel, delete — clearly distinct from the optimistic « Envoi… » state
- [ ] « Envoyer maintenant » (force send now) button bypassing the delay
      — must remain a deliberate, separate gesture from the default
- [ ] Queue survives process death (persisted, not just in-memory)
- [ ] Interaction with the kill switch: queue only active when the composer
      is on; delayed sends re-check the session before posting

## AI writing assist (smart writing)

Assist the parent in drafting — never send on its own, never invent facts
about the school.

- [ ] Decide the provider + privacy posture first (which API, what data
      leaves the device, on by default or opt-in) — needs a user decision
- [ ] Draft suggestion from a short intent (« demander une attestation »)
- [ ] Rewrite/polish of the typed message (tone, grammar, formality)
- [ ] Inline accept / regenerate / dismiss — composer stays the single
      source of truth for the text
- [ ] Never auto-send: AI output always lands in the composer for review
- [ ] French-first prompts; school-context glossary kept client-side
