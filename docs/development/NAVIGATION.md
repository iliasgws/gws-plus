# Navigation — le contrat

La navigation est la priorité n°1 du projet : un geste de retour qui se
comporte pareil partout, des piles qui ne se mélangent jamais.

## Trois états racine

`AppNav.kt` ne pilote pas un `NavController` racine mais un `Crossfade` sur
l'état de session (DataStore) :

```
Chargement → Onboarding (premier lancement) → Connexion → Registre (la coquille)
```

Quand le serveur tue la session (`disconnect: true`), l'état retombe sur la
connexion, **où que l'on soit** : `SessionStore` émet un événement, la
coquille dépille sa pile et la racine bascule.

## La coquille : quatre onglets, quatre piles

`Shell` : un `Scaffold` + barre basse (Registre, Devoirs, Documents,
Messages) autour d'un `NavHost`. Chaque onglet garde sa pile via
`saveState`/`restoreState` (`allerÀLOnglet`) :

- retour depuis un onglet racine → Registre ;
- retour depuis le Registre → quitter l'app ;
- jamais de racine poussée sur une pile de détail (`allerDétail` ne dépille
  rien).

Les détails (post, demandes, conversation, nouveau message) poussent sur la
pile de leur onglet avec `launchSingleTop`.

## L'onglet actif et les sous-pages (issue #34)

La barre basse ne lit pas la route exacte : `ongletActifDe` (AppNav.kt) lit la
pile complète (`NavController.currentBackStack`, StateFlow public) et prend le
**dernier écran racine d'onglet** qu'elle contient. C'est l'onglet qui
« possède » la sous-page affichée au-dessus :

- post ouvert depuis le Registre → l'onglet Registre reste actif ;
- post ouvert depuis Actualités → l'onglet Actualités reste actif ;
- retour arrière → la pile se recalcule et l'onglet d'origine se remet en
  surbrillance, sans état mémorisé à réinitialiser.

La couleur d'accent de l'écran (voir `accentDe`) reste, elle, statique : le
détail d'une actualité porte toujours la teinte Actualités, même ouvert depuis
le Registre — la pile pilote la sélection, la table pilote la teinte.

## Retour prédictif

`enableOnBackInvokedCallback` dans le manifeste, edge-to-edge partout :
le geste système prédit le retour sur tous les écrans. **À ne pas régresser**
— c'est le point de vigilance n°1 des revues.

## Composeur et clavier

Les écrans avec composeur (conversation, nouveau message) appliquent
`imePadding()` : le clavier repousse le contenu, la barre d'envoi reste
visible au-dessus de la zone de saisie.
