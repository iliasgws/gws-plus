package school.greenwood.plus.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette « Liquid glass » (docs/product/DESIGN.md §2). L'encre verte de Greenwood
 * écrit toujours ce qui compte, le rouge stylo signale toujours ce qui réclame
 * une action parent — mais les surfaces ne sont plus du papier : un fond aurore
 * doux derrière du verre translucide. Les deux thèmes partagent les rôles,
 * seules les valeurs bougent.
 */

// — Encre de Greenwood : titres, texte principal, éléments actifs
val InkLight = Color(0xFF1F3D2B)
val InkDark = Color(0xFFE8F0E9)

// — Fond de l'app : base de l'aurore derrière tout le verre
val PaperLight = Color(0xFFEFF3ED)
val PaperDark = Color(0xFF0C110E)

// — Texte posé sur une teinte pleine (encre, stylo rouge) ; page sombre aussi
//   base des fills de verre en mode sombre
val PageLight = Color(0xFFFFFFFF)
val PageDark = Color(0xFF1B241E)

// — Teintes pleines sémantiques : bulles admin, état correct du quiz,
//   pastille « Ce soir », indicateur d'onglet actif
val SageLight = Color(0xFFE9EFE7)
val SageDark = Color(0xFF243026)

// — Rouge stylo de correction : action requise uniquement (badge, CTA « signer »)
val RedPenLight = Color(0xFFB3382A)
val RedPenDark = Color(0xFFF0917F)

// — Texte secondaire, horodatages, états vides. Plus sombre que l'ancien craie :
//   il doit rester lisible à travers une feuille de verre translucide.
val ChalkLight = Color(0xFF5F6F63)
val ChalkDark = Color(0xFF93A396)
