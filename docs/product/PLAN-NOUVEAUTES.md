# PLAN — Milestone « Nouveautés » (tab, post_view, latest-article link)

Implementation spec for the Nouveautés milestone. Agent-facing (English).
Decisions taken with the user on 2026-09-20. Facts marked **VERIFIED** come
from a read-only probe (2026-09-20, `~/dev/shape-probe/post_view.json`,
`nouveautes_p1/s10/s11.json`) and from the official bundle v2.4.14
(chunks 1901 list / 8562 detail / 8853 pinned). Never copy probe values into
this repo — shapes and field names only.

## Context

News currently exist only inside the Registre daily feed: no paginated list,
no bookmark/read-badge/author display, detail bodies stream the ~19 MB
`admin_nouveautes` dump, and detail attachments are inert text rows. The
official app makes Nouveautés the parent landing page: a paginated list
(5th tab equivalent), and a per-post detail fetched via `post_view`.

## Verified API facts

### GET `nouveautes` — list (pagination VERIFIED 2026-09-20)

- Params: standard envelope + `start`/`limit`. The server is **1-based**:
  `start=10&limit=10` re-serves the last item of page 1 (overlap 1);
  `start=11&limit=10` is a clean next page (overlap 0). The bundle's
  `start = accumulated length + 1`, `limit = 10` is therefore correct.
- Merge rule (bundle): `start == 0` → replace; else append.
- Pull-to-refresh (bundle): refetch `start=0, limit=current length`, replace.
- Item fields (all confirmed): `id, title, categorie, date, image, intro,
  bookmark ("bookmark" | null), permitComments, permitNewComments,
  permitQuiz (bools), quiz[], user, reponses, file, file_test, files`
  (file/file_test/files/reponses/user were null on the whole sample).
- `intro` is the « Vu le » read badge as HTML (double-check icon + grey
  text) — render as plain text.
- `bookmark == "bookmark"` → orange bookmark icon; **display-only, no toggle
  endpoint exists anywhere in the bundle**.
- Top level: `p_count`, `im_here`, `empty/empty_icon/empty_text`,
  `translation{title, label_author}`.

### GET `post_view` — per-post detail (VERIFIED 2026-09-20, read-only)

- Params: standard envelope + `post=<id>`. Called on every open by the
  official app; the visit is the de-facto mark-as-read (the « Vu le » badge
  then shows up in the list `intro` on next fetch). **No mark-read endpoint
  exists.**
- Top keys: `data, empty, empty_icon, empty_text, quiz, translation,
  preventScreenshot(false)`.
- `data` keys: `canSendComment` (null observed — falsy), `commentaires[]`
  (empty observed), `images[]` (empty observed), `post{}`.
- `data.post` keys: `id, title, categorie, date, description (full HTML),
  image, permitComments, permitNewComments, permitQuiz, quiz[], files,
  file, file_test, reponses, user, DatePublication, DateExpiration,
  Parents, cat, promo, visible` — `files/file/file_test/reponses/user` null
  on the sample (tolerant parsing required).
- `translation`: `commentaires, download, hideMore, label_author,
  no_commentaires, seeMore, sousCommentPlaceholder, votre_commentaire`.
- Top-level `quiz[]` — the detail page's answer-state container (bundle).
- `admin_nouveautes` is NEVER called by the official parent app — keep the
  existing streamed-corps path only as **fallback** when `post_view` fails.

### Comments & post quizzes (write paths — bundle-derived, UNVERIFIED live)

- Comment object: `{img, nom, commentaire, date,
  sousComment{isOpen, comments[{img, nom, comment, date}]}}`.
- Add comment: **POST `nouveautes`** `{commentaire, post, eleve_id, user_id,
  parent_id, key}` (+ `replyTo: <comment.id>` for a reply); response
  `{commentaire:{…}}` pushed locally, no refetch.
- Post quiz: questions `{label, reponses[strings], alias, permitAnswer, res}`;
  answer = **POST `nouveautes`** `{alias_question, res, post, eleve_id,
  user_id, parent_id, key}`; completion alert when every question has `res`.
- Gates: `permitComments` (section), `permitNewComments` (form),
  `canSendComment` (replies), `permitQuiz` (quiz UI).
- **All 10 probed posts have every flag false** — no live verification is
  possible today. Ship the write paths **gated OFF** (composer precedent:
  session kill switch, enabled only after one live verification).

## Decisions

1. Dedicated paginated list = **5th bottom tab « Actualités »** (order:
   Registre, Actualités, Devoirs, Documents, Messages).
2. Comments + post-quiz POSTs implemented **gated** (kill switch default
   OFF until one live verification succeeds).
3. **« Dernière actualité » card in the Registre**: most recent post by date
   (even when not published today), tappable → `post/{id}`; hidden when that
   post is already in today's feed.
4. `pinned_posts` **dropped** — deep-link-only page needing a `category` id;
   the official list never merges pinned posts.

## Tasks

### 1. Probes → docs (DONE in this branch's spec; map update in the PR)

- [ ] `docs/api/ENDPOINT-MAP.md`: new `post_view` section (shapes above,
      move out of the unverified table); nouveautes pagination note
      (1-based start, `len+1` rule, pull-to-refresh replace); pinned_posts
      decision note; comments/quiz POST fields (bundle-derived, unverified).

### 2. Models — `model/Models.kt`

- [ ] `Post` += `bookmark: Boolean = false`, `auteur: String? = null`,
      `permitComments/permitNewComments/permitQuiz: Boolean = false`.
