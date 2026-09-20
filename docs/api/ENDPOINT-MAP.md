# Endpoint map (parent app)

> Shapes observed **2026-09-18** via a read-only probe on one parent account;
> single-account sample, treat as indicative not exhaustive.
>
> The raw probe files live outside the repo (`~/dev/shape-probe/`) and
> contain personal data (names, phones, ids, signed URLs) — never commit
> them, never copy their values into any doc. Everything below uses
> placeholder values only.
>
> Companion docs: [`BOTI-API.md`](BOTI-API.md) (protocol, auth, quirks) and
> [`ENDPOINTS.md`](ENDPOINTS.md) (full 100-endpoint inventory from the APK).

Base URL for every endpoint below:

```
https://boti.education/p/greenwood/botiapi/<endpoint>
```

## Preamble: quirks that shape every response

1. **HTTP 200 even for errors.** The status code carries no information —
   branch on the envelope: `error`, `status`, `msg`, `disconnect`. Example:
   the homework-submission endpoint answers a body-level
   `{"status": 202, "message": "Travail envoyé"}`; login errors come back as
   `{"error": true, "msg": "…"}` — all with HTTP 200.
2. **`"disconnect": true` kills the session.** Any response containing it
   means the server dropped the session — navigate to login. Implement once,
   in the API client (see `BOTI-API.md`).
3. **Display-ready French dates.** The server pre-formats dates per field,
   each field with its own format. Machine-parseable dates are the
   exception, not the rule:

   | format | example | seen in |
   |---|---|---|
   | `YYYY-MM-DD` (ISO) | `2026-06-22` | `date_remise`, `next_week`, `last_week`, `datenaissance` |
   | `YYYY-MM-DD HH:MM:SS` | `2026-09-09 09:32:05` | message `datetime`, `vu_le`, devoirs `date` |
   | `DD Mon YYYY` | `18 Sep 2026` | `objects.found`, `envoye_le` fragments |
   | `DD/MM/YY HHhMM` | `07/09/26 13h59` | demandes `created_at` / `updated_at` |
   | `DD Septembre YYYY` | `07 Septembre 2026` | demandes `date` |
   | `📥 le DD/MM/YYYY à HH:MM` | `📥 le 01/09/2026 à 12:47` | nouveautes `date` |
   | `Le DD Mon YYYY` | `Le 14 Sep 2026` | cours_v2 day labels |

4. **`media.boti.education` signed URLs are double-encoded** — decode
   exactly **once** (keep `%2B`, `%2F`, `%3D` literal on the first pass).
   URLs expire after ~15–20 minutes. A few fields (default avatars, school
   icons) point at plain `boti.education` asset paths instead — those are
   not signed.
5. **`paltform` (sic) is mandatory.** The typo is the official spelling; the
   server expects it. Never "fix" it.
6. **List envelopes share a skeleton.** Nearly every list endpoint returns
   `data` (list or object), `empty` (bool), `empty_icon`, `empty_text`
   (HTML — `<br>` allowed), and a `translation` object with the screen's
   French strings. Render empty states and labels from `translation`
   instead of hardcoding.
7. **Sloppy keys and mixed types.** Raw devoirs items carry the due date
   under the key `"de "` — with a **trailing space**. `stats_retards.total`
   is a string while `stats_absences.total` is an int. June is abbreviated
   `Jui` (not `Juin`). duplicated consecutive messages exist server-side.
8. **Pagination/echo fields.** Lists echo context back: `p_count`,
   `im_here`, `eleve` (display name of the selected élève), `start`/`limit`
   or `page`.

## Standard request envelope

| param | value | notes |
|---|---|---|
| `user_id` | from login / acces_check | required |
| `key` | `keyToken` from login / acces_check | required — the session key |
| `parent_id`, `eleve_id` | from login / acces_check | sent by the official app; the probe's read calls succeeded with `user_id` + `key` only |
| `versionCode` | `24140` | |
| `versionNumber` | `2.4.14` | |
| `paltform` | `android` | typo mandatory |
| `lang` | `fr` | |

