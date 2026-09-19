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
