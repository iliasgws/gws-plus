# TASKS — Issue #8 « Start building the actual app »

Living checklist for the first application milestone. Agent-facing doc (English).
Probe findings are distilled into `docs/api/BOTI-API.md` and `docs/api/ENDPOINT-MAP.md`;
raw probe responses stay out of the repository (personal data).

## Legend

`[x]` done · `[~]` in progress · `[ ]` todo · `(!)` needs a decision or a real-device check

## 1. Groundwork

- [x] Read DESIGN.md, AGENTS.md, docs/api/BOTI-API.md, greenwood-school-re protocol docs
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
- [x] Launcher icon (adaptive: tree + cross artwork on a blue gradient sized
      to survive every launcher mask; themed icon keeps the ink monogram)

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

- [x] `docs/api/BOTI-API.md` corrections: devoirs_date_v2 POST = submission,
      date param ignored on GET devoirs, objects = lost & found,
      acces_check = session bootstrap (returns fresh keyToken)
- [x] `docs/api/ENDPOINT-MAP.md`: complete parent-relevant endpoint map with
      observed shapes (from `~/dev/shape-probe`), no personal data

# TASKS — Issue #10 « Messages tab misses sending option »

Living checklist for the message-composing milestone. Split in two PRs per
user decision (2026-09-19): PR 1 = conversation screen (read-only), PR 2 =
composer + sending + attachments + voice. The write path was verified
**statically** (bundle), then **live** (first real send, 2026-09-19).

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
      setting instead (kill switch for the first days, default ON since the
      validated live send).
- [x] One live send before enabling the write path (2026-09-19 — message
      delivered to the school administration).

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
- [x] (!) one live send before flipping the default — DONE 2026-09-19: user
      sent a real message (text) to the administration successfully;
      composer default flipped to ON, the Messages title-bar icon is the
      on/off switch
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

# Roadmap — beyond the composer (first-week wishlist, 2026-09-19)

Ideas collected after the composer went live. Nothing wired; each needs a
design pass and, where data comes from outside the official API, a serious
source-of-truth decision before any code.

## Homework alternate source

Some teachers never write homework in the official app — those subjects
simply go dark in the Devoirs tab, and the parent finds out too late.

- [ ] Decide the alternate source: manual entry by the parent? Shared
      between parents (community)? Imported from another channel?
- [ ] If community-shared: needs a server/sync story + who can write —
      big trust question, keep out of scope until decided
- [ ] If manual/local: parent-entered homework for a subject, clearly
      badged « ajouté à la main » so it never masquerades as official
- [ ] Merge display: official `devoirs` list + alternate entries, same
      « Ce soir » treatment, source always visible

## Timetable community feedback

The administration itself says the timetable is provisional — parents spot
the changes first, in the corridors.

- [x] Read-only first: the timetable IS in the API (`cours_v2` — top-level week
      shape verified 2026-09-20); inner `seances[]` slots were empty in the
      probe, parsed defensively (plausible field names, never trusted)
- [ ] Parent-side report: flag a slot as wrong/changed, in-app note —
      local first, no shared write path without a decision
- [ ] Community layer only if/when several parents use the app: shared
      corrections with authorship, opt-in, moderation story
- [ ] Parent-side report: flag a slot as wrong/changed, in-app note —
      local first, no shared write path without a decision
- [ ] Community layer only if/when several parents use the app: shared
      corrections with authorship, opt-in, moderation story

## « Download all » for assignments and documents

Homework attachments and document-space files arrive one by one; a single
button that grabs them all is the obvious missing convenience.

- [ ] Devoirs screen: « Tout télécharger » per day — images + documents
      via the existing `Fichiers.télécharger`, progress per file, retry
      on failure
- [ ] Post detail: « Tout télécharger » for one post's attachments
- [ ] Documents screen: « Tout télécharger » per matière (bounded — warn
      if the set is large)
- [ ] Open-with chooser after batch download; files land in the same
      private documents space (no storage permission needed)

# TASKS — Issue #17 « Documents: filter by type + access the quizzes »