Per-endpoint extras observed in the probe: `start` + `limit` (devoirs,
nouveautes), `page` (messages, demandes, absences), `search` (objects,
ressources_v2), `id` (nouvelle-demande), `date` (devoirs_date_v2 — see that
section: not a listing filter).

Every call listed below was a GET unless marked POST, carrying the standard
envelope plus the extras noted, and returned the shape shown.

---

## acces_check — session bootstrap/validation

**GET** `acces_check` — probe params: standard envelope only
(`acces_check.json`).

The call that validates (and refreshes) a stored session. It returns a
**fresh `keyToken`** plus the full account payload. Call it once at app
start with the stored `user_id` + `key`; on success, replace the stored key
with the fresh one.

Top-level keys on success: `keyToken`, `userId`, `parentId`, `eleveId`,
`lang`, `dir`, `user_lang`, `role`, `_acces`, `cycle`, `eleves`, `parent`,
`params`, `translation`, `color`.

```json
{
  "keyToken": "<40 hex chars>",
  "userId": "<id>", "parentId": "<id>", "eleveId": "<id>",
  "lang": "fr", "dir": "ltr", "user_lang": "fr",
  "role": "parent", "_acces": "parent", "cycle": "lycee",
  "params": [null, false],
  "color": { "main": "#1ba58a", "rgb": [27, 165, 138] }
}
```

`eleves[]` item (one per child; one élève in the probe sample):

```json
{
  "id": "<élève id>", "niveau_id": "<id>", "classe_id": "<id>",
  "site_id": "school", "cycle_id": "<id>",
  "nomcomplet": "<prénom nom>", "prenom": "<prénom>", "nom": "<nom>",
  "prenomar": "<prénom arabe>", "nomar": "<nom arabe>",
  "datenaissance": "<YYYY-MM-DD>", "sexe": "Homme",
  "niveau": "<libellé du niveau>",
  "img": "<signed media.boti.education URL>",
  "parent_nom": "<nom du parent>",
  "parent_image": "<URL — peut être un asset par défaut>"
}
```

`parent` object: `id`, `nomcomplet` **and** `nomComplet` (both spellings are
sent — parse either), `tel`, `email`, `sexe`, `image`.

`translation` object: `versionAr` (bool), `menu[]` — the official app's
navigation config, `{icon, label, path, visible}` with `visible` a bool
**or null** (null = hidden/conditional); some entries carry `count` — and
`conf_terms_link`. Note the menu path `/parent/objets-perdus` — the official
app itself labels the `objects` feed « Objets perdus ».

Notes:

- Success carries **no** `error`/`disconnect` wrapper — the payload *is* the
  response. Behaviour on an invalid/expired key: UNVERIFIED — do not rely.
- Default parent avatar can be a plain boti.education asset (e.g. a generic
  icon), not a signed media URL.

## login — authentication

Cross-reference: full shape in [`BOTI-API.md`](BOTI-API.md)
(« Authentication → Login »). POST multipart (`login`, `password`,
`privacy`, `remembreMe` (sic)) → `keyToken`, `userId`, `parentId`,
`eleveId`, `eleves[]`, `parent`, `_acces`, `role`. The success payload
largely mirrors the `acces_check` body. Errors arrive as
`{"error": true, "msg": "…"}` with HTTP 200.

## devoirs — homework (cahier de liaison)

**GET** `devoirs` — probe params: `start=0`, `limit=30` (`devoirs.json`).

The server **ignores any date param** here — there is no server-side date
filter. For a day view, fetch and filter client-side on `date_remise` from
the parsed buckets.

Top-level keys: `data`, `empty`, `empty_icon`, `empty_text`, `translation`,
`devoirs_remettre`, `devoirs_ancien`, `restricted`.

`data[]` — raw items:

```json
{
  "id": "25058",
  "title": "<titre>",
  "categorie": "Devoir individuel",
  "date": "2026-06-11 09:53:00",
  "description": "",
  "file": {
    "text": "Télécharger la pièce jointe",
    "link": "<Google Docs viewer URL wrapping the double-encoded media URL>",
    "download_icon": "<URL>"
  },
  "intro": null,
  "user": null,
  "image": null,
  "testar": "ar",
  "de ": "2026-06-22"
}
```

