package school.greenwood.plus.ui.theme

import androidx.compose.ui.graphics.Color

/*
 * Palette « Le registre » (DESIGN.md §2). L'encre verte écrit ce qui compte,
 * le rouge stylo signale uniquement ce qui réclame une action parent — il ne
 * décore jamais. Les deux thèmes partagent les rôles, seuls les valeurs bougent.
 */

// — Encre de Greenwood : titres, texte principal, éléments actifs
val InkLight = Color(0xFF1F3D2B)
val InkDark = Color(0xFFE8F0E9)

// — Fond de l'app
val PaperLight = Color(0xFFFAFAF7)
val PaperDark = Color(0xFF121814)

// — Surfaces : cartes registre, feuilles
val PageLight = Color(0xFFFFFFFF)
val PageDark = Color(0xFF1B241E)

// — Conteneurs passifs, puces, séparateurs doux
val SageLight = Color(0xFFE9EFE7)
val SageDark = Color(0xFF243026)

// — Rouge stylo de correction : action requise uniquement (badge, CTA « signer »)
val RedPenLight = Color(0xFFB3382A)
val RedPenDark = Color(0xFFF0917F)

// — Texte secondaire, horodatages, états vides
val ChalkLight = Color(0xFF6B7A6E)
val ChalkDark = Color(0xFF93A396)
