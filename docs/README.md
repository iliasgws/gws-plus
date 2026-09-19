# Documentation — gws-plus

Tout ce qui fait tourner le projet, rangé par intention. Le `README.md` de
la racine reste la porte d'entrée ; tout le reste vit ici.

## Product — le produit et son design

| Doc | Contenu |
| --- | --- |
| [`product/OVERVIEW.md`](product/OVERVIEW.md) | Ce que l'app fait, pour qui, ce qui est vérifié ou non |
| [`product/DESIGN.md`](product/DESIGN.md) | Direction visuelle « Le registre », contrat de navigation, périmètre |
| [`product/ROADMAP.md`](product/ROADMAP.md) | Checklist vivante : milestone en cours + feuille de route |

## Development — comment c'est construit

| Doc | Contenu |
| --- | --- |
| [`development/SETUP.md`](development/SETUP.md) | Toolchain, build, tests, structure du dépôt |
| [`development/ARCHITECTURE.md`](development/ARCHITECTURE.md) | Couches (UI → ViewModel → Repository → BotiClient), session, médias signés |
| [`development/NAVIGATION.md`](development/NAVIGATION.md) | Le contrat de navigation : onglets, piles, retour prédictif |

## Boti API research — le protocole reconstitué

| Doc | Contenu |
| --- | --- |
| [`api/BOTI-API.md`](api/BOTI-API.md) | Le protocole : enveloppe, session, pièges connus |
| [`api/ENDPOINT-MAP.md`](api/ENDPOINT-MAP.md) | Formes observées, endpoint par endpoint |
| [`api/ENDPOINTS.md`](api/ENDPOINTS.md) | Inventaire complet des 100 endpoints de l'APK |

## Security

| Doc | Contenu |
| --- | --- |
| [`security/SECURITY-NOTES.md`](security/SECURITY-NOTES.md) | Observations de sécurité, règles (pas de secrets, pas de données personnelles) |

---

Pour les agents (humains aussi) : [`../AGENTS.md`](../AGENTS.md) — les règles
de travail dans ce dépôt.
