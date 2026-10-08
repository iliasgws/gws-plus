# Security notes from the official app analysis

Findings from static analysis of the publicly distributed
`com.botieducation.greenwood` 2.4.14 APK. Original write-up:
<https://gitea.oimcloud.myaddr.tools/Omarchy-Big-PC/greenwood-school-re>
(`docs/FINDINGS.md`). We track them here for two reasons: to know how the
vendor backend behaves around us, and to make sure gws-plus does better.

| # | Severity | Finding |
|---|---|---|
| F1 | HIGH | Hardcoded demo account shipped in the production build |
| F2 | MEDIUM | Plaintext password transmitted as a form field |
| F3 | LOW | Verbose production logging of params/headers/tokens |
| F4 | INFO | No certificate pinning, no obfuscation |
| F5 | INFO | Broad but plausible permissions |

## F1 — Hardcoded credentials in the shipped production build [HIGH]

The login page component (`assets/public/1028.js`) contains a leftover
dev/biometric-reconnect account compiled into the production bundle:

```
reconnectUser = {id:12745, phone:"0649014703", password:"0649014703"}
```

It is present in every public copy of the APK. Anyone with the app has a
phone number + password pair for the vendor backend. Whether the account is
still active server-side is unknown — that is the vendor's to fix, and
testing it against the live server would cross into unauthorized access.

**Lessons for gws-plus:** never ship credentials or "reconnect" demo
accounts in the bundle; nothing secret belongs in client code.

## F2 — Plaintext credential transmission [MEDIUM]

`POST /login` sends the password as a plaintext form field. TLS protects the
transport, but the credential sits in plaintext in any intermediary that
logs form data. Same pattern in forgot-password / change-password. No
client-side hashing.

**Lessons for gws-plus:** we must use the same protocol to talk to the same
backend, but we can (a) never persist the password beyond what login
requires, (b) prefer the platform keystore/keychain for any stored secret,
(c) not add our own half-baked client-side hashing that would break the
protocol.

## F3 — Verbose production logging [LOW]

The official ApiService logs request payloads, headers and the access token
to `console.log` in the release build (visible via `adb logcat` and remote
WebView debugging if enabled).

**Lessons for gws-plus:** strip logs of tokens and personal data in release
builds; never log the session key.

## F4 — No certificate pinning, no obfuscation [INFO]

No pinning configuration, default network security config, fully readable JS
bundle — the whole protocol was mapped in about an hour.

**Lessons for gws-plus:** security through obscurity doesn't exist here; the
backend must be treated as hostile to unknown clients (rate limits, error
handling), and our own client must fail gracefully when the vendor changes
or blocks responses.

## F5 — Broad but plausible permissions [INFO]

CAMERA, fine+coarse LOCATION, microphone, external storage, FCM. Location is
for bus tracking; camera for photo upload. No SMS/contacts/accessibility
abuse observed.

**Lessons for gws-plus:** request only what a feature actually uses, and
document the use case next to the permission.

## Responsible disclosure context

- The vendor is Boti Education (boti.education); the school (Green Wood
  School) can escalate. A ready-to-send French disclosure email exists in the
  source repo (`french` branch, `docs/DISCLOSURE.md`).
- gws-plus should not test F1 against the live server beyond its existence in
  the bundle — that would cross into unauthorized access.

## GWS+ note — update checker (issue #46, added 2026-09-22)

The update checker talks to one third-party host beyond the school API:
`api.github.com` (public repo `iliasgws/gws-plus`), unauthenticated GET,
throttled to once per 12 h. No token, no personal data is sent — only the
device's IP, like any HTTP client. APK downloads come from the release's
direct `browser_download_url`. `REQUEST_INSTALL_PACKAGES` is declared for
that single flow; `POST_NOTIFICATIONS` is runtime-requested from Paramètres
only. Everything else in the permission list is still intentionally absent.

## GWS+ note — release signing policy (issue #136, added 2026-10-08)

Every APK ever distributed — every GitHub Release — is signed with the same
debug-keystore certificate (SHA-256 starts `775498f7…`, recorded in
`AGENTS.md`). `app/build.gradle.kts` deliberately points
`release.signingConfig` at the debug signing config: Android only replaces
an installed app when the new APK carries the same certificate, so swapping
keys would force every existing user to uninstall first and lose local
state. **No key change is made or planned until a continuity/migration
plan exists** (issue #136); the "replace the debug key" recommendation is
acknowledged and consciously deferred for this reason.

Before any future migration, all of the following must be true:

- The fingerprint of every shipped artifact is verified (`apksigner
  verify --print-certs`) and continuity with the historical certificate is
  recorded.
- A migration release mechanism exists that does not rely on in-place
  upgrade across certificates (Android cannot install a differently-signed
  update over an existing app): e.g. a final legacy-signed version whose
  updater side-loads the first new-certificate build with user consent.
- The new private key lives in hardware-backed or offline storage; neither
  the key nor its passwords are ever committed. Release signing stays a
  dedicated identity, separate from debug builds, once migrated.

The current debug key's exposure is not treated as an authorization
boundary: updates are trusted via the GitHub Releases source, and the
certificate only establishes install-chain continuity.
