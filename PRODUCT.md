# Product

<!-- impeccable:product-schema 1 -->

## Platform

web

## Stack

Astro (delegated: the user left the stack to the agent's recommendation — markdown-native, static output, full design control; no framework was imposed on them twice).

## Users

Primary: parents of Greenwood School pupils — non-technical, mostly on a
phone, French-speaking. They arrive to understand what Greenwood School +
is and to install it. Secondary readers of the published documentation
(contributors, humans and AI agents) are served by the docs section but
are not the primary audience.

## Product Purpose

The project's documentation and public website: present Greenwood School +
to parents (what it does, why to trust it, how to get it) and publish the
project's documentation set in one browsable place. Success means a parent
understands the app within seconds and downloads the APK from GitHub
Releases.

## Positioning

An independent, unofficial native Android client (Kotlin + Jetpack
Compose) that rebuilds the Greenwood School app experience: the day's
registre, homework, documents, messages with the administration — in a
fast, sober « Le registre » interface. The network protocol was
reconstructed by observation; every endpoint is marked verified or not,
and no personal data ever enters the repository.

## Operating Context

- Distribution is direct: GitHub Releases APKs, no app store. Installing
  means allowing « apps inconnues » on Android; the update checker of
  issue #46 also lives on those Releases.
- Site content comes from the repository's `docs/` set (curated public
  subset) and the root README facts; it is rebuilt whenever those files
  change.
- Everything user-facing is French, with proper accents.
- The repo rules in AGENTS.md apply: feature branch + PR, no merge or
  publish without explicit user confirmation.

## Capabilities and Constraints

- Static site only: no backend, no analytics with personal data, no
  invented forms of contact.
- Publishes the curated public documentation set (11 files: product,
  development, API research, security, and the docs index) — not the
  internal planning notes (`COMPTE_RENDU.md`, `PLAN-NOUVEAUTES.md`).
- Must preserve the independence disclaimer: unofficial, not affiliated
  with Greenwood School nor Boti Education, no personal data in the repo.
- Must not fabricate proof: there are currently no app screenshots in
  the repository (see Evidence on Hand).
- All website work lives in the `site/` folder at the repository root.
- No application code may be changed by this work.

## Brand Commitments

- Name: « Greenwood School + » (never the legacy name).
- The app's custom icon is the identity mark: the « arbre et + »
  adaptive icon (`app/src/main/res/mipmap-*/ic_launcher_*.png`) and the
  ink-page monogram with the red pen dot
  (`app/src/main/res/drawable/ic_launcher_monochrome.xml`). It is used as
  site chrome (header logo, favicon set) only — never as a hero element,
  per the user's explicit instruction.
- « Le registre » is the established product identity
  (`docs/product/DESIGN.md`); the app's design document is not replaced
  by this website work.
- Voice: sober, reader-respecting, French with correct accents.

## Evidence on Hand

- 11 public documentation files under `docs/` (index, product, development,
  API research, security) — the site's content source.
- Root `README.md` facts (features, status, stack, startup commands).
- GitHub Releases: published APKs through v0.9.3-beta.1 (download targets).
- Icon assets listed under Brand Commitments.
- Absences future work must not fabricate: no app screenshots in the
  repository yet (README carries a TODO for them), no testimonials, no
  usage statistics, no pricing, no affiliation with the school.

## Product Principles

1. Verify before publishing — no claim, endpoint, or asset goes on the
   site unless it exists in the repository or was confirmed by the user.
2. The parent decides — clarity and the download action outrank technical
   exposition; documentation serves, never obstructs, that path.
3. One identity — the site is an extension of « Le registre » and the
   app's icon; it never invents a second mark or voice.
4. Say what is true about the state — development is active, demandes
   remain read-only, the client is unofficial.
5. Zero personal data — the site touches no analytics, forms, or stored
   visitor information beyond what static hosting implies.
