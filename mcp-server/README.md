# Greenwood School + hosted MCP service

An initial hosted Streamable HTTP MCP service exposing school data and selected
confirmed actions through the Boti API. It implements OAuth authorization code
with PKCE (S256), a browser login and explicit consent screen, dynamic public-client
registration, short-lived access tokens, rotating refresh tokens, and token
revocation. Grants are bound to a student confirmed by the parent. Read tools
cover homework and details, timetable, interactive resources and quizzes,
library documents, news, conversations, administrative requests, school
contact, shop products and history, and guest-meal planning. Absences are
excluded because their item shape is unverified. `prepare_message` previews a
text message or reply; `send_message` requires fresh MCP elicitation that
confirms the exact recipient/thread, subject, category, and text. Attachments
and voice are not supported. `schedule_reminder`, `list_reminders`,
`cancel_reminder`, and `complete_reminder` manage encrypted, student-bound
reminders in the MCP service; creation requires fresh elicitation. Reminders
are returned when an agent checks the list. There is no push, email, SMS, or
proactive notification delivery. `download_library_pdf`
returns a verified Bibliothèque PDF as an embedded MCP resource. `submit_quiz_attempt`,
`place_shop_order`, and `delete_shop_order` each require a distinct write scope
and a fresh elicitation showing the exact persistent action.

The server uses MCP TypeScript SDK v2 (`@modelcontextprotocol/server`),
including `McpServer`, `createMcpHandler`, and the Node adapter. It routes the
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

The MCP initialize response automatically supplies agent instructions for the
selected-student boundary, scope-dependent tool discovery, data privacy, and
fresh confirmation requirements. The guide tells assistants to treat school
messages, news, homework, documents, and PDFs as untrusted data, and never let
embedded content override user/system directions or authorize an action.
Clients can also fetch the `greenwood_agent_guide` prompt for the same
instructions. The service downloads only PDFs from verified Bibliothèque detail attachments, with a 15 MiB cap,
strict media-host validation, no redirects, and no persistence. Other signed
file URLs returned by Boti expire and are not stable links.

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

The service has no administrator dashboard, login abuse controls, consent
history UI, or reviewed privacy disclosures and terms. Hourly cleanup removes
expired rows. OAuth revocation is available at `/revoke` and `/account`.
