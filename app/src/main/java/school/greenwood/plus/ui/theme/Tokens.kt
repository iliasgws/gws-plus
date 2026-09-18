package school.greenwood.plus.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Jetons étendus (DESIGN.md §5) : les six rôles du registre exposés au-delà
 * du MaterialTheme, pour que « redPen » reste sémantique dans tout le code UI.
 */

@Immutable
data class GwsColors(
    val ink: Color,
    val paper: Color,
    val page: Color,
    val sage: Color,
    val redPen: Color,
    val chalk: Color,
)

fun lightGwsColors() = GwsColors(
    ink = InkLight,
    paper = PaperLight,
    page = PageLight,
    sage = SageLight,
    redPen = RedPenLight,
    chalk = ChalkLight,
)

fun darkGwsColors() = GwsColors(
    ink = InkDark,
    paper = PaperDark,
    page = PageDark,
    sage = SageDark,
    redPen = RedPenDark,
    chalk = ChalkDark,
)

val LocalGwsColors = staticCompositionLocalOf { lightGwsColors() }

/** Accesseur des jetons du registre : `RegistreTheme.colors.redPen` etc. */
object RegistreTheme {
    val colors: GwsColors
        @Composable
        get() = LocalGwsColors.current
}