- `date` = publication datetime; `"de "` (key with **trailing space**, sic)
  = the due date, ISO.
- `file.link` is a `docs.google.com/viewer?url=…` wrapper around a
  double-encoded media URL — unwrap and decode once to get the PDF.
- `file` was non-null on both observed raw items; nullability unverified.

`devoirs_remettre[]` / `devoirs_ancien[]` — parsed cards:

```json
{
  "id": "25058",
  "promo": {},
  "title": "<titre>",
  "categorie": "Devoir individuel",
  "date": { "mois": "Jui", "jour": "22", "jourLabel": "Lundi", "annee": "2026" },
  "publication": "Publié le 2026 Juin 11",
  "date_remise": "2026-06-22",
  "description": "",
  "matiere": "<matière>",
  "enseignant": "<nom>",
  "intro": null,
  "user": null,
  "devoir_fait": { "files": [], "file_sent": false, "fait": false }
}
```

- **`date_remise` is the due date (échéance)**, ISO; `date`/`publication`
  describe the publication. This resolves the date semantics for the
  day view: filter on `date_remise`.
- `devoir_fait` drives the submission state: `fait` (bool), `file_sent`
  (bool), `files` (uploaded attachments list).
- Bucket semantics: `devoirs_remettre` = « Devoirs à remettre » (upcoming),
  `devoirs_ancien` = « Devoirs passés ». In the probe `devoirs_remettre`
  was `[]`; both buckets share the item shape (verified on
  `devoirs_ancien`).
- `translation`: `label_devoir_td` « Devoirs à remettre »,
  `label_old_devoir` « Devoirs passés », `title` « Devoirs ».
- `restricted` `{restricted, label, contact}` gates the whole tab when the
  school blocks it.
- Month abbreviations are non-standard: June arrives as `Jui`; `promo` was
  an empty object in every observed item.
- `empty_text` is HTML: « Vous n'avez aucun devoir <br> pour le moment ».

## devoirs_date_v2 — homework submission (POST)

**POST** `devoirs_date_v2`. **Not a date selector and not a listing
endpoint.**

Verified 2026-09-18: a POST to this endpoint is treated as a work
submission. Even a stray call carrying only the standard envelope plus
`date=DD/MM/YYYY` (no devoir id, no attachment) was accepted:

```json
{ "status": 202, "message": "Travail envoyé" }
```

The exact multipart fields of a real submission (devoir id, files, flags)
are UNVERIFIED — do not rely; read them from the official bundle before
implementing. The date-filtered listing is done by **GET `devoirs`** (the
server ignores the date param) plus a client-side filter on `date_remise`.

## nouveautes — school news list

**GET** `nouveautes` — probe params: `start=0`, `limit=10`
(`nouveautes.json`).

`data[]` items — list cards only. **No description bodies here**: the HTML
body (`description`) only exists in `admin_nouveautes` (~19 MB, see
`BOTI-API.md`).

```json
{
  "id": "25058",
  "title": "<titre>",
  "categorie": "",
  "date": "📥 le 01/09/2026 à 12:47",
  "intro": null,
  "bookmark": null,
  "file": null,
  "file_test": null,
  "files": null,
  "permitComments": false,
  "permitNewComments": false,
  "permitQuiz": false,
  "reponses": null,
  "quiz": [],
  "user": null,
  "image": "<signed media.boti.education URL>"
}
```

Top-level extras: `empty`, `empty_icon`, `empty_text`, `im_here` (true),
`p_count` (echoed count — 10 for `limit=10`; exact semantics unverified),
`eleve` (display name of the selected élève), `Questionnaires` (array of
nulls, same length as `data`), `getQuestionnaires` (array of empty arrays,
same length), `translation` (`title`, `label_author`).

- `date` is a display string, ready to render as-is.
- `intro` when present is HTML: a small « Vu le … » read badge (double-check
  icon + grey text), never the body.
