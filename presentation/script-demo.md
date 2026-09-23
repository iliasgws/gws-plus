# Script de démo — Greenwood School + (5 minutes)

Diapos : `presentation/slides.html` — ouvrir dans Chromium/Firefox, **F** = plein écran, **←/→** = naviguer, clic gauche/droit aussi.

Fil conducteur : **pas d'arguments, des faits** — les versions, les PRs, les issues fermées, puis la démo. Tout ce qui est à l'écran existe sur GitHub ; si on demande une preuve, la montrer en direct.

## La veille (checklist)

- [ ] Téléphone chargé, **mode Ne pas déranger** activé
- [ ] App **GWS+ v0.8.2** déjà ouverte sur le Registre (jamais de login en direct devant la classe)
- [ ] Tester le WiFi de l'école **le matin même** — si l'API répond mal → plan B
- [ ] **Plan B** : enregistrer ce soir une vidéo d'écran de 1–2 min qui montre les 4 arrêts de la démo, et la laisser sur le PC
- [ ] Ouvrir `presentation/slides.html`, tester plein écran et les flèches

## Déroulé minute par minute

### 0:00 → 0:20 · Diapo 1 · titre

> « Mon projet : Greenwood School +, l'application de l'école refaite en
> natif pour Android, en Kotlin. Je vous montre d'abord ce qui est fait,
> avec l'historique du projet — puis l'app en vrai. »

### 0:20 → 0:45 · Diapo 2 · ce que fait l'app

> « Sept espaces, un par besoin de la journée : le registre du jour, les
> actualités, l'emploi du temps, les devoirs, les documents, les demandes,
> la messagerie. Je montre les trois principaux à la fin. »

Une phrase, un geste — ne pas lire les sept lignes.

### 0:45 → 1:15 · Diapo 3 · cinq jours, dix-neuf versions

> « Les faits d'abord. La première version du client est datée du 18
> septembre. La dernière, la 0.8.2, du 22. Cinq jours, dix-neuf versions,
> chacune installable depuis GitHub. Voici les grandes étapes. »

Passer vite : les dates et les PRs parlent seules.

### 1:15 → 1:30 · Diapo 4 · les pull requests

> « 41 pull requests fusionnées. Chaque fonction est passée par une branche
> et une pull request : le composeur de messages ici, les squelettes et le
> cache là, l'onglet Actualités, l'Emploi du temps, la Boutique, la
> Bibliothèque des profs, et le système de mise à jour intégré. »

### 1:30 → 1:45 · Diapo 5 · les issues fermées

> « Dix issues fermées, avec les vrais titres : la messagerie qui n'avait
> pas de bouton d'envoi, les documents des profs invisibles, le système de
> mise à jour demandé en issue #46 — construit de zéro. »

Si une question arrive sur une issue précise, ouvrir GitHub en direct (plan B si pas de réseau : les captures locales).

### 1:45 → 2:15 · Diapo 6 · un bug de près (PR #51)

> « Un exemple, parce qu'un bug fermé raconte mieux qu'une liste. Les
> flèches de l'emploi du temps ne changeaient jamais de semaine. Hypothèse :
> envoyer `date=` au serveur. Constat : le serveur l'ignorait. J'ai regardé
> ce que l'application officielle envoie *vraiment* — sonde en lecture seule
> —, corrigé avec les mêmes champs, et la version 0.8.2 est sortie le soir
> même. C'est la PR #51. »

### 2:15 → 2:20 · Diapo 7 · transition

> « Passons à la démo, sur mon téléphone. »

### 2:20 → 4:20 · LA DÉMO — quatre arrêts

| Arrêt | Durée | Ce que tu montres | Ce que tu dis |
|---|---|---|---|
| 1. Registre | ~40 s | Le fil du jour ; la carte « Mise à jour disponible » si elle est là | « C'est le fil du jour : tout ce qui compte arrive ici, dans l'ordre. » |
| 2. Emploi du temps | ~30 s | Les flèches ←/→ pour changer de semaine | « Et c'est exactement le bug d'avant — réparé dans la 0.8.2 que j'ai sur le téléphone. » |
| 3. Devoirs | ~30 s | La liste par échéance, ouvrir une pièce jointe PDF | « Le PDF s'ouvre nativement, sans sortie de l'app. » |
| 4. Messagerie | ~30 s | Un fil avec l'administration (ne pas envoyer en direct) | « On peut écrire à l'administration — l'envoi réel a été validé. » |

Puis revenir au Registre et poser le téléphone écran visible.

### 4:20 → 4:45 · Diapos 8–9 · chiffres et honnêteté

> « Les comptes : 15 789 lignes de Kotlin, 111 commits, 41 PRs, 19 versions.
> Et pour rester honnête : les demandes administratives sont encore en
> lecture seule, et une montre compagnon est en préparation. »

### 4:45 → 5:00 · Diapo 10 · merci

> « Tout est public sur le dépôt — les issues, les PRs, les versions.
> Merci. Des questions ? »

## Si ça plante pendant la démo

- Phrase de secours : « si le serveur a une minute de faiblesse, j'ai prévu
  une vidéo » → ouvrir la vidéo plan B.
- L'app affiche une erreur : la montrer — « l'app ne ment jamais sur un
  échec, c'est un choix de conception » — puis continuer.
- L'app se ferme brutalement : la relancer une fois ; si ça se répète, passer
  directement à la vidéo.

## Questions probables

- **« C'est autorisé, de parler à leur serveur ? »** → J'utilise le compte de
  mon parent, l'exploration s'est faite uniquement en lecture (GET), aucune
  donnée personnelle n'est publiée dans le dépôt, et c'est documenté dans le
  projet (notes de sécurité).
- **« Tu as vraiment tout fait seul ? »** → Répondre honnêtement : j'ai
  travaillé avec des assistants IA pour certaines parties, mais chaque
  décision, chaque test et chaque ligne est passée par moi — et le protocole a
  été vérifié à la main, sur un vrai téléphone. *(À ajuster selon ta réalité.)*
- **« Comment tu as trouvé l'API ? »** → En observant les requêtes de l'app
  officielle avec mon propre compte, et en documentant tout.
- **« 41 PRs, c'est beaucoup pour six jours ? »** → Ce sont de petites
  PRs : une fonction, une branche, une fusion. L'historique complet est
  public.
- **« Les autres peuvent l'installer ? »** → Oui, chaque version est
  téléchargeable sur GitHub — c'est l'étape suivante : la diffuser aux
  familles.

## Si le temps manque (version 3 min)

- Diapos 3–5 : une phrase chacune (« cinq jours, dix-neuf versions, 41 PRs,
  dix issues fermées — tout est public »).
- Diapo 6 : garder, c'est le meilleur moment oral.
- Démo : registre + emploi du temps seulement.
- Diapo 9 (appris) : passer en oral.
