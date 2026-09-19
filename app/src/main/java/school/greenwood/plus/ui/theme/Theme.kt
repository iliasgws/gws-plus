package school.greenwood.plus.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/*
 * Thème « Le registre » (docs/product/DESIGN.md §2) : encre et papier réinventés.
 * Material 3 porte la mécanique (composants, ripple, accessibilité) ; les
 * six jetons du registre circulent en plus via CompositionLocal.
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
    surfaceContainer = PageLight,
    surfaceContainerHigh = PageLight,
    surfaceContainerHighest = PageLight,
    outline = ChalkLight,
    outlineVariant = SageLight,
    error = RedPenLight,
    onError = PageLight,
    errorContainer = SageLight,
    onErrorContainer = RedPenLight,
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
    surfaceContainer = PageDark,
    surfaceContainerHigh = PageDark,
    surfaceContainerHighest = PageDark,
    outline = ChalkDark,
    outlineVariant = SageDark,
    error = RedPenDark,
    onError = PaperDark,
    errorContainer = SageDark,
    onErrorContainer = RedPenDark,
)

@Composable
fun GwsPlusTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) darkColorScheme() else lightColorScheme()
    val gwsColors = if (darkTheme) darkGwsColors() else lightGwsColors()

    CompositionLocalProvider(LocalGwsColors provides gwsColors) {
        MaterialTheme(
            colorScheme = scheme,
            typography = GwsTypography,
            shapes = GwsShapes,
            content = content,
        )
    }
}