- `bookmark` is `null` or the literal string `bookmark`.
- `file` / `file_test` / `files` were `null` on every observed item; the
  non-empty shape is UNVERIFIED — do not rely.
- Pagination via `start` / `limit`.

## pinned_posts — pinned news

**GET** `pinned_posts` — probe params: standard envelope only
(`pinned_posts.json`).

Empty for this account:

```json
{
  "data": [],
  "empty": true,
  "empty_icon": "<URL>",
  "empty_text": "Vous n’avez aucune nouveauté <br> pour le moment",
  "im_here": true,
  "p_count": 0,
  "eleve": "<nom affiché de l’élève>",
  "translation": { "title": "Nouveautés", "label_author": "Par" }
}
```

Assumed to return the same item shape as `nouveautes` when non-empty —
UNVERIFIED — do not rely.

## absences — absences and retards

**GET** `absences` — probe params: `page=1` (`absences.json`).

Top-level keys: `data`, `empty`, `empty_icon`, `certif_icon`, `empty_text`,
`translation`, `actions`, `stats_absences`, `stats_retards`, `hide_tabs`,
`can_justify`, `number_absences`, `show_number_absences`,
`color_number_absences`, `show_number_retards`, `justifiees`,
`non_justifiees`.

```json
{
  "data": { "justifiees": [], "non_justifiees": [] },
  "stats_absences": { "total": 0, "justifie": 0, "nonjustifie": 0 },
  "stats_retards": { "total": "0", "justifie": 0, "nonjustifie": 0 },
  "number_absences": 0,
  "show_number_absences": false,
  "color_number_absences": "red",
  "show_number_retards": true,
  "hide_tabs": true,
  "can_justify": true,
  "actions": [],
  "justifiees": { "empty": true },
  "non_justifiees": { "empty": true },
  "certif_icon": "<URL .svg>"
}
```

- This account had zero absences: `data.justifiees` and
  `data.non_justifiees` were both `[]` — the **list item shape is
  UNVERIFIED — do not rely**.
- The top-level `justifiees` / `non_justifiees` twins are
  `{empty: bool}` flags, not lists.
- `stats_retards.total` is a **string** while `stats_absences.total` is an
  int — normalize client-side.
- `translation` is rich (card titles, justification form labels:
  `ajout_file`, `btn_envoyer`, `motif_absence`, `envoyer_justif`,
  `calendar_title`, `sceance_justifier` (sic), …) — reuse for UI strings.
- `empty_text`: « Aucune absence enregistrée ».

## messages — conversations with the administration

**GET** `messages` — probe params: `page=1` (`messages.json`).

**2026-09-20** — the vendor updated the handler: top-level keys now include
`teachers[]`, `can_send_newmessage` and `hidesend` (absent from the
2026-09-18 probe). During that rollout the endpoint intermittently answered
a raw PHP `print_r` dump (`Models\Inscription Object (…)`) instead of JSON —
request shape was irrelevant (the same params answered clean JSON from a
fresh session while a phone session got the dump). gws-plus now salvages
embedded JSON and retries illisible GETs up to three times (v0.4.1).

`data[]` — one entry per conversation:

```json
{
  "user": "<user id>", "parent": "<parent id>", "is_self": false,
  "id": "<id>", "to": "Administration", "at": "Administration",
  "img": "<URL — école ou avatar>",
  "sujet": "<sujet>",
  "envoye_le": "",
  "vu_le": "",
  "conversation": []
}
```

`conversation[]` items, chronological:

```json
{
  "user": "<user id>", "parent": "<parent id>", "is_self": true,
  "id": "<id>", "message_id": "<id>",
  "to": "Administration", "at": "Administration",
  "img": "<URL>",
  "sujet": "<sujet>",
  "files": [],
  "message": "<texte, retours à la ligne \r\n>",
  "datetime": "2026-09-09 09:32:05",
  "audio": null,
  "envoye_le": " - 09 Sep 2026 à 09:32",
  "vu_le": "2026-09-10 09:51:41"
}
```

