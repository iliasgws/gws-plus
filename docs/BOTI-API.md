# Boti Education API — Green Wood School variant

> Internal reference for **gws-plus**, distilled from the public static
> reverse-engineering of the official app
> (`com.botieducation.greenwood` 2.4.14, Capacitor/Ionic/Angular).
>
> Source: <https://gitea.oimcloud.myaddr.tools/Omarchy-Big-PC/greenwood-school-re>
> (branch `french` for the announcement-body finding).
>
> **Status of the API:** undocumented vendor API. It can change or start
> rejecting unofficial clients at any time. Keep every call behind our own
> client layer — never scatter raw URLs through feature code.

## Base URL

```
https://boti.education/p/greenwood/botiapi/
```

Pattern per school: `https://boti.education/p/<school-slug>/botiapi/` — all
Boti Education schools share one backend. Media/files live on a separate
host: `media.boti.education` (signed, expiring URLs — token appended to path).

## Authentication

### Login

```
POST {base}/login            Content-Type: multipart/form-data
```

| field | value |
|---|---|
| `login` | phone number, e.g. `06xxxxxxxx` |
| `password` | plaintext (vendor-side choice, not ours) |
| `privacy` | `"true"` / `"false"` |
| `remembreMe` | `"true"` / `"false"` |

Response (HTTP **200** even on errors, `application/json`):

```json
{
  "status": 200, "msg": "Bienvenue !", "error": false,
  "keyToken": "<40 hex chars>",
  "userId": "…", "parentId": "…", "eleveId": "…",
  "_acces": "parent", "role": "parent",
  "eleves": [ { "id": "…", "nomcomplet": "…", "niveau": "…", "img": "…", "parent_nom": "…", "parent_image": "…" } ],
  "parent": { "id": "…", "nomcomplet": "…", "tel": "…", "email": "…", "sexe": "…", "image": "…" }
}
```

Errors come back as `{"error": true, "msg": "…"}` with HTTP 200 — always
branch on `error`, never on the status code.

### Using the session

Every subsequent call carries the session key:

- **GET:** query params + `key=<keyToken>&user_id=<userId>`
- **POST:** multipart/form-data fields + `key=<keyToken>`

Optional JWT path exists (`Authorization` / `X-refresh-token` headers) but is
empty for parent accounts — ignore it.

### Server-side session control (must be handled globally)

- Any response containing `"disconnect": true` → session was killed; navigate
  to login. Implement once, in the API client.
- `"redirect": "<path>"` → the app should navigate there.
- `"showSLides": "<key>"` → onboarding/news modal key.

## Request envelope

GET params observed in the official app (subset applies per endpoint):
`user_id, key, acces, versionCode, versionNumber, paltform, eleveId, lang,
date, startDate, endDate, classe, id, search, page`.

POST FormData conventions:

- flat fields: `key=value`
- nested object → `key[subkey]=value`
- array → `key[0]=…, key[1]=…`
- `File`/`Blob` → appended raw under the field name
- `key=<keyToken>` is auto-appended by the API service

### Quirks that will bite you

- **`paltform`** — the typo is in the official app; the server expects it.
  Do **not** "fix" it.
- Endpoint names are the complete server route — raw Pascal/snake identifiers
  appended to the base URL. No `/api` prefix, no `.php`.
- The official build logs request params, headers and tokens to
  `console.log` in production. We should do the opposite (see
  `SECURITY-NOTES.md`).

## Push notifications

```
POST {base}/device_token     fields: token=<fcm token>, user_id, key, uid=<device uuid>
```

## Media / files

- Images and documents are fetched from `media.boti.education` with a signed,
  expiring path (base64url token + timestamp appended to the path).
- **Links are double-encoded:** decode exactly **once** (e.g. `parse_qs`
  semantics); keep `%2B`, `%2F`, `%3D` literal.
- URLs expire after roughly **15–20 minutes** — download in the same run that
  resolves the URL, or re-resolve before retrying.
- PDFs are rendered client-side (pdf.js in the official app).

## Endpoint cheat-sheet for gws-plus features

Full 100-endpoint inventory lives in [`endpoints.md`](endpoints.md). The
subset this project needs:

| gws-plus feature | endpoints |
|---|---|
| Cahier de liaison / devoirs | `devoirs` (GET/POST), `devoirs_date_v2` (POST), `cours_v2` (GET) |
| Actualités de l'école | `nouveautes` (GET/POST), `pinned_posts`, `post_view` — **bodies only via `admin_nouveautes`**, see below |
| Espace documents | `ressources_v2` (GET/POST), `ressource_details`, `bibliotheque`, `cartable_numeriques`, `cartable_split` |
| Demandes administratives | `demandes` (GET), `nouvelle-demande` (GET/POST) |
| Contact administration | `messages` (GET), `nouveau-message` (POST) |
| Session / compte | `login` (POST), `logout` (POST), `acces_check` (GET), `compte` (GET/POST) |

### `nouveautes` trap: announcement bodies live in `admin_nouveautes`

The parent-facing `nouveautes` list returns only
`id, title, categorie, date, intro, files, image` — the `intro` is usually
empty or just a "Vu le …" badge. The real bodies (`description`, HTML) are
only returned by:

```
GET {base}/admin_nouveautes      params: user_id, parent_id, eleve_id, key
```

- Despite the name it answers fine to a standard parent account.
- **~19 MB of JSON** for Greenwood — cache the response locally and never
  poll it in a loop.
- Recent posts sometimes appear there **before** they show up in the parent
  `nouveautes` list.
- Many announcements have no PDF at all; the content is only in `description`
  (HTML with emojis, `<p style="text-align: justify">`).

## Usage & attribution

- Spec produced by static analysis of the publicly distributed APK plus **one**
  live login call by the original author; no other requests were made against
  the school server.
- gws-plus, as an unofficial client, should stay on the parent's own account
  and keep credentials out of the repository (local config file, `chmod 600`).
- Credit the source repo when this doc informs public-facing work.
