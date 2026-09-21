package school.greenwood.plus.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette « École vivante » (docs/product/DESIGN.md §2) : le crème chaud de la
 * cour d'école, l'encre verte qui écrit ce qui compte, et une famille d'accent
 * par onglet — comme les couvertures de cahiers par matière. Le rouge stylo
 * garde sa règle d'or : il signale uniquement ce qui réclame une action
 * parent, il ne décore jamais. Chaque rôle a une valeur claire et une sombre.
 */

// — Encre de Greenwood : titres, texte principal, éléments actifs
val InkLight = Color(0xFF1F3324)
val InkDark = Color(0xFFE9F0E6)

// — Fond de l'app : crème chaud le jour, vert-charbon la nuit
val PaperLight = Color(0xFFF7F1E5)
val PaperDark = Color(0xFF141B15)

// — Surfaces : cartes, feuilles
val PageLight = Color(0xFFFFFFFF)
val PageDark = Color(0xFF1D271E)

// — Conteneurs passifs, puces neutres, séparateurs doux (sable parchemin)
val SageLight = Color(0xFFEBE6D7)
val SageDark = Color(0xFF262F24)

// — Rouge stylo de correction : action requise uniquement (badge, CTA « signer »)
val RedPenLight = Color(0xFFB3382A)
val RedPenDark = Color(0xFFF0917F)

// — Texte secondaire, horodatages, états vides (assombri : ≥ 5:1 sur crème)
val ChalkLight = Color(0xFF5E6B5C)
val ChalkDark = Color(0xFF9AAB97)

// — Orange vif du signet des actualités (ex #FC942D, désormais un jeton)
val SignetVifLight = Color(0xFFE27A0F)
val SignetVifDark = Color(0xFFFFA94F)

// — Craies des marches d'échelle tonale (profondeur des surfaces surélevées)
val OutlineLight = Color(0xFF949B8C)
val OutlineDark = Color(0xFF6F7B6D)
val OutlineVariantLight = Color(0xFFDDD5C2)
val OutlineVariantDark = Color(0xFF39443A)

// — Bandes d'erreur teintées (M3) : le rouge stylo reste le texte, jamais le fond
val ErreurConteneurLight = Color(0xFFF6DDD6)
val ErreurConteneurDark = Color(0xFF4A231C)
val SurErreurConteneurLight = Color(0xFF8C2A1F)
val SurErreurConteneurDark = Color(0xFFF6B3A6)

/*
 * Familles d'accent par onglet — teinte (texte/glyphes sur carte),
 * conteneur (fond doux), surConteneur (texte sur conteneur). Toutes les
 * paires vérifiées ≥ 4,5:1 en texte, ≥ 3:1 en glyphe.
 */

// — Registre : le vert de Greenwood
val VertAccentTeinteLight = Color(0xFF256B3B)
val VertAccentConteneurLight = Color(0xFFDDEBD3)
val VertAccentSurConteneurLight = Color(0xFF1B4226)
val VertAccentTeinteDark = Color(0xFF8ED6A4)
val VertAccentConteneurDark = Color(0xFF1F3A26)
val VertAccentSurConteneurDark = Color(0xFFC8EACF)

// — Actualités : l'ambre des affiches
val AmbreTeinteLight = Color(0xFFA85E10)
val AmbreConteneurLight = Color(0xFFFAE6CC)
val AmbreSurConteneurLight = Color(0xFF6E3D08)
val AmbreTeinteDark = Color(0xFFFFB35C)
val AmbreConteneurDark = Color(0xFF452B10)
val AmbreSurConteneurDark = Color(0xFFFFD9A8)

// — Cours : le bleu de la math
val BleuTeinteLight = Color(0xFF21618F)
val BleuConteneurLight = Color(0xFFD9E7F2)
val BleuSurConteneurLight = Color(0xFF16456B)
val BleuTeinteDark = Color(0xFF96C6EC)
val BleuConteneurDark = Color(0xFF17324A)
val BleuSurConteneurDark = Color(0xFFCCE4F6)

// — Devoirs : le violet de l'étude
val VioletTeinteLight = Color(0xFF6B3FA8)
val VioletConteneurLight = Color(0xFFE9E1F6)
val VioletSurConteneurLight = Color(0xFF4A2B7A)
val VioletTeinteDark = Color(0xFFCDA9F4)
val VioletConteneurDark = Color(0xFF382856)
val VioletSurConteneurDark = Color(0xFFE7DBF9)

// — Documents : l'ocre du rangement
val OcreTeinteLight = Color(0xFF7D5E00)
val OcreConteneurLight = Color(0xFFF4E8C6)
val OcreSurConteneurLight = Color(0xFF574100)
val OcreTeinteDark = Color(0xFFE6C368)
val OcreConteneurDark = Color(0xFF3D340D)
val OcreSurConteneurDark = Color(0xFFF5E5AB)

// — Messages / Demandes : le corail de la correspondance
//   (framboise ~350°, distinct du rouge stylo ~8° : les deux ne cohabitent
//   jamais dans un même composant)
val CorailTeinteLight = Color(0xFFC13B69)
val CorailConteneurLight = Color(0xFFF9DEE6)
val CorailSurConteneurLight = Color(0xFF8C2149)
val CorailTeinteDark = Color(0xFFF59FB4)
val CorailConteneurDark = Color(0xFF481D2C)
val CorailSurConteneurDark = Color(0xFFFBD2DC)

// — Plus / Boutique : la sarcelle de l'extra-scolaire (le « Plus » de la
//   barre, la Boutique de l'école) — ~180°, la teinte libre entre le vert
//   du Registre (~150°) et le bleu des Cours (~210°)
val SarcelleTeinteLight = Color(0xFF16665E)
val SarcelleConteneurLight = Color(0xFFD3EAE7)
val SarcelleSurConteneurLight = Color(0xFF0B4B46)
val SarcelleTeinteDark = Color(0xFF7FD8CF)
val SarcelleConteneurDark = Color(0xFF173C38)
val SarcelleSurConteneurDark = Color(0xFFA9E5DD)
