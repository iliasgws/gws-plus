# AGENTS.md

Guidance for AI agents (and humans) working in the **gws-plus** repository — the home of **Greenwood School +**, an enhanced version of the Greenwood School app.

## Project overview

Greenwood School + is an application that lets parents of pupils follow their children's school activity in real time:

- 📔 Cahier de liaison et de devoirs (homework & liaison notebook)
- 📰 Actualités de l'école (school news)
- 📁 Espace documents (document space)
- 📝 Suivi des demandes administratives (administrative request tracking)
- ✉️ Contacter l'administration de l'école en ligne (online contact with school administration)

**Current status:** the repository is in its documentation/planning phase — there is no application code yet. Do not assume frameworks, build tools, or dependency files exist; verify against the actual repository state before acting.

**Current priority (from README):** fix Android back-gesture navigation — opening the homework section and then using the Android system back gesture must behave correctly in every section of the app. Do not regress this behaviour.

## Language rules

- **User-facing text is French.** Every French word MUST keep its proper accents: é, è, ê, ë, à, â, ç, î, ï, ô, û, ù, ü, ÿ, œ. Never strip accents from French text — not in UI strings, not in documentation, not in commit messages, not in PR titles. This includes capitalized words: write « École », not « Ecole »; « élèves », not « eleves ».
- **Agent-facing documentation (including this file) is English**, so any agent can work with it regardless of locale.
- When editing existing French content, preserve existing accents and fix any that are missing. Before submitting, re-read all French text you touched and check every word for correct accents.

## Repository structure

Current contents (update this section whenever files are added or removed):

| Path | Purpose |
| --- | --- |
| `README.md` | Project description, in French |
| `app-description.png` | Screenshot/illustration of the app description |
| `AGENTS.md` | This file — rules for AI agents |

When application code lands, document its layout, key directories, and entry points here so agents can orient in seconds.

## Ground rules for agents

1. **Verify, don't invent.** Base every statement and change on the actual repository state. If something isn't in the repo yet, say so instead of guessing.
2. **Respect the language rules above.** French content keeps its accents; agent-facing docs stay in English.
3. **Naming.** The project is called « Greenwood School » (renamed from the legacy name in commit `2c372fc`). Use the current name everywhere.
4. **Branch + pull request workflow.** Make every change on a feature branch and open a pull request — this matches the existing history (PRs #1, #2, #3). Never commit directly to `main`.
5. **Commit and PR style.** Short, imperative messages, consistently in one language per commit (French or English), e.g. « Corriger la navigation par geste de retour Android » or "Fix Android back-gesture navigation".
6. **No secrets.** Never commit credentials, API keys, tokens, or other sensitive data.
7. **Stay surgical.** Make precise, complete changes that fully address the task; avoid unrelated changes and do not fix unrelated pre-existing issues.
8. **Keep documentation in sync.** When you add or change features, update `README.md` (including « Priorité actuelle ») and this file's structure section in the same PR.

## How to verify work

There is no build or test suite yet. Until code exists, verify changes by:

- Checking Markdown renders correctly (valid syntax, tables, and links).
- Confirming no file references point to paths that don't exist.
- Re-checking every French word for correct accents (see Language rules).
- Reviewing the diff for accidental scope creep.

Once the application code is added, replace this section with the real commands (build, lint, test) and mark which ones must pass before a PR can be merged.
