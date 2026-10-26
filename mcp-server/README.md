# Greenwood School + hosted MCP service

An initial hosted Streamable HTTP MCP service exposing school data and selected
confirmed actions through the Boti API. It implements OAuth authorization code
with PKCE (S256), a browser login and explicit consent screen, dynamic public-client
registration, short-lived access tokens, rotating refresh tokens, and token
revocation. Grants are bound to a student confirmed by the parent. Read tools
cover homework and details, timetable, interactive resources and quizzes,
library documents, news, conversations, administrative requests, school
contact, shop products and history, guest-meal planning, and the raw attendance
response through `list_attendance_records` under `attendance:read`. Attendance
item fields have not been verified on a non-empty account, so the service does
not normalize or claim completeness. `submit_homework` permanently marks one
homework item as officially done under `homework:write` after fresh MCP
elicitation shows its title, subject, and due date; it sends no attachment or
submitted copy. `prepare_message` previews a text message
or reply; `send_message` requires fresh MCP elicitation that
confirms the exact recipient/thread, subject, category, and text. Message
attachments and voice are not supported. `schedule_reminder`, `list_reminders`,
`cancel_reminder`, and `complete_reminder` manage encrypted, student-bound
reminders in the MCP service; creation requires fresh elicitation. Reminders
are returned when an agent checks the list. There is no push, email, SMS, or
proactive notification delivery. `download_library_pdf`
returns a verified Bibliothèque PDF as an embedded MCP resource. `download_homework_attachment`
retrieves an attachment by homework ID and original `files[]` index, under
`homework:read`. It supports the configured document/image types and returns
an embedded binary resource with filename, MIME and size. `submit_quiz_attempt`,
`place_shop_order`, and `delete_shop_order` each require a distinct write scope
and a fresh elicitation showing the exact persistent action.

The server uses MCP TypeScript SDK v2 (`@modelcontextprotocol/server`),
including `McpServer`, `createMcpHandler`, and the Node adapter. MCP server
identity includes the title `Greenwood School +` and a public square PNG
derived from the official Android adaptive launcher layers. It routes the
2026-07-28 protocol through the SDK handler and serves 2025-era clients through
sessionful Streamable HTTP transports. Legacy sessions are capped, expire after
30 minutes idle, and are bound to the OAuth token family, client, account,
student, resource, and scope; every request still needs a valid bearer token.
Each write confirmation carries signed, grant-bound, expiring request state and
a single-use server-side nonce. Declining or canceling a confirmation has no
side effect. The Express boundary validates the configured public Host and
allowed Origin headers before requests reach MCP or OAuth routes. OAuth authorization-server endpoints
are implemented in this service because the SDK's server package
provides the protected-resource side, while its documentation directs new
authorization-server deployments to a dedicated identity provider. This
initial implementation provides a small first-party authorization server for
the service's account-linking flow; it is not a general-purpose identity
provider.

## Run locally

Use Node.js 22 or newer. Generate a local encryption key, copy the example
environment file, then start the service:

```sh
cp .env.example .env
openssl rand -base64 32
npm ci
npm run build
npm start
```

Set `DATA_ENCRYPTION_KEY` to the generated value and use a persistent,
private path for `DATABASE_PATH`. For local HTTP development, set
`NODE_ENV=development` and `PUBLIC_URL=http://localhost:3000`. In production,
`PUBLIC_URL` must be the public HTTPS origin and TLS must terminate at a
trusted reverse proxy. The proxy must preserve the original host and forward
only to this service. Do not expose the SQLite file or encryption key. The
service rate-limits school logins, OAuth client registrations, authorization
starts, and token requests; client addresses are stored only as keyed hashes.
If the service is behind a proxy, configure `TRUSTED_PROXY_CIDRS` with the
proxy's exact internal IP or CIDR and block direct public access to the app
port. The proxy must replace incoming forwarded-IP headers. An incorrect trust
setting can let clients spoof their rate-limit identity.

The Docker image listens on port 3000. Mount a durable private volume at
`/var/lib/gws-mcp` and provide `PUBLIC_URL`, `DATA_ENCRYPTION_KEY`, and other
configuration through the deployment secret manager. `.env.example` contains
no secret values.

