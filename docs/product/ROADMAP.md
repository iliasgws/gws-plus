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

- [ ] Read-only first: is the timetable even in the API? (`cours_v2`
      inner `seances[]` — still on the unverified list)
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

## Direction visuelle « liquid glass » (branch `design/liquid-glass`)

Full visual restyle: the ink-and-paper theme becomes liquid glass — aurora
backdrop, translucent glass sheets, floating frosted capsule tab bar. The
navigation/back-stack contract (DESIGN.md §3) is untouched: same routes,
same tab gesture, same transitions, `enableOnBackInvokedCallback` untouched.

- [x] Library verified live on Maven Central:
      `io.github.kashif-mehmood-km:backdrop:0.0.1-alpha02` (KMP Liquid Glass,
      port of Kyant0's AndroidLiquidGlass). All library usage confined to
      `ui/components/Glass.kt` — any API churn is a one-file fix
- [x] Tokens: `GlassPalette` (card / bar / barStrong / stroke / auraA-B-C)
      nested in `GwsColors`; `paper` repurposed as the aurora base; `chalk`
      darkened (`#5F6F63`) to hold ≥ 4.5:1 through glass; `redPen` semantics
      untouched everywhere
- [x] Shapes: pages 24 / sheets 28 / bubbles 18 / controls capsule (50 %);
      `AnnotationShape` removed, 4 call sites migrated
- [x] Glass primitives: `AuroraBackdrop` (three static blobs, `drawBehind`,
      never animated), `GlassSurface` (translucent fill + 1 dp luminous
      stroke, `strong` = 95 % fallback), `GlassBottomBar` (real backdrop
      sampling via `drawBackdrop`; `<` API 31 renders the barStrong
      capsule, designed fallback)
- [x] Shell: one `layerBackdrop` capture at the AppNav root (aurora +
      screens); the floating glass bar is a sibling outside the captured
      layer so it never samples itself; Scaffold kept (transparent, empty
      bottomBar) purely for insets; screens receive bottom padding =
      nav inset + `GlassDefaults.BarTotal` and scroll under the bar
- [x] Second pass (beta-1 feedback: no refraction, no overlaid components):
      shared real-glass primitive `FeuilleVerre` (vibrancy + blur + lens via
      `drawBackdrop`, opaque fallback when no capture or < API 31) plus
      `LocalGlassBackdrop` (scene provided once at the AppNav root) — applied
      to the floating bottom bar (full liquid stack: vibrancy + blur 18 +
      lens 12/24), the composer, the « Ce soir » hero card (sage tint kept,
      ink liseré kept) and the login card; the Registre date header becomes
      a floating glass bar the feed scrolls UNDER (real component overlay);
      calm feed cards stay fill-only on purpose (nothing with edges behind
      them — refraction would be invisible and cost per-frame shaders)
- [x] Crash fix (beta-2 on-device report: « skeleton shows, then crashes »):
      a glass sheet must never sample a capture that CONTAINS it — the root
      `layerBackdrop` re-records its subtree (`recordLayer { drawContent() }`),
      so in-scene glass (hero card, floating header, composer, login) drawing
      `drawLayer(sceneLayer)` while that scene layer was being recorded made
      a self-referential GraphicsLayer → crash on the first content frame
      (skeleton had no glass, hence the timing). Fix: two captures at the
      AppNav root — `fondScene` (aurora + screens, read ONLY by the bottom
      bar standing outside it) and `fondAurore` (the aurora node alone, read
      by all in-screen glass via `LocalGlassBackdrop`); the rule is
      documented on `LocalGlassBackdrop` and in DESIGN.md §2
- [x] Screens: all 13 converted — transparent roots; day chips, nature
      filters, message categories, search field and action button extracted
      into shared `PuceChoix` / `ChampRecherche` / `GwsBouton`; quiz answers
      keep solid semantic fills (sage correct / redPen wrong — a verdict is
      read, not seen through); admin bubbles stay solid sage, parent bubbles
      and pending-send bubbles go glass; modal sheets use `barStrong` (own
      window, cannot sample); skeletons converted to glass
- [x] Docs: DESIGN.md §2 rewritten (material rules + new token tables),
      §5 theme bullet updated; CHANGELOG `[Unreleased]`; this section
- [x] `assembleDebug`, `assembleRelease` (minified) and `testDebugUnitTest`
      green
- [ ] (!) real-device pass (no emulator in the build environment): blur
      visible on the floating bar (API 31+), lens refraction (API 33+),
      `<` 31 fallback legible, contrast on glass in both themes, back
      gesture from every section unchanged
