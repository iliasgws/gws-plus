# Greenwood School + hosted MCP service

An initial hosted Streamable HTTP MCP service exposing read-only homework and
library data from the Boti API. It implements OAuth authorization code with
PKCE (S256), a browser login and explicit consent screen, dynamic public-client
registration, short-lived access tokens, rotating refresh tokens, and token
revocation. The MCP tools are `get_homework`, `get_document_units`,
`list_documents`, and `get_document_details`.

The server follows the official MCP TypeScript SDK's current `McpServer`,
`createMcpHandler`, and Node adapter APIs. OAuth authorization-server endpoints
are implemented in this service because the SDK's current server package
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
only to this service. Do not expose the SQLite file or encryption key.

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

The browser session has a `/account` page. It lists active MCP clients for
that Boti login; the user can revoke all grants for the login and erase its
encrypted Boti key. This is currently a browser page on the service, not an
Android in-app screen.

The Boti password is used only in the login request and is not written to the
database. The Boti `keyToken` is encrypted with AES-256-GCM using
`DATA_ENCRYPTION_KEY`; its nonce and authentication tag are stored with the
ciphertext. OAuth bearer tokens never contain the Boti token. The database
also contains account identifiers required to make requests to the parent’s
school account, so it must be protected as personal data. Do not enable HTTP
request-body logging at the reverse proxy or application host.

The login page sends credentials directly to Boti's documented `POST login`
endpoint over HTTPS, with `remembreMe=false`. Read tools use authenticated
`GET devoirs`, `GET bibliotheque`, and `GET ressource_details` requests. No
write endpoints are exposed. Signed file URLs returned by Boti expire and are
passed through as data; the service does not download or persist files.

## Production setup still required

This is an implementation starter, not a production identity deployment.
Before exposing it to families, an operator must provision a stable HTTPS
origin, durable encrypted backups, secret rotation/recovery, monitoring and
login abuse controls, retention/deletion policy, and privacy/legal review for
handling school-linked personal data. The small first-party login form cannot
complete school-side identity assurance, parent/student authorization checks,
or account recovery beyond Boti's own login response. It links the student ID
returned as `eleveId` (or the first entry in `eleves`) and currently has no
student picker. Verify that behavior and the vendor's terms with the school
before production use. If Boti changes its undocumented API or invalidates
sessions, users will need to sign in again.

The initial service has no administrator dashboard, Android in-app access
management, rate limiting, consent history UI, or automated cleanup job for
expired rows. Reviewed privacy disclosures and terms of service must be
prepared before broad production use. OAuth revocation is available at
`/revoke` and `/account`.