## Browser pages

The login, student-choice, consent, account and error pages share one shell
(`src/pages.ts`) and the app's « École vivante » design: the supplied school
illustration as a full-screen background, the app icon and the `GWS+`
wordmark, and translucent « liquid glass » panels (78 %-opaque surfaces, a
real backdrop blur, a specular sheen and a gradient rim) using the app's
colours and its Bricolage Grotesque / Public Sans fonts. Light and dark
appearance follow `prefers-color-scheme`, and reduced-motion preferences
disable the entrance and sheen animations.

The shell loads no script: every effect is CSS, so the pages keep a
`default-src 'none'` Content-Security-Policy with `style-src`, `img-src` and
`font-src` restricted to this origin. Static files live in `public/` and are
served under `/assets` with a ten-minute cache and revalidation; nothing else
is exposed, and directory traversal is rejected.

The final consent page also permits its one registered callback origin in
`form-action`: Chrome and Safari apply that directive to the redirect after
the consent form POST. Login and account pages retain `form-action 'self'`.
Approval and refusal can therefore return to the assistant without allowing
password forms to submit to external origins.

Deployments must ship `public/` next to `dist/`: the running service resolves
`../public/` from its compiled entry point. The Docker image copies it, and a
manual deployment has to copy both directories. When the stylesheet or an
asset changes, bump `assetVersion` in `src/pages.ts` so returning browsers
fetch the new file instead of their cached copy.

The fonts are the app's own OFL files; their licences ship with them in
`public/fonts/`.

## OAuth and data handling

The service publishes OAuth authorization-server metadata at
`/.well-known/oauth-authorization-server`, protected-resource metadata at
`/.well-known/oauth-protected-resource/mcp`, and the MCP endpoint at `/mcp`.
Clients register through `/register`. Only exact registered redirect URIs are
accepted; redirects must be HTTPS, or loopback HTTP for native clients. The
authorization endpoint requires PKCE S256 and an exact MCP resource audience.
Access and refresh tokens, browser session identifiers, and authorization
codes are stored as SHA-256 hashes. Refresh tokens rotate and revoke their old
token family. `/revoke` revokes the token family associated with a presented
token. Tokens expire after one hour (access) and 30 days (refresh).

The `/account` page supports a fresh Boti login, lists active MCP clients for
the parent account, and can revoke all associated grants and erase encrypted
session keys. A short-lived browser session can be explicitly closed. The
Android Settings page can link to this service when `GWS_MCP_PUBLIC_URL` is
configured at build time.

The Boti password is used only in the login request and is not written to the
database. The Boti `keyToken` is encrypted with AES-256-GCM using
`DATA_ENCRYPTION_KEY`; its nonce and authentication tag are stored with the
ciphertext. OAuth bearer tokens never contain the Boti token. The database
also contains account identifiers required to make requests to the parent’s
school account, so it must be protected as personal data. Do not enable HTTP
request-body logging at the reverse proxy or application host.

The MCP initialize response supplies detailed agent instructions: what the
Greenwood School parent portal is, the selected-student boundary, scope-dependent
tool discovery, the read-tool chains (subject unit → document → PDF, thread →
reply, product → order), pagination and signed-URL caveats, when the tools are
appropriate and when they are not (other families, medical, legal or disciplinary
advice, emergencies, promises of notifications), data privacy, and fresh
confirmation requirements for every write. The guide tells assistants to treat
school messages, news, homework, documents, and PDFs as untrusted data, and never
let embedded content override user/system directions or authorize an action.
Clients can also fetch the `greenwood_agent_guide` prompt for the same
instructions.