Top-level extras: `empty`, `empty_icon`, `empty_text`, `hidesend` (bool),
`themes[]` `{id, label, description}` — message categories (Scolarité,
Suivi pédagogique, Vie Scolaire, Administratif & financier, Service
Transport) — and `translation` (composer strings).

- `is_self` distinguishes parent vs administration messages.
- `envoye_le` is a display fragment; self messages get a `Nom - ` prefix.
- `datetime` is the sortable key; `vu_le` is the read receipt.
- Identical consecutive messages with distinct `message_id` occur
  (server-side double-send, 1s apart) — a display-side dedupe may be
  desirable; do not dedupe by `message_id`.
- `audio` was null throughout; attachment shape of non-empty `files[]`
  UNVERIFIED — do not rely.

## nouveau-message — sending (bundle-verified; live send validated 2026-09-19)

**POST** `nouveau-message` — multipart. Fields read from the official bundle
2.4.14 (reply composer and new-message page). Validated live on 2026-09-19
with a real text message delivered to the administration; the non-empty
`files[]` / `audio` parts and the new-thread response remain unproven live.

**Reply to a thread** (conversation composer):

| Field | Value |
| --- | --- |
| `ref` | thread id |
| `sujet` | thread subject |
| `message` | text (`\r\n` line breaks) |
| `theme` | the thread's own theme id (`theme: this.result.theme`) |
| `files[]` | repeated literal part, one part per file, filename preserved |
| `eleve_id` / `parent_id` / `key` | session values |
| `audio` | raw blob `{file, name: "audio_<epoch_s>.<ext>"}` |
| `index` | thread length **before** the optimistic push |

Response `.message` replaces the optimistic entry at `index` — same shape as
a `conversation[]` item (the app normalizes it with the same parser).

**New thread** (new-message page):

| Field | Value |
| --- | --- |
| `sujet` / `message` / `theme` | free text / free text / chosen from `themes[]` |
| `eleve_id` / `parent_id` / `user_id` / `key` | session values |
| `eleve` | nested `eleve[...]` parts (vestigial — `eleve_id` does the work) |
| `file` | `null` (vestigial — the original uploader is dead code, its 1 MB toast is the only live trace) |
| `files[]` / `audio` | same conventions as the reply path |

