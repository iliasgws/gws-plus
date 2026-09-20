package school.greenwood.plus.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/*
 * Thème « École vivante » (docs/product/DESIGN.md §2) : crème et accents de
 * cahiers. Material 3 porte la mécanique (composants, ripple, accessibilité) ;
 * les jetons d'encre, papier et accents circulent en plus via CompositionLocal.
 * Les marches surfaceContainer* sont désormais distinctes : les éléments
 * surélevés se lisent comme surélevés (profondeur tonale).
 */

private fun lightColorScheme(): ColorScheme = lightColorScheme(
    primary = InkLight,
    onPrimary = PageLight,
    primaryContainer = SageLight,
    onPrimaryContainer = InkLight,
    secondary = InkLight,
    onSecondary = PageLight,
    secondaryContainer = SageLight,
    onSecondaryContainer = InkLight,
    tertiary = ChalkLight,
    onTertiary = PageLight,
    background = PaperLight,
    onBackground = InkLight,
    surface = PageLight,
    onSurface = InkLight,
    surfaceVariant = SageLight,
    onSurfaceVariant = ChalkLight,
    surfaceContainerLowest = PageLight,
    surfaceContainerLow = Color(0xFFFBF7EE),
    surfaceContainer = Color(0xFFF5EEDF),
    surfaceContainerHigh = Color(0xFFEFE7D6),
    surfaceContainerHighest = Color(0xFFE9E0CD),
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    error = RedPenLight,
    onError = PageLight,
    errorContainer = ErreurConteneurLight,
    onErrorContainer = SurErreurConteneurLight,
)

private fun darkColorScheme(): ColorScheme = darkColorScheme(
    primary = InkDark,
    onPrimary = PaperDark,
    primaryContainer = SageDark,
    onPrimaryContainer = InkDark,
    secondary = InkDark,
    onSecondary = PaperDark,
    secondaryContainer = SageDark,
    onSecondaryContainer = InkDark,
    tertiary = ChalkDark,
    onTertiary = PaperDark,
    background = PaperDark,
    onBackground = InkDark,
    surface = PageDark,
    onSurface = InkDark,
    surfaceVariant = SageDark,
    onSurfaceVariant = ChalkDark,
    surfaceContainerLowest = Color(0xFF171E18),
    surfaceContainerLow = Color(0xFF1A231B),
    surfaceContainer = Color(0xFF212B21),
    surfaceContainerHigh = Color(0xFF252F25),
    surfaceContainerHighest = Color(0xFF2A3429),
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    error = RedPenDark,
    onError = PaperDark,
    errorContainer = ErreurConteneurDark,
    onErrorContainer = SurErreurConteneurDark,
)

@Composable
fun GwsPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val gwsColors = if (darkTheme) darkGwsColors() else lightGwsColors()

    // Le fond système suit le thème : les icônes des barres restent lisibles.
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            runCatching {
                (view.context as? Activity)?.window?.let { fenêtre ->
                    val contrôleur = WindowCompat.getInsetsController(fenêtre, view)
                    contrôleur.isAppearanceLightStatusBars = !darkTheme
                    contrôleur.isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }

    CompositionLocalProvider(
        LocalGwsColors provides gwsColors,
        LocalGwsAccent provides gwsColors.accents.getValue("registre"),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GwsTypography,
            shapes = GwsShapes,
            content = content,
        )
    }
}
