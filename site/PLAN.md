# PLAN — Documentation / project website for gws-plus

> Living plan for the website work. Everything website-related lives in
> this `site/` folder: the plan, the Astro project, the Impeccable
> artifacts (surface brief, direction contract, review screenshots) and
> the generated icon assets. Repository docs (`docs/*.md`) stay where
> they are — Astro reads them in place.

## Decisions (confirmed with the user)

| Decision | Answer |
| --- | --- |
| Purpose | Docs / project website — Impeccable mode **Read** |
| Location | `site/` at the repository root, feature branch `feat/site-documentation` forked from `main` |
| Stack | **Astro** — markdown-native (feeds the 13 `docs/*.md` files directly), static output, zero client JS by default, full design control (a themed generator would fight the design process) |
| Custom icon | **Chrome only** (header logo + favicon set), never a hero element |
| Language | Site UI chrome in French with correct accents (repo language rule); source docs keep their existing language |
| Design world | Inherit the app's « Le registre » identity (`docs/product/DESIGN.md`) — the app's DESIGN.md is not replaced |
| Ship rules | Feature branch + PR; no merge/publish without explicit user confirmation (AGENTS.md) |

## Icon assets (derived from existing repo assets, no new art invented)

- **Header logo:** composite `app/src/main/res/mipmap-xxxhdpi/ic_launcher_background.png`
  + `ic_launcher_foreground.png` (432×432) → `site/public/icon.png`.
- **Favicon set:** convert the monochrome monogram
  `app/src/main/res/drawable/ic_launcher_monochrome.xml` (ink page
  `#1F3D2B`, red pen dot `#B3382A`, light lines `#E9EFE7`) to SVG favicon,
  plus `favicon.ico` / `apple-touch-icon.png` from the composited color
  mark.
- The « arbre et + » mark feeds the direction contract's OWN-WORLD block:
  the site derives its identity from the app's icon rather than
  introducing a second mark.

## Phase 1 — Skill setup & product truth (Impeccable `init`)

1. Run `impeccable context` once from the skill base directory, cwd =
   repository root; follow its directives (resolved `PRODUCT.md` /
   `DESIGN.md` paths, image-generation availability).
2. `init` interview — only material gaps (≤ 3 questions): who reads this
   site (parents? developers? agents?), what success looks like, whether
   to inherit « Le registre ».
3. Write `PRODUCT.md` at the path the skill resolves (schema comment
   included, platform `web`, stack `Astro`). If image generation exists,
   ask the one-time **comp-first vs code-first** question →
   `.impeccable/config.json`.

## Phase 2 — Direction (Impeccable `new-work`, mode Read)

4. Read `reference/new-work.md` and `reference/craft-floor.md` before any
   UI edit.
5. Run `impeccable concept-seed --scope surface --mode read` → present
   the three structure cards; the user locks one.
6. Record the six-block **Direction contract** (THESIS / OWN-WORLD /
   STORY / FIRST VIEWPORT / FORM / FINISH) in the surface brief via
   `impeccable surface-brief write`.

## Phase 3 — Build (in `site/`, on `feat/site-documentation`)

7. Scaffold Astro in `site/`; wire the 13 markdown files from `../docs/`
   (sidebar mirroring `docs/README.md`, per-page table of contents,
   prev/next navigation, code blocks and tables, responsive layout).
8. Follow the chosen build path (comp-led gates via `impeccable
   build-phase`, or code-led with the ambition carried by the direction
   contract).

## Phase 4 — Verify & finish (bounded: ≤ 2 capture rounds)

9. Serve locally; one batched capture round (1440 desktop + 390 mobile →
   `.impeccable/review/`), run `impeccable detect`, fix everything in
   one batch, confirm with one final round.
10. Spawn `impeccable-finish-reviewer`, act on its disposition
    (recapture / fix / ship), then `impeccable-documenter` → root
    `DESIGN.md` + `.impeccable/design.json`.

## Phase 5 — Ship

11. Update `docs/product/ROADMAP.md`, `CHANGELOG.md` and the `AGENTS.md`
    structure table in the same PR (repo rule 8).
12. Verify `./gradlew :app:assembleDebug` and
    `./gradlew :app:testDebugUnitTest` still pass (no app code is
    touched).
13. Push `feat/site-documentation`, open the PR — **no merge without the
    user's explicit confirmation.** GitHub Pages workflow only if the
    user asks.

## Status

- [x] Plan agreed and committed in `site/PLAN.md`
- [ ] Phase 1 — init
- [ ] Phase 2 — direction
- [ ] Phase 3 — build
- [ ] Phase 4 — verify & finish
- [ ] Phase 5 — ship