- [ ] New `Commentaire(auteur, texte, date, image, sousCommentaires)`.
- [ ] New `QuestionPost(alias, label, réponses: List<String>, réponseChoisie)`.
- [ ] New `PostDetail` (post fields + `descriptionHtml`, `files`:
      `List<Attachment>`, `images: List<String>`, `commentaires`,
      `peutCommenter`, `peutNouveauCommentaire`, `peutRépondre` (canSendComment),
      `questions: List<QuestionPost>`).

### 3. Normalizers — `data/repo/Normalizers.kt`

- [ ] `post()`: parse `bookmark` (`== "bookmark"`), `user.nom` (tolerant),
      the three permit flags; **fix the multi-file truncation** —
      `.firstOrNull()` on `MediaUrls.piècesJointes` drops all but the first
      pair of a comma-joined link (devoirs keep them all, line ~62).
- [ ] New `postDetail(raw)` — tolerant: missing/null `commentaires`,
      `images`, `files`, `quiz`, `canSendComment` (null = false);
      description only from `post_view`.
- [ ] New `commentaire(raw)` (+ nested `sousComment`).
- [ ] Pure pagination helper `fusionner(anciens, nouveaux, départ)`:
      `départ == 0` → replace, else append.

### 4. Repository — `data/repo/NouveautesRepository.kt` + `data/cache/`

- [ ] `liste(départ: Int = 0, limite: Int = 10)` (params per the 1-based rule).
- [ ] `détail(postId): PostDetail` — GET `post_view`; on failure or
      ill-shaped response, fall back to list item + streamed
      `admin_nouveautes` `corps()` (existing `extraireCorps` untouched).
- [ ] `commenter(postId, texte, replyTo?)` and
      `répondreQuestionQuiz(postId, alias, réponse)` — POST `nouveautes`;
      callers must check the server flags AND the kill switch.
- [ ] `CachesSession` gains `posts: MemoireSession<List<Post>>` (accumulated
      list, same `clé()` stamping; login/logout purge already generic).
- [ ] `dernière(): Post?` — most recent by date from the first page.

### 5. ViewModels — `ui/AppViewModels.kt`

- [ ] `ActualitesÉtat/ActualitesViewModel`: `liste`, `finAtteinte` (empty
      page disables load-more), `chargement/rafraîchissement/erreur`,
      `charger(force)` (cache prefill → background refresh, issue-#21
      contract), `pageSuivante()` (`start = length + 1`), `rafraîchir()`
      (refetch 0..length, replace).
- [ ] New `PostDetailViewModel` — decouples detail from
      `RegistreÉtat.registre.entrees` (today's dependency); exposes
      `PostDetail` + loading + error.
- [ ] Kill switch (composer pattern: DataStore setting in `SessionStore`,
      default OFF, toggle with confirmation) gating comment composer and
      quiz answers; UI also requires the server flags.

### 6. UI

- [ ] `ui/AppNav.kt`: `Onglets` += `Onglet("actualites", "Actualités", …)`;
      routes `actualites`; back contract untouched (tab root → Registre,
      predictive back regression pass).
- [ ] Extract the private `CarteActualité` from `RegistreScreen.kt` into a
      shared component; extend: bookmark icon (orange #fc942d), `intro`
      « Vu le » badge as plain text, author row (hidden when null).
- [ ] New `ui/screens/actualites/ActualitesScreen.kt`: LazyColumn, infinite
      scroll (last item → `pageSuivante()`), refresh action, `EmptyState`
      from server `empty_text`, `SqueletteActualites` (new, from
      `BlocSquelette`), `BandeauErreur` + Réessayer.
- [ ] `PostDetailScreen`: switch to `PostDetailViewModel`; body stays
      `htmlToPlainMultiline()`; images LazyRow gallery when `images[] > 1`;
      files become clickable rows → `Fichiers.télécharger` + FileProvider
      open (reuse the Devoirs pattern); comments list (read);
      gated composer row (reply support); gated quiz cards (buttons,
      local `réponseChoisie`, « Merci ! » completion alert).
- [ ] `RegistreScreen`: « Dernière actualité » `SectionLabel` + card under
      the header (before « Aujourd'hui »), from `dernière()`; hidden when
      the latest post id is already in today's feed; tap → `post/{id}`.

### 7. Tests — `app/src/test/java/school/greenwood/plus/`

- [ ] `NormalizersPostTest.kt` (JUnit4, `buildJsonObject`, backtick French
      names): bookmark/author/permit parsing; multi-file attachments;
      `postDetail` parse (tolerant nulls); `commentaire` (+ sousComment);
      `fusionner` (replace at 0, append, overlap-free join);
      `dernière()` selection.

### 8. Docs & delivery

- [ ] `README.md` (Actualités tab + latest-article card), `ROADMAP.md`
      (TASKS section), `CHANGELOG.md`, `AGENTS.md` structure/verify rows.
- [ ] Branch `nouveautes-onglet`, French imperative commits, PR referencing
      the milestone issue, stating verified vs unverified.

## Verification

- `./gradlew testDebugUnitTest` and `./gradlew :app:assembleDebug` green.
- Real-device pass: 5-tab back-gesture contract unchanged; page joins
  without duplicates (start = length + 1); pull-to-refresh replaces;
  « Dernière actualité » tap-through and no duplicate when today's feed
  holds it; detail images/files open; « Vu le » badge appears after opening
  a post then refreshing; skeleton → content; airplane-mode banner.
- Write paths stay hidden until the school publishes a post with
  `permitComments`/`permitQuiz` true; then one live comment + one quiz
  answer before flipping the default ON.
- Accent audit on new French strings; no personal data, no tokens.
