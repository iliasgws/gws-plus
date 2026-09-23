# Script de démo — Greenwood School + (5 minutes)

Diapos : `presentation/slides.html` — ouvrir dans Chromium/Firefox, **F** = plein écran, **←/→** = naviguer, clic gauche/droit aussi.

## La veille (checklist)

- [ ] Téléphone chargé, **mode Ne pas déranger** activé
- [ ] App **GWS+ v0.8.2** déjà ouverte sur le Registre (jamais de login en direct devant la classe)
- [ ] Tester le WiFi de l'école **le matin même** — si l'API répond mal → plan B
- [ ] **Plan B** : enregistrer ce soir une vidéo d'écran de 1–2 min qui montre les 4 arrêts de la démo, et la laisser sur le PC
- [ ] Ouvrir `presentation/slides.html`, tester plein écran et les flèches

## Déroulé minute par minute

### 0:00 → 0:30 · Diapos 1–2 · le problème

> « Bonjour. Mon projet part d'un constat simple : l'école a une application
> officielle, mais elle est lente, et tout y est mélangé — l'essentiel se noie.
> Je l'ai reconstruite de zéro en application Android native. »

*Ne pas lire toute la diapo 2 ; la fiche officielle est juste là pour l'authenticité.*

### 0:30 → 1:00 · Diapo 3 · ce que j'ai construit

> « Sept espaces, un par besoin de la journée. Je vous montre les trois
> principaux en vrai juste après : le registre, les devoirs, la messagerie. »

Ne pas lire les sept lignes — les montrer d'un geste.

### 1:00 → 1:45 · Diapo 4 · la démarche (la diapo la plus importante)

> « J'ai procédé comme un ingénieur. **Observer** : j'ai sondé l'API de la
> plateforme en lecture seule — jamais une seule requête d'écriture pendant
> l'exploration. **Documenter** : chaque endpoint, chaque paramètre, chaque
> piège, notés dans des fiches. **Construire** en Kotlin et Jetpack Compose.
> **Publier** : chaque fonction part en bêta sur mon téléphone, puis devient
> stable. »

### 1:45 → 2:15 · Diapo 5 · le bug des semaines

> « Un exemple concret de méthode. Les flèches de l'emploi du temps ne
> changeaient jamais de semaine. Mon hypothèse : envoyer `date=` au serveur.
> Constat : le serveur l'ignorait totalement. J'ai donc observé ce que
> l'application officielle envoie *vraiment*, j'ai corrigé avec les mêmes
> champs, vérifié, publié. Hypothèse, test, preuve, correction — comme en TP. »

### 2:15 → 2:20 · Diapo 6 · transition

> « Passons à la démo, sur mon téléphone. »

### 2:20 → 4:20 · LA DÉMO — quatre arrêts

| Arrêt | Durée | Ce que tu montres | Ce que tu dis |
|---|---|---|---|
| 1. Registre | ~40 s | Le fil du jour ; la carte « Mise à jour disponible » si elle est là | « Tout ce qui compte aujourd'hui arrive ici, dans l'ordre. » |
| 2. Emploi du temps | ~30 s | Les flèches ←/→ pour changer de semaine | « Et c'est exactement le bug de la diapo précédente — réparé hier. » |
| 3. Devoirs | ~30 s | La liste par échéance, ouvrir une pièce jointe PDF | « Le PDF s'ouvre nativement, sans sortie de l'app. » |
| 4. Messagerie | ~30 s | Un fil avec l'administration (ne pas envoyer en direct) | « On peut écrire à l'administration — les envois sont validés en conditions réelles. » |

Puis revenir au Registre et poser le téléphone écran visible.

### 4:20 → 4:45 · Diapos 7–8 · chiffres et bilan

> « Au total : 15 789 lignes de Kotlin, 107 commits, 18 versions publiées.
> Et pour être honnête sur ce qui reste : les demandes administratives sont
> encore en lecture seule, et une montre compagnon est en préparation. »

### 4:45 → 5:00 · Diapo 9 · merci

> « Voilà. Merci — des questions ? »

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
- **« Les autres peuvent l'installer ? »** → Oui, chaque version est
  téléchargeable sur GitHub — c'est l'étape suivante : la diffuser aux
  familles.

## Si le temps manque (version 3 min)

- Diapo 3 : une phrase au lieu des sept lignes.
- Démo : registre + emploi du temps seulement.
- Diapos 7–8 : garder les chiffres, passer le bilan en oral.