Living checklist for the document-space milestone (issue #17): a filter by
document type, and actually opening the quizzes. Branch `documents-quiz`.

## Verification (2026-09-19)

- [x] GET `quiz` live-probed read-only on the reference account
      (`~/dev/shape-probe/quiz_list.json`, `quiz_play.json`): a paginated
      list (`start`/`limit`, `matieres[]` catalog) and the play form
      (`data{quiz_id, label, matiere, niveau, color, image, questions[],
      minutes, can_play, can_replay}` — questions carry `reponses[].correct`
      flags, per-question `temps_reponse`, pre-filled `answer.correct`,
      top-level `no_play`). Shapes distilled into `docs/api/ENDPOINT-MAP.md`.
- [x] POST `quiz` read from the official bundle (chunk 1140.js, page
      `/parent/quiz`): `quiz_id`, `questions` = the GET array serialized
      with `answer.answer`/`answer.answered` updated, `eleve_id`, `user_id`,
      `parent_id`, `key`; response carries `score`/`time`/`can_replay`.
      User decision: ship it wired — a failed POST is non-fatal, the local
      score stays displayed (the official app itself ignores POST errors).

## Branch `documents-quiz`

- [x] Documents tab: filter by nature — Tout / Quiz / Documents (type
      `quiz` vs everything else; absent type stays a document) — chips
      under the search field, same gabarit as the Devoirs day selector
- [x] Quiz rows open a quiz screen (GET `quiz?quiz_id=`): start card
      (visual, matières, « N questions · durée », « Démarrer le quiz »),
      then question-by-question play with the official rules — per-question
      countdown mm:ss, immediate right/wrong feedback (the server carries
      the flags), 2 s then advance, timeout = unanswered with full time
- [x] End of play: POST `quiz` records the attempt; the score screen shows
      the server score, falls back to the local count with a discreet
      inline note when the POST fails; « Rejouer » when `can_replay`
- [x] Non-quiz rows stay inert (`ressource_details` shapes UNVERIFIED)
- [x] Tests: 12 new (quiz normalizers, POST payload builder, nature
      filter, duration/clock helpers) — 59 total, 0 failures
- [x] `assembleDebug` green
- [x] (!) one live quiz play to validate the POST — DONE 2026-09-19: a
      simulated play (3 correct of 5, one wrong, one timeout) was accepted;
      the server scored and recorded it itself (`lastPlay`: id, date,
      « 60 % ») and the POST response carries `score` (« 3/5 »), `time`
      (« 02:01 »), `can_replay` flat (see `docs/api/ENDPOINT-MAP.md`).
      The installed app had also played a quiz earlier the same evening —
      the app's own POST worked live too. Shapes distilled into
      `docs/api/ENDPOINT-MAP.md` (`quiz_post.json`, `quiz_play_apres.json`).
- [x] Protected exit while playing: during the Jeu phase, every path out of
      the quiz asks for confirmation — system back gesture/button
      (`BackHandler`), the screen's own back arrow, and any bottom-bar tab
      switch (the shell watches the `container.quizEnJeu` signal driven by
      `QuizViewModel`); confirming abandons the attempt (popped without
      state, score not recorded), « Continuer le quiz » cancels. Départ and
      Résultat stay freely exitable — nothing to lose there

# TASKS — Issue #21 « Skeleton screens, data cache, truncated dates »

Living checklist for the responsiveness milestone (issue #21): skeletons on
first load, a session-scoped last-known-data cache refreshed in the
background, non-blocking error banners, and the registre date fix. Branch
`issue-21-cache-squelettes`.

## Design decisions

- [x] Cache = memory only, stamped on write with the session pair
      `userId/eleveId` (`MemoireSession<T>`): switching account OR child
      mechanically invalidates everything, and `CachesSession.vider()` also
      runs on login and logout (belt and braces). Nothing new on disk — a
      cold start shows skeletons, a warm start shows the last known data.
- [x] Two-state loading contract, one field each in the six data states:
      `chargement` true only when there is nothing to show (skeleton
      territory); `rafraîchissement` = network refresh running while known
      content stays displayed. Data is never cleared between states (no
      flicker): prefill from cache, then one `copy` swaps the list.
- [x] Errors are never silent: on failure the known content stays and the
      error surfaces as a non-blocking banner (« Réessayer » = force);
      the full-screen error remains only when there is no content at all.
      `erreur` is cleared on success only, not at retry start, so the
      banner does not blink off during a refresh that fails again.

## Branch `issue-21-cache-squelettes`

- [x] `data/cache/`: `MemoireSession<T>` (pure Kotlin, synchronized,
      unit-tested) + `CachesSession` (registre / devoirs / documents /
      demandes / messages) wired through every repository. Messages keeps
      its 45 s TTL on top; `conversationsEnCache()` now serves the stamped
      page regardless of TTL (stale-while-revalidate) and a new
      `conversationEnCache(id)` prefills an open thread
- [x] The streamed post-body cache in Nouveautes is stamp-checked too (the
      one cache living outside the holder); a failed read never writes the
      cache, an empty list is a valid last-known state
- [x] Auth: caches purged on login (before the new session is written) and
      on logout
- [x] ViewModels (Registre, Devoirs, Documents, Messages, Conversation,
      Demandes): cache prefill, background refresh, `charger(force)`
      skipping the prefill for « Réessayer »; composer, quiz, login
      untouched
- [x] Components: `BlocSquelette` (pulsing sage block, alpha 0.35→1) plus
      one skeleton per screen shaped like the real layout — Registre,
      Devoirs, Documents, Messages, Demandes, Conversation (bubbles +
      composer bar), Quiz; `BandeauErreur` = inline error + « Réessayer »
      in one quiet row, reused at every call site
- [x] Post detail: the body area pulses while `admin_nouveautes` streams
      (fallback text kept for an unavailable body)
- [x] Dates: the registre header wraps to two lines instead of ellipsizing
      (« jeudi 18 septembre » stays readable on small screens); every other
      date spot audited — they all wrap already (no `maxLines`, weight(1f)
      columns)
- [x] Tests: 7 new (cache stamping contract: same key serves, foreign or
      blank key never serves, vider clears, empty list is a state) —
      66 total, 0 failures
- [x] `assembleDebug` green
- [x] Navigation untouched (`ui/AppNav.kt` not in the diff) — the Android
      back-gesture contract, priority n°1 of the project, cannot regress
      from this change
- [ ] (!) real-device pass: skeleton → content swap on each tab, warm
      reopen without flicker, banner + « Réessayer » under airplane mode,
      back gesture from Devoirs unchanged

# TASKS — Plan Nouveautés « Onglet dédié Actualités & détail des posts »

Living checklist for the Nouveautés milestone (plan `docs/product/PLAN-NOUVEAUTES.md`).
Branch `nouveautes-onglet`.

## Verification (2026-09-20)

- [x] GET `nouveautes` live-probed: 1-based pagination verified (`start = accumulated length + 1`, `limit = 10`),
      pull-to-refresh (`start = 0, limit = max(taille, 10)`), merge rule (`start == 0` replaces, `start > 0` appends)
- [x] GET `post_view` live-probed read-only: per-post detail (`data`, `post`, `images`, `comments`, `quiz`, `translation`),
      serves as server-side mark-as-read
- [x] Write paths gated OFF behind DataStore kill switch (`ecritureNouveautesActivée = false`)

## Branch `nouveautes-onglet`

- [x] Step 1: Probes & docs (`docs/api/ENDPOINT-MAP.md` updated with pagination rules, write paths, post_view)
- [x] Step 2: Models (`Post` extended with bookmark/auteur/permits, `Commentaire`, `QuestionPost`, `PostDetail`)
- [x] Step 3: Normalizers (`post` with multi-file fix, `commentaire`, `questionPost`, `postDetail`, `fusionner`, `dernière`)
- [x] Step 4: Repository & Session Cache (`CachesSession.posts`, `SessionStore.ecritureNouveautes`, `NouveautesRepository`)
- [x] Step 5: ViewModels (`ActualitesViewModel`, `PostDetailViewModel`, `RegistreViewModel.derniereActualite`)
- [x] Step 6: UI (`AppNav` 5 tabs, `CarteActualité`, `ActualitesScreen`, `PostDetailScreen`, `RegistreScreen`)
- [x] Step 7: Tests (`NormalizersPostTest.kt` — 80 unit tests total, 0 failures)
- [x] Step 8: Docs & delivery (`README.md`, `ROADMAP.md`, `CHANGELOG.md`, `AGENTS.md`)
- [x] Step 9 (on-device beta follow-up, 2026-09-20): first real-device trial of the
      beta crashed on every post open — `SquelettePostDetail` carried its own
      `verticalScroll` and sat inside `PostDetailScreen`'s scrolling Column
      (infinite-height constraints → `IllegalStateException`). Skeleton now
      scrolls with its screen; fix verified live on device (both the Actualités
      tab and the Registre « Dernière actualité » card open the detail cleanly).

# TASKS — « Emploi du temps » (tab, week view) — branch `cours-onglet`

## Verification (2026-09-20)

- [x] `cours_v2` top-level shape live-probed read-only and documented
      (ENDPOINT-MAP §cours_v2: `translation`, `next_week`/`last_week` ISO
      Mondays, `selected_day` 1-based, `label`, `seances[]` day entries)
- [ ] Inner `seances[]` slots: shape UNVERIFIED (empty in the probe) — parsed
      defensively (plausible field names: matiere/title/label, heure_debut/
      start/hdebut, heure_fin/end, salle/room, prof/enseignant/nom; any
      unrecognizable object degrades to its first string field; a bare string
      becomes the slot label). Needs a live check on an account with real slots.
- [x] Week navigation param: VERIFIED live 2026-09-22 — the server expects
      its own field name echoed back (`last_week=`/`next_week=` with the ISO
      Monday it published; a generic `date=` is silently ignored). The ←/→
      arrows use the response's own `last_week`/`next_week` so navigation
      never dead-ends.

## Design decisions

- 6th bottom tab (user decision, same path as the Actualités 5th tab) —
  `Onglet("cours", "Cours", CalendarMonth)`; DESIGN.md §3 amended.
- The tab shows BOTH the week overview and the day detail: a chip per day
  (letter + short date + slot count) above the selected day's slot cards.
- Read-only first: no parent-side writes; the `restricted` server block is
  surfaced as a notice card when the school restricts access.
- Day dates are derived from the week's Monday (extracted from the ISO label,
  fallback `last_week + 7d`) — the server day labels (« Le 14 Sep 2026 ») are
  display-only (English month abbreviations, not parseable).

## Branch `cours-onglet` (stacked on `nouveautes-onglet`, PR #28)

- [x] Step 1: Models (`Créneau`, `JournéeCours`, `SemaineCours`)
- [x] Step 2: Normalizers (`semaineCours`, `journéeCours`, `créneau`, `extractHeure`)
- [x] Step 3: Repository & cache (`CoursRepository`, `CachesSession.cours`)
- [x] Step 4: ViewModel (`CoursViewModel` — warm open from cache, ←/→ navigation,
      day selection kept across weeks)
- [x] Step 5: UI (`AppNav` 6 tabs, `CoursScreen` + `SqueletteCours`,
      `RegistreScreen` « Emploi du temps » link card)
- [x] Step 6: Tests (`NormalizersCoursTest.kt` — 89 unit tests total, 0 failures)
- [x] Step 7: Docs & delivery (`DESIGN.md` §3, `README.md`, `ROADMAP.md`,
      `CHANGELOG.md`, `AGENTS.md`, `ENDPOINT-MAP.md`)

# TASKS — « École vivante » (full-app visual restyle) — branch `restyle-ecole-vivante`

## Design decisions (2026-09-20)

- Direction chosen with the user (dad's verdict on beta 2: « not stunning »):
  bold & colorful, full-app scope, BOTH light and dark crafted.
- Concept: schoolyard energy — warm cream paper (light) / deep green-charcoal
  (dark), one accent family per tab (green, amber, blue, violet, ochre, coral),
  like subject-notebook covers. Reading surfaces stay neutral; `redPen` keeps
  its required-action-only discipline and never meets the coral (different hue,
  never in the same component).
- Display font: bundled-unused Bricolage Grotesque replaces Fraunces (removed
  from the APK); Public Sans stays for text; every changing/aligned number goes
  through the new `tabulaire()` (tnum).
- Radii up (24/16/10 + bar pill); tonal depth via distinct `surfaceContainer*`
  steps; custom `BarreOnglets` replaces stock NavigationBar — single-line labels
  (the 2-line wrap of « Actualités » etc. is fixed), per-tab accent pill, spring
  selection. Navigation contract (allerÀLOnglet, saveState/restoreState) untouched.

## Branch `restyle-ecole-vivante` (stacked on `fix-post-detail-scroll-crash`)

- [x] Tokens: Color.kt (neutrals + 6 accent triads + signetVif, light/dark),
      Tokens.kt (GwsAccent, accents map, LocalGwsAccent), Theme.kt (tonal
      ladders, error container, system-bar icon contrast SideEffect), Type.kt
      (Bricolage display, tabulaire()), Shape.kt, Mouvement.kt
- [x] Components: Puce/SectionLabel accent params, EmptyState playful mark,
      BandeauErreur M3 tint, EntréeCascade, BarreOnglets.kt, CarteActualité
      accent + signetVif token (hardcoded #FC942D gone)
- [x] AppNav: route→accent table + animated ambient accent + custom bar
- [x] Screens: Registre (focal card in green accent, highlighter, blue Cours
      link, coral Demandes line), Actualités + PostDetail (amber), Cours (blue
      chips/times), Devoirs (violet chips), Documents + Quiz (ochre; correct
      answer = universal green), Messages/Conversation/Composeur/Demandes
      (coral; recording dot is state, not required-action), Login + Onboarding
      (default green, pill pager)
- [x] System: launch windowBackground cream/charcoal
- [x] Docs: DESIGN.md §2 rewritten, CHANGELOG, AGENTS
- [ ] On-device pass: screenshots of every tab, light AND dark
      (`adb shell cmd uimode night no/yes`), back-gesture contract re-check

# TASKS — Issue #34 « Navigation active non mise en surbrillance lors de l'ouverture d'une sous-page »

Small fix, no milestone: the bottom bar highlighted a tab only on exact route
match, so any sub-page (post, quiz, conversation, nouveau-message, demandes)
left the bar with no active tab. The expected behavior is contextual: the
detail screen belongs to the tab it was opened from.

## Branch `issue-34-onglet-actif`

- [x] AppNav: read the full back stack (`NavController.currentBackStack`,
      public StateFlow in navigation 2.10.1) and derive the active tab with
      `ongletActifDe` — last tab-root entry in the stack owns the sub-page
      pushed above it; `RouteDépart` const shared with the NavHost
- [x] BarreOnglets untouched — it still compares routes, it now receives a
      route that is always a tab root (Registre fallback before first emission)
- [x] `accentDe` unchanged: screen-tint stays static (a post keeps the
      Actualités tint even when opened from Registre)
- [x] Docs: NAVIGATION.md « L'onglet actif et les sous-pages », CHANGELOG
      « Non publié »
- [ ] On-device check: open a post from Registre and from Actualités, open a
      quiz / conversation / demande, verify the parent tab stays active and
      back restores it

# TASKS — Foreground-return refresh (absence ≥ duration chosen in Paramètres)

Small feature, no milestone: the app is opened several times a day; coming
back after minutes away should show fresh data without anyone thinking
about it. Same stale-while-revalidate semantics as issue #21 — known
content stays visible while the network refreshes, never a wipe.

## Branch `veille-retour-premier-plan`

- [x] `VeilleSession` (data/session/Veille.kt): records activity
      onStop/onStart, emits one `retoursPérimés` signal when the absence is
      ≥ the duration chosen in Paramètres (0 = never); pure Kotlin, injected
      timestamps, flow delivery tested
- [x] MainActivity: onStop → `enregistrerArrêt`, onStart →
      `enregistrerReprise(minutes)` with the duration read from
      SessionStore (`actualisation_retour_minutes`, default 5, survives
      logout like the composer switch)
- [x] Subscribed screens: Registre, Devoirs, Documents, Demandes, Messages,
      Conversation, Cours (`charger(force = true)` — force skips the 45 s
      messages TTL and renews signed URLs), Actualités (`rafraîchir()` —
      keeps pagination depth; a forced `charger` would collapse the list to
      the first 10 posts), Post detail (`charger()`, already a pure network
      refetch)
- [x] Excluded: quiz in play (its `charger()` would reset `quizEnJeu`
      mid-game, breaking the issue #17 exit protection), composer (draft
      untouched), Connexion (no data)
- [x] Paramètres panel (from a quiet line at the bottom of the Registre,
      under the Demandes line): absence duration — jamais / 1 / 2 / 5 / 10
      minutes; route `parametres`, registre accent
- [x] Tests: 10 new (threshold matrix, short cycles, newest-arrêt rule,
      « jamais » setting, flow delivery) — 99 total, 0 failures
- [x] On-device check: absence below the chosen duration → nothing happens;
      absence above → content kept + silent refresh; change the duration in
      Paramètres and repeat — DONE 2026-09-21: validated live on the Xiaomi
      device

## Branch `boutique-ecole-plus`

Prerequisites: the `shop` route probed live on 2026-09-21 (GET catalogue /
cart / history / detail-commande, POST order + order deletion on the real
parent account; the accidental probe order was deleted right away). Protocol
facts recorded in `docs/api/ENDPOINT-MAP.md` → « shop ».

- [x] Probe: catalogue (rubrique + search — mandatory, else the server leaks
      PHP notices before the JSON), cart (empty in this deployment),
      history (orders + per-article can_edit/can_delete + state alias),
      detail (product + variants{amount,qte} + commande prefill)
- [x] Probe: POST product = **direct order** (alert « Commande passée avec
      succès », order state « en-cours »); cart stays empty — the cart
      screens of the original app do not participate here
- [x] Theme: new accent family `plus` (sarcelle ~180°, light+dark, contrast
      checked), used by the Plus tab and every boutique route
- [x] Nav: bottom bar 6 destinations — Actualités leaves the bar for the new
      **Plus** tab root; Plus screen = two cards wearing their destination's
      accent (Actualités amber, Boutique teal); `accentDe` maps
      plus/boutique/boutique-historique and `boutique/…` prefixes
- [x] Boutique catalogue: rubrique chips (server list, « Tout » = -1),
      local search, 2-column product grid (signed images, display price
      « 250 DH »), skeleton, empty + error states, bandeau on stale content
- [x] Boutique detail: variant chips (price + stock echo), quantity stepper
      bounded 1..stock, comment field, total on the button; confirmation
      dialogue **before** the POST (orders are immediate); server alert
      shown on success; `can_add_to_cart` = false disables the button
      (unless editing an order — `commande` mode, server decides)
- [x] Boutique history: order cards (date, state chip — « validée » wears
      the Registre green, price, articles with image/size/qty), per-article
      edit (pushes detail with `commande` prefill) and delete, order delete
      — all gated by the server's `can_edit`/`can_delete` flags, confirmed
      before deletion
- [x] Registre: hamburger drawer (ModalNavigationDrawer) with Mes demandes
      and Paramètres — the two bottom list rows are gone; the drawer closes
      before navigating; routes unchanged
- [x] Repo signal `commandesChangées` (like `quizEnJeu`): catalogue and
      history refresh when an order is placed or deleted
- [x] Tests: BoutiqueTest (normalizers of the probed shapes, prix unitaire,
      button label, local filter) — 108 total, 0 failures
- [x] Docs synced: DESIGN.md (§2 accents, §3 nav + drawer, §4 Plus/Boutique,
      §6 scope), NAVIGATION.md (Plus + tiroir), CHANGELOG « Non publié »,
      AGENTS.md structure table, ENDPOINT-MAP.md « shop »
- [x] Probe follow-up (beta 2): « Repas invité » is canteen planning, not
      products — rubrique 2 returns `products: []` + `cantines[]` (day,
      availability with server colors, price, « Réserver »/« Réservé 1/1 »);
      the meal IS shop product 25, orderable through the same verified POST
      (probed: order created then deleted; day carried by the `comment`
      field — the only client-side mention; « Réservé » counts validated
      orders only, app 2.4.14 has no reserve button at all)
- [x] Registre shortcut: « Repas invité » card (section Cantine, sarcelle
      accent) → day picker → confirmation dialog (day read back black on
      white) → same POST; success alert from the server
- [x] Boutique rubrique 2: the same planning replaces the false « Boutique
      vide » empty state
- [x] Tests: +3 (repas comment formatting, server-hex color parser, day
      label) — 111 total, 0 failures
- [x] On-device check: open Boutique from Plus, place a real order, edit
      and delete it from history; reserve a Repas invité from the Registre
      shortcut — TODO on the beta 2 build

## Branch `bibliotheque-documents` (issue #43)

The maths teacher published a document on 2026-09-22, visible in the official
app but absent from GWS+. Diagnosis by live read-only probes (GET only,
`diff_*.json` out of the repo): the parent resource space splits into three
sections served by three endpoints — **Bibliothèque** (`bibliotheque`), where
teacher documents live; **Cartable numérique** (`cartable_numeriques`, digital
textbooks — template data on this account, out of scope here); **Exercices
interactifs** (`ressources_v2`, the quiz feed — the only one GWS+ rendered).

- [x] Probe: `bibliotheque` bare → `unites[]` (matières, `count_resources`
      as string, `has_new`); compared against the 18/09 capture — the unit
      Mathématiques appeared since then with 1 resource (the new doc)
- [x] Probe: `bibliotheque?unite=<id>` → `data[]` fiche cards (`title`,
      `categorie`, `date`, `by`, `color` without `#`); `file.link` is a bare
      filename — `download?link=<bare name>` answers a PHP « No such file »
      error, so the list alone cannot open the file
- [x] Probe: `ressource_details?ressource=<id>` (French spelling; `resource`
      answers a PHP error) → detail carrying **signed media URLs** in
      `files[]` — the download path
- [x] Probe: `download?link=<absolute URL>` streams remote files (verified
      on a cartable textbook URL) — the proxy is URL-based, the bare list
      filename is simply not a server path
- [x] Repo: `bibliotheque()` (units → per-unit fiches, one unpaged GET each,
      unit failure isolated), `unitesBibliotheque()`, `fichesDeUnite()`,
      `détailFiche()`, cache `caches.bibliotheque` (session-stamped)
- [x] Normalizers: `uniteBibliotheque` / `ficheBibliotheque` /
      `détailBibliotheque` / `fichierRessource` — bare filenames never
      become attachments; detail `description` blank → null
- [x] ViewModel: both sources loaded in parallel, one failure never hides
      the other (non-blocking banner joins the messages, issue #21 pattern);
      cache prefill for the fiches too; `téléchargerFiche` = detail →
      signed URL → `Fichiers.télécharger` → open via FileProvider
- [x] UI: fiches grouped by matière **ahead of** the exercises in each
      group, subtitle « date · Par <enseignant> », « Bibliothèque » chip,
      download button with spinner and inline « Fichier indisponible » on
      failure; nature filter: fiches count as Documents, hidden under Quiz;
      item keys now carry the matière (ressources_v2 repeats one id per
      matière — the old keys could collide in the LazyColumn)
- [x] Tests: BibliothequeTest (units, fiches, detail signed files, bare-name
      rejection, filter) — all green
- [x] Docs synced: ENDPOINT-MAP (bibliotheque + ressource_details verified
      sections, cartable note), CHANGELOG « Non publié », AGENTS.md status
- [x] On-device check: the maths doc visible under Mathématiques in the
      Documents tab, download opens the PDF in the system reader

## Branch `mises-a-jour` (issue #46)

GWS+ is sideloaded — nothing told parents a new version existed. Baked-in
update system against the public GitHub Releases API (no server, no
Obtainium, no FCM).

- [x] Probe: real API shape confirmed (`tag_name`, `prerelease`,
      `assets[].browser_download_url`); all existing beta releases were
      **not flagged prerelease on GitHub** — re-flagged 2026-09-22
      (`gh release edit --prerelease`), else the stable channel would have
      offered betas
- [x] `UpdatesRepository`: unauthenticated GET (60 req/h/IP, 12 h throttle
      in DataStore), semantic version parsing/comparison
      (`0.7.1-beta.1 < 0.7.1`, tolerant of the `v` tag prefix), release
      picking by **highest parsed version** (list order not trusted — a
      stable can sort after its own beta), APK asset isolation
      (`GWS-*.apk`, .idsig ignored), persisted last-seen publication so the
      card survives process death under throttle
- [x] Channels: stable-only by default; « Participer aux bêtas » opt-in
      toggle in Paramètres re-checks on switch
- [x] UI: « Mise à jour disponible » card at the top of the Registre flow
      (version, notes summary, one-click « Mettre à jour » → download via
      direct link → system installer; « Installer » re-fire after leaving
      the installer; « Réessayer » + inline error on failure; « Voir sur
      GitHub » opens the release page) and the same card in Paramètres
- [x] Permissions: `REQUEST_INSTALL_PACKAGES` (declarative), « apps
      inconnues » system settings opened automatically when missing;
      `POST_NOTIFICATIONS` runtime-requested from a Paramètres row (13+)
      — local notification posted only when granted
- [x] Honest limits documented: no push while the app is fully closed
      (that would need a server or FCM) — the daily app-open check covers it
- [x] Tests: MiseAJourTest (version parsing/ordering, channel picking
      incl. order-independence, GitHub JSON parsing, APK asset isolation,
      notes summary) — 125 total, 0 failures
- [x] Docs synced: AGENTS.md, CHANGELOG « Non publié », SECURITY-NOTES
      (GitHub third-party host note), ROADMAP (this section)
- [x] On-device check: install the beta, publish a newer release, verify
      the card + notification + one-click install end-to-end

## Branch `composeur-ia` (issue #56)

AI writing assist in the message composer, Apple-Writing-Tools style.
BYOK against any OpenAI-compatible provider — no server of ours, no
school data beyond the text the parent chooses to transform.

- [x] `data/ai/ComposeurIA.kt`: provider presets (OpenRouter, Groq, DeepSeek,
      Mistral AI, Together AI, Fireworks AI, Cerebras), `ActionIA`
      (Relire/Réécrire/Résumé/Points clés/Tableau/Liste) + `TonIA`
      (Amical/Professionnel/Concis) with French labels, system-prompt builder
      (French output, accents, no preamble), single-shot
      `POST {base}/chat/completions` with `Authorization: Bearer`, low
      temperature, `choices[0].message.content` extraction
- [x] Settings in `SessionStore` (app preferences, survive session purge):
      active flag, base URL, model, BYOK API key (never logged — F3), default
      tone; single `réglagesIA` flow + setter
- [x] `ui/screens/messages/PanneauIA.kt`: floating rounded panel above the
      composer — « Décrivez votre modification » field, quick actions
      Relire/Réécrire, tone chips, transformation icon row (Résumé/Points
      clés/Tableau/Liste), result shown in-panel with Remplacer / Réessayer /
      Annuler (retry replays the same action)
- [x] Composeur: ✨ button (`AutoAwesome`) right next to the mic (issue
      placement requirement), only when the feature is configured and ready;
      wired from both `ConversationScreen` and `NouveauMessageScreen` via the
      session's `réglagesIA` flow
- [x] Paramètres « Assistant IA » section: on/off toggle, default tone,
      provider preset chips + free-form base URL (« Autre »), model field,
      masked API key field
- [x] Tests: ComposeurIATest (prompt building, empty/blank consigne, preset
      coverage, `prête` gating, OpenAI response extraction incl. invalid
      bodies) — all green
- [ ] On-device check: configure a real provider + key, run Relire/Réécrire
      and each transformation on a draft, verify the ✨ button hides when the
      feature is off or unconfigured

## Branch `badge-ia` (issue #58)

Reusable « Généré par IA » marker for every AI-produced surface (composer
outputs of #56, conversation summary of #57).

- [x] The reference SVG (512×512) was a rasterized vector trace (thousands of
      1-unit segments) — redrawn clean as a Compose `ImageVector`:
      rounded square (stroke) + monoline "AI" glyphs + filled 4-branch
      sparkle bottom-right; theme-tinted (`tint`), never hard black
- [x] `ui/components/BadgeIA.kt`: `IcôneGénéréIA` vector, `PuceIcôneIA`
      (compact icon-only, contentDescription « Généré par IA ») and
      `BadgeGénéréIA` (chip: icon + label, `compact = true` falls back to
      the icon alone) — AnnotationShape chip, sage background, labelSmall
- [x] Consumed by the AI panel: the badge sits under the result actions in
      `PanneauIA.kt`; #57 will consume the same component
- [ ] On-device check: badge visible under every panel result, light + dark

## Panel redesign (after real-device trial of beta 3)

User feedback on the shipped beta: the panel was too tall/visually heavy,
ambiguous in places. Redesigned as a compact bottom sheet:

- [x] Actions renamed for clarity: **Corriger** (spelling only) / **Réécrire**
      (reformulate) as the two primary actions; the unlabeled icon-only row is
      replaced by explicit labeled transformations **Raccourcir / Développer /
      Structurer / Simplifier**, tucked behind a drag handle (up = expand,
      down = collapse; tap toggles too) — closed height ≈ 250 dp
- [x] Tones: **Amical / Professionnel / Neutre** (« Concis » dropped — it was
      length, not tone; stored CONCIS falls back to Amical)
- [x] Instruction field is the clear starting point: outlined container,
      label « Que voulez-vous modifier ? », example placeholder « Ex. Rends
      ce message plus professionnel »
- [x] Message preview at the top (« Message sélectionné », first 90 chars)
      so it's obvious which draft is being edited in a long chat
- [x] Primary action **✨ Générer** runs the selected action — the panel is
      now a select-then-generate model, not tap-to-run
- [x] Accent consistency: selected chips + primary button use the tab accent
      (the pink of the ✨), not ink/sage mixing
- [x] The two-stage compact-menu idea was folded into the sheet itself: the
      collapsed sheet (what opens on tap) already shows only field + Corriger/
      Réécrire + tones + Générer; transformations live one drag away
- [x] Tests updated for the new enum sets — all green
