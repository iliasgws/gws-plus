# Experimental WordCraft viewer (issue #157)

The default-off Settings switch **Lecteur WordCraft — bêta** routes supported
downloaded attachments through a private `WordcraftActivity`. The shared
`Fichiers.intentionOuvrir` and `intentionOuvrirUri` entry points cover attachment
opening across Documents, homework, news and conversations. Downloads/caches
and server protocol are unchanged. Unsupported files and the disabled switch
retain the existing external Intent. A visible **Ouvrir avec…** action bypasses
the beta unconditionally.

## Scope

- Word `.docx`/`.doc`, ODT, RTF, TXT, Markdown, HTML and TeX import.
- No PDF, spreadsheet or presentation rendering is claimed here.
- 10 MiB maximum, with a 50 MiB expanded ZIP limit and at most 2048 entries
  for DOCX/ODT. Invalid/empty/oversized files produce an explicit error.
- This embeds the upstream editor as an experimental preview. In-memory edits
  are possible, but save/export/print/file selection are not exposed. The UI
  explicitly says modifications are not saved. Do not call it a read-only engine.
- Desktop-oriented UI, font substitution, fidelity and mobile GPU support need
  real-handset testing. It is not exact Microsoft Word parity.

## Privacy boundary

Pinned bundled WASM/JS is served through an exact request allowlist under
`https://wordcraft.gws.invalid`; this is not a real server. Only four known
assets and one bounded temporary document are served. Every other request,
method, host or scheme is rejected. WebView network loading, mixed content,
filesystem/content-provider access, browser storage, downloads, permissions,
navigation and native JavaScript bridges are disabled. A restrictive CSP blocks
frames, remote connections, workers and objects. The source HTML is imported
as document data, never navigated to as executable page content.

Original URI/name/token never enter JavaScript. The engine receives a neutral
`document.<extension>` via a synthetic drop. Console output is suppressed. The
bounded staging copy lives in app cache and is deleted on activity destruction;
Android may retain a cache file after process death until cache cleanup. Nothing
is uploaded. Renderer termination is handled with an external-viewer fallback.

## Reproducibility and notices

Upstream version, original distribution checksum, licenses, attribution and the
Android texture-limit adjustment are in `third_party/wordcraft/`. The runtime
assets are under `app/src/main/assets/wordcraft/`. The upstream module is large;
expect APK size growth. No download or runtime CDN is used.

Unit tests cover supported/unsupported extensions, MIME fallback and the strict
request allowlist. `WordcraftViewerTest` constructs a synthetic DOCX, verifies
opt-out/opt-in routing, starts the local engine and captures the result without
school data. This test's renderer-ready signal alone is not evidence of document
fidelity; inspect the captured document too. No school-account files are used.