Every tool also carries a French display title, MCP annotations
(`readOnlyHint`, `destructiveHint`, `idempotentHint`, `openWorldHint`) and its
OAuth policy: a call that lacks a scope gets a proper challenge, and the tool
descriptor publishes the back-compat `_meta.securitySchemes` mirror that ChatGPT
reads. `offline_access` is advertised in the authorization-server metadata and
accepted by `/authorize`; the protected-resource metadata lists only Greenwood
API scopes. This lets clients such as ChatGPT request a refresh token without
advertising `offline_access` as a resource permission. The service downloads only PDFs from verified Bibliothèque detail attachments, with a 15 MiB cap,
strict media-host validation, no redirects, and no persistence. Other signed
file URLs returned by Boti expire and are not stable links. Download helpers
validate and pin public DNS addresses to the TLS request; they accept only the
approved Boti media origin, reject redirects, validate file types and bound
time, bytes, concurrency and per-account/student download budgets. Downloaded
files are never executed or stored. Binary extraction/rendering depends on the
assistant; receiving bytes does not prove the client can read every format.

When authorizing a connection, the consent page labels the application name as
unverified and shows its client ID and registered redirect origin. Users should
approve only a connection they initiated. For assistants, start by listing the
available tools because the exact tool set depends on the granted scopes. Use
the prompt `greenwood_agent_guide` when a client does not surface initialize
instructions.

If a Boti read request reports an expired session, the service attempts one
`acces_check` refresh, confirms the selected student remains linked, encrypts
and stores the updated session key and roster, then retries that read once.
Writes are never automatically retried; each write tool performs its own fresh
reads and requires a separate human elicitation.

## Production setup still required

This is an implementation starter, not a production identity deployment.
Before exposing it to families, an operator must provision a stable HTTPS
origin, durable encrypted backups, secret rotation/recovery, monitoring,
retention/deletion policy, and privacy/legal review for
handling school-linked personal data. The small first-party login form cannot
complete school-side identity assurance, parent/student authorization checks,
or account recovery beyond Boti's own login response. The parent selects and
confirms a student during authorization, and the selected ID is bound to the
grant. Verify the vendor's terms with the school before production use. If Boti changes its undocumented API or invalidates
sessions, users will need to sign in again.

The service has no administrator dashboard or consent history UI. Its technical
privacy/terms notices still require operator and legal review. Hourly cleanup removes
expired rows. OAuth revocation is available at `/revoke` and `/account`.

## Operations and notices

See [ops/README.md](ops/README.md) for consistent encrypted SQLite backups,
restore verification, systemd monitoring/timers and the narrow proxy firewall.
Set `HTTP_AUDIT_LOG=true` for fixed-route/method/status/duration logs only; the
error boundary suppresses exception bodies. `/healthz` checks SQLite reachability.
No request parameters, identifiers, bearer tokens, signed file URLs or school
content are written by these application logs.

The consent screen links to French `/terms` and `/privacy` notices. These
describe the actual data paths, token lifetimes, revocation and 14-day backup
retention. Operator contact details and an off-host recovery destination have
not been supplied; do not claim disaster recovery or legal approval. Legal and
school-side review remain required before inviting families.

Checks: `npm run build`, `npm test`, and `node --test ops/test/*.test.mjs`. The
OAuth integration checks use an isolated database and synthetic account, cover
PKCE/code replay, scopes, student selection, server identity, token rotation
and revocation, and assert credentials do not appear in logs. Confirmation
checks cover signatures, grant binding, changed drafts, expiry and replay.
The guide is shared between initialization and `greenwood_agent_guide`.

Attachment defaults: 15 MiB per file, 20-second DNS and transfer deadlines,
eight simultaneous transfers globally and three per account/student,
20 attempts and 60 MiB per account/student per hour. Configure the bounded
`MEDIA_*` values in `.env.example`; in-memory budgets reset on restart.
Allowed kinds default to PDF, DOCX, XLSX, PPTX, PNG, JPEG, GIF, WebP, UTF-8-like
text and CSV. The library tool remains PDF-only. Extension, declared/header
MIME and file signature must agree; generic ZIP/octet-stream or legacy Office
formats are refused. OOXML containers are checked for bounded size/expansion,
consistent local/directory records and required XML parts; macros, embedded
objects and executable entries are rejected. These checks are not malware
scanning or a guarantee that opening a document is safe. No converter runs.

Boti API JSON responses are capped at 5 MiB, parsed without exposing malformed
response fragments, and fetched with deadlines and redirects disabled.