Official limit: **1 MB per attachment, new-message path only** (toast
« S'il vous plait choisi un fichier moins ou egale 1MB » in the bundle).
After a successful new-thread send the official app navigates back to the
list; the thread appears on the next `messages` fetch.

## demandes — administrative requests

**GET** `demandes` — probe params: `page=1` (`demandes.json`).

`data[]` items:

```json
{
  "cree": "Réponse",
  "id": "<id>",
  "type": "Attestation de scolarité",
  "created_at": "07/09/26 13h59",
  "updated_at": "14/09/26 15h35",
  "statut": "Traitée",
  "icone": "<URL .svg>",
  "date": "07 Septembre 2026",
  "file": null,
  "reponses": []
}
```

`reponses[]` entries:

```json
{ "label": "Autres détails", "reponse": "<texte>" }
```

- `statut` values observed: « Traitée », « En Cours ».
- `reponses[]` labels observed: « Autres détails », « Date de mise à
  jour », « Réponse ». Entries labelled « Réponse » carry an extra optional
  `file` (null in the probe).
- Every date is a display string (`DD/MM/YY HHhMM`, `DD Septembre YYYY`) —
  no ISO anywhere in this response.
- Top-level: `empty`, `empty_icon`, `empty_text`,
  `translation` {`title`, `download`}.

## nouvelle-demande — request type catalog and creation

**GET** `nouvelle-demande` with `id=""` — probe params: `id=""`
(`nouvelle_demande_form.json`).

With `id=""` the endpoint returns the **catalog of request types** and
their forms — `data[]` items:

```json
{
  "id": "<type id>",
  "label": "Attestation de scolarité",
  "icon": null,
  "questions": []
}
```

`questions[]` entries:

```json
{
  "label": "Autres détails",
  "type": "textarea",
  "id": "<question id>",
  "required": false,
  "reponse": null
}
```

- Question types observed: `input`, `textarea`, `select`. A `select`
  question adds `reponses[]` options: `{ "id": 0, "label": "Aller
  seulement" }` — `id` is an int there, a string on the question itself.
- Top-level: `empty`, `empty_icon`, `empty_text`, `translation`
  {`title`, `terminer`, `slectOkText`, `slectFermerText`}.
- The **POST** (submitting a demande) response shape is UNVERIFIED — do not
  rely. Whether GET with a specific `id` returns a single pre-filled form
  is UNVERIFIED — do not rely.

## contact — school contact block

**GET** `contact` — probe params: standard envelope only (`contact.json`).

```json
{
  "data": {
    "inscription_site": "Green Wood",
    "social_text": "Suivez-nous sur :",
    "icone": "<URL .svg>",
    "logo": "<URL .png>",
    "title": "Contact",
    "text": "<texte de présentation>",
    "website": null,
    "facebook": "<URL>",
    "tel": "<tel>",
    "socials": []
  },
  "empty": false,
  "empty_icon": "<URL>",
  "empty_text": "<HTML>",
  "translation": {
    "title": "Contact", "contact": "Contactez-nous !", "tel": "Téléphone",
    "website": "Site web", "follow": "Suivez-nous sur"
  }
}
```

- `socials[]` entries: `{link, icone, color}` — `icone` is an
  Ionicons-style name (e.g. `logo-facebook`), `color` a hex string
  (e.g. `#4c6bac`). Render the buttons from those two fields.
- `website` was `null` (school without a website entry).

## ressources_v2 — interactive resources (document space)

**GET** `ressources_v2` — probe params: `search=""` (`ressources_v2.json`).

`data[]` items — only `type: "quiz"` observed for this account:

```json
{
  "id": "25058",
  "matiere_id": "<id>",
  "matiere": "Mathématiques",
  "label": "<intitulé>",
  "presentation": null,
  "type": "quiz",
  "color": "#33a6e1",
  "icon": "<URL image>"
}
```

Top-level: `empty`, `logo` (empty string), `empty_icon`, `empty_text`,
`no_play` (« Télécharger la pièce jointe »), `translation`
{`title`, `quiz`, `_quiz`}.

- The same resource `id` repeats once per `matiere_id` (one entry per
  matière) — group or dedupe client-side.
- `icon` may be hotlinked from third-party hosts — do not assume
  `media.boti.education`.
- Other `type` values (PDF, videos, …) expected but not observed for this
  account — shapes UNVERIFIED — do not rely.

## quiz — quiz list and play flow (document space)

**GET** `quiz` — probed 2026-09-19 (`quiz_list.json`, `quiz_play.json`).
**POST** `quiz` — same endpoint records a finished play; fields read from
the official bundle (chunk 1140.js, parent page `/parent/quiz`) and
**validated live 2026-09-19**: a simulated 3/5 play was accepted, scored
server-side and recorded (`quiz_post.json`, `quiz_play_apres.json`).

**List form** — GET with `start=0`, `limit=10` (the official parent
« Quizs » tab, chunk 93.js; an `filter=JSON.stringify(...)` param exists in
the bundle but its filter select is dead code there — ignore):

`data[]` items:

```json
{
  "id": "<id>",
  "matieres": [{ "id": "4", "label": "Anglais" }],
  "label": "<intitulé>",
  "presentation": null,
  "type": "quiz",
  "color": "#ff882d",
  "icon": "<URL image>",
  "images": ["<URL image>"],
  "matiere_id": 1
}
```

- Top-level: `empty`, `logo`, `empty_icon`, `empty_text`, `no_play`,
  `translation` {`title`, `quiz`, `_quiz`, `matiere_placeholder`} and
  `matieres[]` — the school's subject catalog (filter options).
- Pagination `start` / `limit`; the official app appends pages client-side.
- One row per quiz (not per matière, unlike `ressources_v2`).

**Play form** — GET with `quiz_id=<id>`:

```json
{
  "data": {
    "quiz_id": "<id>",
    "label": "<intitulé>",
    "matiere": "Mathématiques",
    "color": "#33a6e1",
    "niveau": "<niveau>",
    "image": "<URL image>",
    "questions": ["…"],
    "minutes": "05:00",
    "can_play": true,
    "can_replay": true
  },
  "empty": false,
  "no_play": "Télécharger la pièce jointe",
  "translation": { "title": "Ressources", "quiz": "Quiz", "_quiz": "Quiz",
                   "matiere_placeholder": "filtrer par matiere" }
}
```

`questions[]` items:

```json
{
  "question": "<texte>",
  "alias": "",
  "answer": { "answer": "", "correct": "<texte de la bonne réponse>",
              "answered": null },
  "image": "<URL image>",
  "temps_reponse": 60,
  "reponses": [
    { "reponse": "<texte>", "correct": false },
    { "reponse": "<texte>", "correct": true }
  ]
}
```

- `answer.correct` arrives **pre-filled with the correct answer text** —
  the play is scored client-side; the per-choice feedback flags are
  `reponses[].correct` (booleans).
- `temps_reponse` = seconds allowed per question; the countdown resets on
  every question (bundle `initTimer`). `minutes` = total, display format
  « mm:ss », display only.
- At answer time the bundle sets `answer.answer` = chosen response text and
  `answer.answered` = seconds consumed (`temps_reponse − remaining`). A
  timeout answers with no choice and the full time.
- `lastPlay` (top level, from the first play onward — « last attempt »
  card in the bundle), observed 2026-09-19 after a validated play:

  ```json
  { "id": "2230", "date": "2026-09-19 22:49:27", "percent": "60 %" }
  ```

  `percent` is the server-computed score (3 correct of 5 → « 60 % ») —
  the server scores the play itself, no client score needed.
- `can_play: false` → the official app only displays `no_play`
  (« Télécharger la pièce jointe ») with no link — a dead end; read it as
  « quiz indisponible ».

**POST** — end of a completed play (bundle 1140.js):

| Field | Value |
| --- | --- |
| `quiz_id` | `data.quiz_id` of the GET |
| `questions` | the GET's `questions[]` serialized as a JSON **string**, each `answer` updated as above — everything else left byte-for-byte |
| `eleve_id` / `user_id` / `parent_id` / `key` | session values |

Response (observed 2026-09-19): flat top-level `score` (« 3/5 »),
`time` (« 02:01 », mm:ss of the play) and `can_replay` (bool) — read flat
exactly as the bundle's `resultatScore.score` does. Quirk: the envelope's
`data` is an **array** (`[]`) in this response, not an object — parsers
must fall back to the top level (ours does). The server computes both
score and time itself; the same play shows up in the next GET's
`lastPlay` (`percent` « 60 % » for a 3/5 score). The official app ignores
POST errors (console.log only) and falls back to its client-side correct
count.

## bibliotheque — school library (document space)

**GET** `bibliotheque` — probe params: standard envelope only
(`bibliotheque.json`).

Empty for this account:

```json
{
  "empty": true,
  "empty_icon": "<URL>",
  "empty_text": "<texte>",
  "data": [],
  "0": { "label_new_ressource": "Nouvelles ressources en lignes !!" },
  "unites": [],
  "translation": { "ressources": "ressources", "no_data": { "img": "", "label": "<texte>" } }
}
```

- `data[]` and `unites[]` item shapes UNVERIFIED — do not rely.
- Quirk: a numeric-string key `"0"` carries a UI label at top level —
  ignore unknown keys instead of failing on them.

## objects — lost & found feed (NOT the document space)

**GET** `objects` — probe params: `search=""` (`objects.json`).

Despite the generic name, this endpoint is the **objets perdus** feed
(112 items observed) — confirmed by the app menu returned by `acces_check`
(label « Objets perdus », path `/parent/objets-perdus`). It has nothing to
do with the document space (`ressources_v2` / `ressource_details` /
`bibliotheque`).

Top-level keys: `all_objects`, `types`, `empty`, `empty_icon`, `empty_text`,
`translation`.

`all_objects[]` items:

```json
{
  "isForMe": false,
  "id": "<id>",
  "description": "<description de l'objet>",
  "found": "18 Sep 2026",
  "personFound": "<initiale du trouveur>",
  "image": "<signed media.boti.education URL>",
  "eleve": null,
  "eleve_name": null
}
```

- `found` is the discovery date, display format `DD Mon YYYY` (verified on
  all 112 items).
- `eleve` / `eleve_name` were null on 96/112 items and populated on the
  rest — nullable; present when the object is tied to an élève.
- `isForMe` was false on every observed item; the « Ça m'appartient »
  action (button text from `translation.button_text`) presumably flips it
  server-side — UNVERIFIED — do not rely.
- `image` always a signed media URL in the probe.

`types[]` — categories, each partitioning the feed:

```json
{ "id": "<id>", "label": "<catégorie>", "alias": "<alias>", "items": [] }
```

- `items[]` uses the exact same item shape as `all_objects`; their total
  length matched `all_objects` exactly in the probe.
- Top-level `translation`: `title` « Objets Perdus », `categ_All`,
  `aucun_obj`, `par`, `button_text` « Ça m'appartient », `etat_obj`
  « En cours ».

## cours_v2 — timetable (out of v1 scope, shape kept for later)

**GET** `cours_v2` — probe params: standard envelope only
(`cours_v2.json`).

```json
{
  "translation": {
    "title": "Emploi du temps",
    "aucun_cours": "<texte>",
    "cahie_de_texte": "Cahier texte"
  },
  "next_week": "2026-09-21",
  "last_week": "2026-09-07",
  "selected_day": 5,
  "label": "Du  2026/09/14 Au  2026/09/20",
  "label_du": "Du  14 Sep 2026",
  "label_au": "Au  20 Sep 2026",
  "seances": [],
  "show_cahier_text": true,
  "restricted": { "restricted": false, "label": "<HTML>", "contact": "<HTML>" }
}
```

- `seances[]` = one entry per day (Monday–Saturday):
  `{ "label": "L", "day": 1, "date": "Le 14 Sep 2026", "seances": [] }`.
  The inner `seances[]` (actual slots) was empty in the probe — inner slot
  shape UNVERIFIED — do not rely.
- `selected_day` is a 1-based day index; `next_week` / `last_week` are ISO
  Mondays for week navigation.
- `translation` key `cahie_de_texte` (sic).

---

## Not probed (v1-adjacent — shapes UNVERIFIED, do not rely)

| endpoint | method(s) | note |
|---|---|---|
| `admin_nouveautes` | GET | announcement bodies (`description` HTML); ~19 MB — cache locally, never poll |
| `post_view` | GET | per-post detail / mark-as-read |
| `ressource_details` | GET | resource detail (quiz content, …) |
| `cartable_numeriques`, `cartable_split` | GET | digital cartable |
| `nouveau-message` | POST | send a message — fields bundle-verified (see section below); live text send validated 2026-09-19, attachment/voice parts still unproven |
| `absences-justification` | POST | justify an absence — field names unverified |
| `pick_enfants` | GET/POST | child switcher — POST fields unverified |
| `device_token` | POST | FCM registration — fields per `BOTI-API.md`, server acceptance unverified |
| `logout` | POST | kills the session server-side |
| `quiz` | GET/POST | list + play flow — GET and POST both verified live 2026-09-19 (see section) |

## Summary of unverified items in this map

- `absences`: list items of `data.justifiees[]` / `data.non_justifiees[]`
- `pinned_posts`: non-empty item shape (probe returned `data: []`)
- `bibliotheque`: `data[]` / `unites[]` item shapes
- `nouvelle-demande`: POST submission response; GET with a specific `id`
- `devoirs_date_v2`: real submission multipart fields
- `cours_v2`: inner `seances[]` slots
- `acces_check`: behaviour on an invalid/expired session
- `nouveau-message`: live text send validated 2026-09-19 — non-empty `files[]`/`audio` parts and the new-thread response remain unproven
- `pick_enfants`, `device_token`: POST fields
- non-empty `files[]` shapes (nouveautes, messages, devoirs `devoir_fait`)
