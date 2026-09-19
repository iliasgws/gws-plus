# Greenwood School + — vue d'ensemble

Un client Android natif, non officiel, pour les parents de l'école Greenwood :
suivre en temps réel l'activité scolaire de leurs enfants — et écrire à
l'administration depuis une interface qui respecte le lecteur.

Le produit visé tient en un écran central (le registre) et quatre onglets ;
le design complet est décrit dans [`DESIGN.md`](DESIGN.md), l'état de la
construction dans [`ROADMAP.md`](ROADMAP.md).

## Les six fonctions

| Fonction | Ce qu'elle fait | État |
| --- | --- | --- |
| 📔 **Registre** | Fil chronologique du jour : actualités, devoirs donnés, absences, messages + carte focale « Ce soir » | ✔︎ en service |
| 📚 **Devoirs** | Liste par échéance (`date_remise`), pièces jointes téléchargées puis ouvertes dans le lecteur du système | ✔︎ en service |
| 📰 **Actualités** | Corps complets diffusés depuis `admin_nouveautes` (~19 Mo, lecture bornée en mémoire), images et PDF | ✔︎ en service |
| 📁 **Documents** | Ressources pédagogiques groupées par matière, recherche | ✔︎ en service (le compte de référence ne voit que les quiz — à revoir sur un vrai compte riche) |
| ✉️ **Messagerie** | Fils de conversation, réponse et nouveau message (sujet + catégorie serveur), pièces jointes (1 Mo max sur la voie « nouveau »), messages vocaux, envoi optimiste avec relance | ✔︎ en service — envoi réel validé le 19/09/2026 |
| 📝 **Demandes** | Lecture seule (statut, réponses) ; la création reste désactivée, champs d'écriture non vérifiés | ⚠︎ lecture seule |

## Ce qui distingue le projet

- **Zéro donnée personnelle dans le dépôt.** Le protocole a été reconstitué
  par observation (compte de référence), les réponses brutes restent hors du
  dépôt (voir [`../security/SECURITY-NOTES.md`](../security/SECURITY-NOTES.md)).
- **Vérifier avant d'écrire.** Chaque chemin réseau est marqué vérifié ou non
  dans [`ROADMAP.md`](ROADMAP.md) ; un champ non vérifié n'est jamais branché
  sur un chemin utilisateur (un envoi raté ne doit jamais être silencieux).
- **« Le registre »** comme direction visuelle : encre, papier, annotations
  sage, un rouge stylo réservé à l'action requise. Voir [`DESIGN.md`](DESIGN.md).

## Où commencer

- Côté produit : [`DESIGN.md`](DESIGN.md)
- Côté code : [`../development/ARCHITECTURE.md`](../development/ARCHITECTURE.md)
- Côté protocole : [`../api/BOTI-API.md`](../api/BOTI-API.md)
