# Contribuer à Greenwood School +

Merci de l'intérêt ! Le projet est jeune et les règles sont simples.

## Avant de proposer

1. **Ouvre une issue d'abord** pour toute fonctionnalité nouvelle — le
   protocole réseau impose de vérifier avant d'écrire (un envoi raté ne doit
   jamais être silencieux pour un parent).
2. Les faits de protocole viennent de
   [`docs/api/BOTI-API.md`](docs/api/BOTI-API.md) et
   [`docs/api/ENDPOINT-MAP.md`](docs/api/ENDPOINT-MAP.md) ; tout ce qui y est
   marqué UNVERIFIED demande une vérification réelle avant d'être branché.

## Les règles non négociables

- **Le français garde ses accents** — é, è, ê, à, ç, œ… partout : chaînes UI,
  docs, messages de commit. « École », jamais « Ecole ».
- **Aucun secret, aucune donnée personnelle** dans le dépôt — pas de jetons,
  pas de captures contenant votre nom ou celui de votre enfant, pas de logs
  de paramètres de requête (voir
  [`docs/security/SECURITY-NOTES.md`](docs/security/SECURITY-NOTES.md)).
- **Des commits courts et impératifs**, une seule langue par commit.
- **Une branche par changement, une PR par branche** — jamais de commit
  direct sur `main`.

## Vérifier son travail

```bash
./gradlew :app:assembleDebug       # doit réussir
./gradlew :app:testDebugUnitTest   # doit passer, zéro échec
```

Ajoutez des tests unitaires pour toute logique testable sans Android
(normalisateurs, parsing, fenêtres de dates) — le motif existe dans
`app/src/test/`.

## En résumé

- `README.md` — la porte d'entrée, ne pas en faire un entrepôt de doc
- `docs/` — tout le reste, indexé dans [`docs/README.md`](docs/README.md)
- [`docs/product/ROADMAP.md`](docs/product/ROADMAP.md) — ce sur quoi on
  travaille ; mettez-la à jour dans la même PR que votre changement
