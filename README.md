# Greenwood School +

Une version améliorée de l'application Greenwood School.

## À propos de cette application

Cette application permet aux parents d'élèves de suivre en temps réel toute l'activité de leurs enfants à l'école :

- 📔 Cahier de liaison et de devoirs
- 📰 Actualités de l'école
- 📁 Espace documents
- 📝 Suivi des demandes administratives
- ✉️ Contacter l'administration de l'école en ligne

## Priorité actuelle

- 🧭 **Correction de la navigation par le geste de retour Android** — l'ouverture de la section des devoirs puis l'utilisation du geste de retour système d'Android ne fonctionne pas ; la navigation doit se comporter correctement dans toutes les sections de l'application.

## Documentation API

L'application officielle Greenwood School (éditée par Boti Education) est un
wrapper web : son protocole réseau a été documenté par rétro-ingénierie
statique. La spécification dont ce projet s'inspire vit dans [`docs/`](docs/) :

- [`docs/BOTI-API.md`](docs/BOTI-API.md) — protocole : base URL, authentification (`keyToken`), enveloppe de requête, média signés, pièges connus (`paltform`, corps des annonces uniquement dans `admin_nouveautes`, …) et table de correspondance fonction → endpoints
- [`docs/endpoints.md`](docs/endpoints.md) — inventaire des 100 endpoints de l'app officielle
- [`docs/SECURITY-NOTES.md`](docs/SECURITY-NOTES.md) — constats de sécurité sur l'app officielle et ce que gws-plus doit faire mieux

Source publique : [greenwood-school-re](https://gitea.oimcloud.myaddr.tools/Omarchy-Big-PC/greenwood-school-re).
API non documentée côté éditeur : elle peut changer sans préavis ; garder
tous les appels derrière une couche client dédiée, et jamais d'identifiants
dans le dépôt.

