# WordCraft experimental viewer

Upstream: https://github.com/storytold/wordcraft, tag `v0.8.0`, commit
`f2e15cf8f899a79899a2d9595ca4ce7df1e5ea22`.

Original web release: `wordcraft-web-0.8.0.zip`, SHA-256
`37a5369cd66606e0c4e019e0f273f905a9b2e9101f68a191a6f40b8724225ecd`.
The checksum matches the release's `SHA256SUMS.txt`.

MIT OR Apache-2.0, copyright ArtCraft Team and contributors. Full licenses,
upstream NOTICE and asset attribution are retained here and shipped with the
viewer assets. Upstream branding is not a GWS+ brand; no upstream logo is used
for app navigation or marketing.

GWS+ wrapper: `app/src/main/assets/wordcraft/{index.html,preview.js}`. It serves
the engine from a fake HTTPS origin intercepted entirely by Android, exposes
only one bounded document, and supplies it as a synthetic file drop. No network,
JavaScript/native bridge, file picker, persistent browser storage, save/download
or print functionality is exposed. Changes in the editor are memory-only and
discarded when closed. This is not a general Office or PDF viewer.

The original release requires an 8192-pixel GPU texture limit. Testing on API 36
with SwiftShader/ANGLE exposes only 4096 and rejects initialization. Compatibility
work is tracked in issue #157; do not claim this passes on all Android devices.
