package school.greenwood.plus.ui.components

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.lerp as lerpCouleur
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceAtMost
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kashif_e.backdrop.Backdrop
import com.kashif_e.backdrop.backdrops.layerBackdrop
import com.kashif_e.backdrop.backdrops.rememberBackdrop
import com.kashif_e.backdrop.backdrops.rememberCombinedBackdrop
import com.kashif_e.backdrop.backdrops.rememberLayerBackdrop
import com.kashif_e.backdrop.drawBackdrop
import com.kashif_e.backdrop.effects.blur
import com.kashif_e.backdrop.effects.lens
import com.kashif_e.backdrop.effects.vibrancy
import com.kashif_e.backdrop.highlight.Highlight
import com.kashif_e.backdrop.shadow.InnerShadow
import com.kashif_e.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.GwsColors
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.RegistreTheme
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.tanh

/*
 * Primitives « liquid glass » (docs/product/DESIGN.md §2). Toute la bibliothèque
 * backdrop (capture d'arrière-plan, floutage, échantillonnage) est confinée à ce
 * fichier : si son API bouge, seul Glass.kt suit. Trois niveaux de verre :
 * les surfaces calmes (cartes du flux, puces, squelettes) posent une feuille
 * translucide sur l'aurore — GlassSurface, sans floutage, gratuit ; les feuilles
 * réelles (FeuilleVerre, barre flottante) échantillonnent la scène derrière
 * elles — vibrance, flou, réfraction : la partie « liquide » du matériau, sur
 * les petits nœuds où elle se voit. Sous l'API 31 le floutage n'existe pas :
 * tout bascule sur des fills opaques équivalents, conçus pour rester lisibles.
 */

/** Où lire la scène capturée ? Fournie une seule fois à la racine (AppNav) ;
 *  les primitives profondes (FeuilleVerre dans les écrans) la lisent ici
 *  plutôt que de la faire remonter paramètre par paramètre.
 *
 *  Règle de survie : c'est la capture de l'AURORE SEULE. Une feuille de verre
 *  ne peut jamais échantillonner une capture qui la contient — la couche se
 *  ré-enregistrerait avec une référence à elle-même et le premier rendu du
 *  contenu crashe (vu en bêta 2 : squelette affiché, puis crash). Le verre
 *  dans les écrans réfracte donc l'aurore ; seule la barre basse, hors de la
 *  capture de scène, voit le contenu qui défile derrière elle. */
val LocalGlassBackdrop = compositionLocalOf<Backdrop?> { null }

/** Un fond d'aurore : trois halos doux et immobiles sur la base du papier.
 *  Le verre a besoin d'un fond avec de la variation pour être visible —
 *  c'est cette couche qu'il échantillonne. Statique : jamais d'animation
 *  déclenchée par rien (docs/product/DESIGN.md §2). */
@Composable
fun AuroraBackdrop(modifier: Modifier = Modifier) {
    val glass = RegistreTheme.colors.glass
    Box(
        modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                val plusGrandCôté = maxOf(w, h)

                // Vert sauge — coin haut gauche
                val centreA = Offset(w * 0.16f, h * 0.10f)
                // Crème chaud — coin bas droit
                val centreB = Offset(w * 0.96f, h * 0.94f)
                // Bleu-vert pâle — mi-hauteur, bord droit
                val centreC = Offset(w * 1.04f, h * 0.46f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraA, glass.auraA.copy(alpha = 0f)),
                        center = centreA,
                        radius = plusGrandCôté * 0.62f,
                    ),
                    center = centreA,
                    radius = plusGrandCôté * 0.62f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraB, glass.auraB.copy(alpha = 0f)),
                        center = centreB,
                        radius = plusGrandCôté * 0.70f,
                    ),
                    center = centreB,
                    radius = plusGrandCôté * 0.70f,
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glass.auraC, glass.auraC.copy(alpha = 0f)),
                        center = centreC,
                        radius = plusGrandCôté * 0.48f,
                    ),
                    center = centreC,
                    radius = plusGrandCôté * 0.48f,
                )
            },
    )
}

/** Feuille de verre calme : fill translucide + liseré lumineux, sans floutage —
 *  visuellement équivalent à un floutage de l'aurore statique, pour aucun coût.
 *  `strong` passe sur le fill quasi opaque (feuilles modales, fallback API < 31). */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = PageShape,
    strong: Boolean = false,
    content: @Composable () -> Unit,
) {
    val glass = RegistreTheme.colors.glass
    Surface(
        modifier = modifier,
        shape = shape,
        color = if (strong) glass.barStrong else glass.card,
        border = BorderStroke(1.dp, glass.stroke),
        content = content,
    )
}

/** Feuille de verre réelle : elle échantillonne la scène derrière elle —
 *  vibrance, flou, puis réfraction (`lens`, la partie « liquide » du
 *  matériau, API 33+ ; no-op silencieux en dessous). À réserver aux petits
 *  nœuds toujours visibles ou posés sur du contenu qui passe derrière :
 *  une carte du flux n'a rien derrière elle — la réfraction y serait
 *  invisible et ne coûterait que des passes de shader.
 *  Sans capture disponible ou sous l'API 31 : fill opaque équivalent
 *  (`teinte` en pleine opacité, sinon barStrong), liseré conservé. */
@Composable
fun FeuilleVerre(
    modifier: Modifier = Modifier,
    forme: Shape = PageShape,
    teinte: Color? = null,
    liseré: BorderStroke? = null,
    flou: Dp = 12.dp,
    réfraction: Dp = 16.dp,
    vibrant: Boolean = true,
    lumineux: Boolean = true,
    content: @Composable () -> Unit,
) {
    val glass = RegistreTheme.colors.glass
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null || !floutageDisponible) {
        Surface(
            modifier = modifier,
            shape = forme,
            color = teinte?.copy(alpha = 1f) ?: glass.barStrong,
            border = liseré ?: BorderStroke(1.dp, glass.stroke),
            content = content,
        )
    } else {
        Box(
            modifier = modifier
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { forme },
                    effects = {
                        if (vibrant) vibrancy()
                        blur(flou.toPx())
                        lens(réfraction.toPx(), (réfraction * 1.5f).toPx())
                    },
                    highlight = { if (lumineux) Highlight.Default else null },
                    onDrawSurface = { teinte?.let { drawRect(it) } },
                )
                .then(
                    if (liseré != null) {
                        Modifier.border(liseré, forme)
                    } else {
                        Modifier
                    },
                ),
        ) {
            content()
        }
    }
}

/** Réglages de la barre flottante : hauteur, marge flottante, espace total à
 *  réserver sous un contenu qui défile sous la barre. */
object GlassDefaults {
    val BarHeight = 64.dp
    val BarMargin = 12.dp
    val BarTotal = BarHeight + BarMargin
}

/** Le floutage réel existe-t-il ici ? API 31+ : RenderEffect. */
private val floutageDisponible: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/** Bouton liquide — le LiquidButton du catalogue (Apache-2.0) : une capsule
 *  qui se déforme vers le doigt (tanh : translation saturée, étirement
 *  directionnel) pendant l'appui. `surfaceColor` en fait un CTA plein qui
 *  garde la physique du verre ; `tint` fait un verre teinté par la teinte. */
@Composable
fun LiquidButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isInteractive: Boolean = true,
    tint: Color = Color.Unspecified,
    surfaceColor: Color = Color.Unspecified,
    hauteur: Dp = 48.dp,
    paddingHorizontal: Dp = 16.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null || !floutageDisponible) {
        // Repli : bouton plein du thème, même silhouette.
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = isInteractive,
            shape = ControlShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (surfaceColor.isSpecified) surfaceColor else RegistreTheme.colors.ink,
                contentColor = RegistreTheme.colors.page,
            ),
        ) {
            content()
        }
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }

    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ControlShape },
                effects = {
                    vibrancy()
                    blur(2.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx())
                },
                highlight = { null },
                layerBlock = if (isInteractive) {
                    {
                        val width = size.width
                        val height = size.height

                        val progress = interactiveHighlight.pressProgress
                        val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                        val maxOffset = size.minDimension
                        val initialDerivative = 0.05f
                        val offset = interactiveHighlight.offset
                        translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                        translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                        val maxDragScale = 4f.dp.toPx() / size.height
                        val offsetAngle = atan2(offset.y, offset.x)
                        scaleX =
                            scale +
                                maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                                (width / height).fastCoerceAtMost(1f)
                        scaleY =
                            scale +
                                maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                                (height / width).fastCoerceAtMost(1f)
                    }
                } else {
                    null
                },
                onDrawSurface = {
                    if (tint.isSpecified) {
                        drawRect(tint, blendMode = BlendMode.Hue)
                        drawRect(tint.copy(alpha = 0.75f))
                    }
                    if (surfaceColor.isSpecified) {
                        drawRect(surfaceColor)
                    }
                }
            )
            .clickable(
                enabled = isInteractive,
                interactionSource = null,
                indication = null,
                role = Role.Button,
                onClick = onClick
            )
            .then(
                if (isInteractive) {
                    Modifier
                        .then(interactiveHighlight.modifier)
                        .then(interactiveHighlight.gestureModifier)
                } else {
                    Modifier
                }
            )
            .height(hauteur)
            .padding(horizontal = paddingHorizontal),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

/** Bouton d'icône liquide compact. Les actions de barre et de composeur
 *  partagent ainsi la même matière et la même physique que les CTA. */
@Composable
fun LiquidIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    taille: Dp = 44.dp,
    surfaceColor: Color = Color.Unspecified,
    content: @Composable RowScope.() -> Unit,
) {
    val fond = if (surfaceColor.isSpecified) surfaceColor else givre()
    LiquidButton(
        onClick = onClick,
        modifier = modifier.size(taille),
        isInteractive = enabled,
        surfaceColor = fond,
        hauteur = taille,
        paddingHorizontal = 0.dp,
        content = content,
    )
}

/** Interrupteur liquide — le LiquidToggle du catalogue : rail qui se remplit
 *  d'encre, pouce de verre qui grossit à l'appui et échantillonne le rail
 *  compressé (l'effet loupe). Accent = l'encre de Greenwood. */
@Composable
fun LiquidToggle(
    selected: () -> Boolean,
    onSelect: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RegistreTheme.colors
    val backdrop = LocalGlassBackdrop.current
    if (backdrop == null || !floutageDisponible) {
        // Repli : interrupteur du thème, même sémantique.
        Switch(
            checked = selected(),
            onCheckedChange = onSelect,
            modifier = modifier,
            colors = SwitchDefaults.colors(
                checkedTrackColor = colors.ink,
                checkedThumbColor = colors.page,
                checkedBorderColor = colors.ink,
                uncheckedThumbColor = colors.page,
                uncheckedTrackColor = colors.sage,
                uncheckedBorderColor = colors.sage,
            ),
        )
        return
    }

    val accentColor = colors.ink
    val trackColor = colors.sage.copy(alpha = 0.55f)

    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val dragWidth = with(density) { 20.dp.toPx() }
    val animationScope = rememberCoroutineScope()
    var didDrag by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(if (selected()) 1f else 0f) }
    val dampedDragAnimation = remember(animationScope) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = fraction,
            valueRange = 0f..1f,
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 1.5f,
            onDragStarted = {},
            onDragStopped = {
                if (didDrag) {
                    fraction = if (targetValue >= 0.5f) 1f else 0f
                    onSelect(fraction == 1f)
                    didDrag = false
                } else {
                    fraction = if (selected()) 0f else 1f
                    onSelect(fraction == 1f)
                }
            },
            onDrag = { _, dragAmount ->
                if (!didDrag) {
                    didDrag = dragAmount.x != 0f
                }
                val delta = dragAmount.x / dragWidth
                fraction =
                    if (isLtr) (fraction + delta).fastCoerceIn(0f, 1f)
                    else (fraction - delta).fastCoerceIn(0f, 1f)
            }
        )
    }
    LaunchedEffect(dampedDragAnimation) {
        snapshotFlow { fraction }
            .collectLatest { fraction ->
                dampedDragAnimation.updateValue(fraction)
            }
    }
    LaunchedEffect(selected) {
        snapshotFlow { selected() }
            .collectLatest { isSelected ->
                val target = if (isSelected) 1f else 0f
                if (target != fraction) {
                    fraction = target
                    dampedDragAnimation.animateToValue(target)
                }
            }
    }

    val trackBackdrop = rememberLayerBackdrop()

    Box(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            Modifier
                .layerBackdrop(trackBackdrop)
                .clip(ControlShape)
                .drawBehind {
                    val f = dampedDragAnimation.value
                    drawRect(lerpCouleur(trackColor, accentColor, f))
                }
                .size(64.dp, 28.dp)
        )

        Box(
            Modifier
                .graphicsLayer {
                    val f = dampedDragAnimation.value
                    val padding = 2.dp.toPx()
                    translationX =
                        if (isLtr) lerp(padding, padding + dragWidth, f)
                        else lerp(-padding, -(padding + dragWidth), f)
                }
                .semantics {
                    role = Role.Switch
                }
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(
                        backdrop,
                        rememberBackdrop(trackBackdrop) { drawBackdrop ->
                            val progress = dampedDragAnimation.pressProgress
                            val scaleX = lerp(2f / 3f, 0.75f, progress)
                            val scaleY = lerp(0f, 0.75f, progress)
                            scale(scaleX, scaleY) {
                                drawBackdrop()
                            }
                        }
                    ),
                    shape = { ControlShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        blur(8.dp.toPx() * (1f - progress))
                        lens(
                            5.dp.toPx() * progress,
                            10.dp.toPx() * progress,
                            chromaticAberration = true
                        )
                    },
                    highlight = {
                        val progress = dampedDragAnimation.pressProgress
                        Highlight.Ambient.copy(
                            width = Highlight.Ambient.width / 1.5f,
                            blurRadius = Highlight.Ambient.blurRadius / 1.5f,
                            alpha = progress
                        )
                    },
                    shadow = {
                        Shadow(
                            radius = 4.dp,
                            color = Color.Black.copy(alpha = 0.05f)
                        )
                    },
                    innerShadow = {
                        val progress = dampedDragAnimation.pressProgress
                        InnerShadow(
                            radius = 4.dp * progress,
                            alpha = progress
                        )
                    },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 50f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.White.copy(alpha = 1f - progress))
                    }
                )
                .size(40.dp, 24.dp)
        )
    }
}

/** Barre basse liquide — le LiquidBottomTabs du catalogue : la capsule de
 *  verre (vibrance + flou + réfraction), une copie fantôme invisible teintée
 *  d'encre (échantillonnée par le spot), et la pastille spot qui glisse avec
 *  des ressorts amortis — draggable d'un onglet à l'autre. Le décalage
 *  « remember(selectedTabIndex) » du catalogue est adapté ici : les clés
 *  portent sur des valeurs (taille, unité) pour survivre aux recompositions
 *  de la navigation.
 *
 *  Rappel de survie : cette barre est HORS de la capture de scène (AppNav) —
 *  c'est ce qui lui permet de lire le contenu qui défile derrière elle. */
@Composable
fun LiquidBottomTabs(
    selectedTabIndex: () -> Int,
    onTabSelected: (index: Int) -> Unit,
    backdrop: Backdrop,
    tabsCount: Int,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    // Contrat : selectedTabIndex doit lire un état snapshot (mutableIntState…)
    // — la synchronisation passe par snapshotFlow, qui ignore les lectures
    // hors snapshot.
    val colors = RegistreTheme.colors
    val accentColor = colors.ink
    // Un givre léger laisse lire la scène sans transformer les zones
    // claires en halo blanc.
    val containerColor = givre()

    val tabsBackdrop = rememberLayerBackdrop()

    BoxWithConstraints(
        modifier,
        contentAlignment = Alignment.CenterStart
    ) {
        val density = LocalDensity.current
        val tabWidth = with(density) {
            (constraints.maxWidth.toFloat() - 8f.dp.toPx()) / tabsCount
        }

        val offsetAnimation = remember { Animatable(0f) }
        val panelOffset by remember(density) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / constraints.maxWidth).fastCoerceIn(-1f, 1f)
                with(density) {
                    4f.dp.toPx() * fraction.sign * EaseOut.transform(abs(fraction))
                }
            }
        }

        val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
        val animationScope = rememberCoroutineScope()
        var currentIndex by remember { mutableIntStateOf(selectedTabIndex()) }
        val dampedDragAnimation = remember(tabsCount) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedTabIndex().toFloat(),
                valueRange = 0f..(tabsCount - 1).toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabsCount - 1)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(
                            0f,
                            spring(1f, 300f, 0.5f)
                        )
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidth * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, (tabsCount - 1).toFloat())
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            )
        }
        LaunchedEffect(Unit) {
            snapshotFlow { selectedTabIndex() }
                .collectLatest { index ->
                    currentIndex = index
                }
        }
        LaunchedEffect(dampedDragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dampedDragAnimation.animateToValue(index.toFloat())
                    onTabSelected(index)
                }
        }

        val interactiveHighlight = remember(animationScope) {
            InteractiveHighlight(
                animationScope = animationScope,
                position = { size, offset ->
                    Offset(
                        if (isLtr) (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 0.5f) * tabWidth + panelOffset,
                        size.height / 2f
                    )
                }
            )
        }

        Row(
            Modifier
                .graphicsLayer {
                    translationX = panelOffset
                }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { ControlShape },
                    // Sans vibrance, et lens modéré : sur une scène claire
                    // (aurore + cartes blanches), la vibrance et un lens fort
                    // font fleurir la barre — un halo brillant qui empeste
                    // la surexposition (vu en bêta 7).
                    effects = {
                        blur(8.dp.toPx())
                        lens(10.dp.toPx(), 16.dp.toPx())
                    },
                    highlight = { null },
                    layerBlock = {
                        val progress = dampedDragAnimation.pressProgress
                        val scale = lerp(1f, 1f + 16f.dp.toPx() / size.width, progress)
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) }
                )
                .then(interactiveHighlight.modifier)
                .height(64.dp)
                .fillMaxWidth()
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )

        CompositionLocalProvider(
            LocalLiquidBottomTabScale provides {
                lerp(1f, 1.2f, dampedDragAnimation.pressProgress)
            }
        ) {
            Row(
                Modifier
                    .clearAndSetSemantics {}
                    .alpha(0f)
                    .layerBackdrop(tabsBackdrop)
                    .graphicsLayer {
                        translationX = panelOffset
                    }
                    .drawBackdrop(
                        backdrop = backdrop,
                        shape = { ControlShape },
                        effects = {
                            val progress = dampedDragAnimation.pressProgress
                            blur(8.dp.toPx())
                            lens(
                                12.dp.toPx() * progress,
                                16.dp.toPx() * progress
                            )
                        },
                        highlight = { null },
                        onDrawSurface = { drawRect(containerColor) }
                    )
                    .then(interactiveHighlight.modifier)
                    .height(56.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp)
                    .graphicsLayer { colorFilter = ColorFilter.tint(accentColor) },
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }

        Box(
            Modifier
                .padding(horizontal = 4.dp)
                .graphicsLayer {
                    translationX =
                        if (isLtr) dampedDragAnimation.value * tabWidth + panelOffset
                        else size.width - (dampedDragAnimation.value + 1f) * tabWidth + panelOffset
                }
                .then(interactiveHighlight.gestureModifier)
                .then(dampedDragAnimation.modifier)
                .drawBackdrop(
                    backdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop),
                    shape = { ControlShape },
                    effects = {
                        val progress = dampedDragAnimation.pressProgress
                        lens(
                            10.dp.toPx() * progress,
                            14.dp.toPx() * progress,
                        )
                    },
                    highlight = { null },
                    shadow = { null },
                    innerShadow = { null },
                    layerBlock = {
                        scaleX = dampedDragAnimation.scaleX
                        scaleY = dampedDragAnimation.scaleY
                        val velocity = dampedDragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dampedDragAnimation.pressProgress
                        drawRect(Color.Black.copy(0.06f), alpha = 1f - progress)
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    }
                )
                .height(56.dp)
                .fillMaxWidth(1f / tabsCount)
        )
    }
}

/** Le verre réel est-il disponible ici ? (capture fournie + API 31+.) */
@Composable
fun verreRéelDisponible(): Boolean =
    LocalGlassBackdrop.current != null && floutageDisponible

/** Givre léger posé sur le verre : assez fin pour laisser la réfraction
 *  vivre, assez présent pour le texte. 25 % en clair, comme la recette du
 *  catalogue ; un peu plus en sombre. */
@Composable
fun givre(): Color =
    if (isSystemInDarkTheme()) {
        RegistreTheme.colors.page.copy(alpha = 0.4f)
    } else {
        Color.White.copy(alpha = 0.25f)
    }

/** Champ de recherche liquide : la pile de verre du LiquidButton (vibrance,
 *  flou, réfraction) sur une capsule champ, avec la déformation tanh du
 *  bouton à l'appui — aucun traitement de faveur : le même verre que les
 *  boutons, sans la vibrance (elle fait fleurir le verre sur une scène
 *  claire) et avec un lens réduit. Repli (sans capture ou sous l'API 31) :
 *  capsule quasi opaque. */
@Composable
fun ChampRecherche(
    valeur: String,
    onChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = RegistreTheme.colors
    val backdrop = LocalGlassBackdrop.current
    val teinteGivre = givre()
    if (backdrop == null || !floutageDisponible) {
        Row(
            modifier
                .clip(ControlShape)
                .background(colors.glass.barStrong)
                .border(1.dp, colors.glass.stroke, ControlShape)
                .height(44.dp)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = colors.chalk,
                modifier = Modifier.size(18.dp),
            )
            ChampTexte(
                valeur = valeur,
                onChange = onChange,
                placeholder = placeholder,
                colors = colors,
                modifier = Modifier.weight(1f),
            )
        }
        return
    }

    val animationScope = rememberCoroutineScope()
    val interactiveHighlight = remember(animationScope) {
        InteractiveHighlight(animationScope = animationScope)
    }
    Row(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { ControlShape },
                effects = {
                    blur(6.dp.toPx())
                    lens(8.dp.toPx(), 14.dp.toPx())
                },
                highlight = { null },
                layerBlock = {
                    val width = size.width
                    val height = size.height

                    val progress = interactiveHighlight.pressProgress
                    val scale = lerp(1f, 1f + 4f.dp.toPx() / size.height, progress)

                    val maxOffset = size.minDimension
                    val initialDerivative = 0.05f
                    val offset = interactiveHighlight.offset
                    translationX = maxOffset * tanh(initialDerivative * offset.x / maxOffset)
                    translationY = maxOffset * tanh(initialDerivative * offset.y / maxOffset)

                    val maxDragScale = 4f.dp.toPx() / size.height
                    val offsetAngle = atan2(offset.y, offset.x)
                    scaleX =
                        scale +
                            maxDragScale * abs(cos(offsetAngle) * offset.x / size.maxDimension) *
                            (width / height).fastCoerceAtMost(1f)
                    scaleY =
                        scale +
                            maxDragScale * abs(sin(offsetAngle) * offset.y / size.maxDimension) *
                            (height / width).fastCoerceAtMost(1f)
                },
                onDrawSurface = { drawRect(teinteGivre) },
            )
            .then(interactiveHighlight.modifier)
            .then(interactiveHighlight.gestureModifier)
            .height(44.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Search,
            contentDescription = null,
            tint = colors.chalk,
            modifier = Modifier.size(18.dp),
        )
        ChampTexte(
            valeur = valeur,
            onChange = onChange,
            placeholder = placeholder,
            colors = colors,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Contenu partagé des deux branches du champ : loupe posée par l'appelant,
 *  ici la saisie encre avec placeholder craie. */
@Composable
private fun ChampTexte(
    valeur: String,
    onChange: (String) -> Unit,
    placeholder: String,
    colors: GwsColors,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = valeur,
        onValueChange = onChange,
        modifier = modifier,
        singleLine = true,
        textStyle = TextStyle(
            color = colors.ink,
            fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
            fontSize = MaterialTheme.typography.bodyMedium.fontSize,
        ),
        cursorBrush = SolidColor(colors.ink),
        decorationBox = { champInterne ->
            Box {
                if (valeur.isEmpty()) {
                    Text(
                        text = placeholder,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.chalk,
                    )
                }
                champInterne()
            }
        },
    )
}

/** Barre basse flottante de l'app : la LiquidBottomTabs du catalogue habillée
 *  des onglets Greenwood (icône + libellé). Repli API < 31 : capsule quasi
 *  opaque, même silhouette, aucun échantillonnage.
 *
 *  L'index sélectionné passe par un état interne synchronisé : snapshotFlow
 *  ne suit que les lectures d'état snapshot — une lambda qui lirait une liste
 *  reconstruite à chaque recomposition ne serait jamais réévaluée, et un
 *  simple tap ne téléporterait jamais la pastille (vu en bêta 4). */
@Composable
fun GlassBottomBar(
    onglets: List<VerreOnglet>,
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
) {
    val glass = RegistreTheme.colors.glass
    val capsule = ControlShape

    // AppNav associe les routes de détail à leur onglet parent. La garde sur
    // -1 couvre seulement les transitions ou une future route non associée.
    val indexExterne = onglets.indexOfFirst { it.sélectionné }
    var indexÉtat by remember { mutableIntStateOf(indexExterne.coerceAtLeast(0)) }
    LaunchedEffect(indexExterne) {
        if (indexExterne >= 0 && indexÉtat != indexExterne) indexÉtat = indexExterne
    }

    if (!floutageDisponible || backdrop == null) {
        // Repli : même silhouette, fill quasi opaque, aucun échantillonnage.
        Row(
            modifier = modifier
                .clip(capsule)
                .background(glass.barStrong)
                .border(1.dp, glass.stroke, capsule)
                .padding(4.dp),
        ) {
            onglets.forEach { onglet -> ÉlémentOnglet(onglet, Modifier.weight(1f)) }
        }
        return
    }

    LiquidBottomTabs(
        selectedTabIndex = { indexÉtat },
        onTabSelected = { index -> onglets[index].onClick() },
        backdrop = backdrop,
        tabsCount = onglets.size,
        modifier = modifier,
    ) {
        onglets.forEach { onglet ->
            LiquidBottomTab(onClick = onglet.onClick) {
                Icon(
                    imageVector = onglet.icône,
                    contentDescription = onglet.libellé,
                    tint = if (onglet.sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                    modifier = Modifier.size(24.dp),
                )
                Text(
                    text = onglet.libellé,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (onglet.sélectionné) RegistreTheme.colors.ink else RegistreTheme.colors.chalk,
                )
            }
        }
    }
}

/** Un onglet de la barre de verre — l'icône, le libellé, l'état, le geste. */
@Immutable
data class VerreOnglet(
    val icône: ImageVector,
    val libellé: String,
    val sélectionné: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun ÉlémentOnglet(
    onglet: VerreOnglet,
    modifier: Modifier = Modifier,
) {
    val colors = RegistreTheme.colors
    val teinte = if (onglet.sélectionné) colors.ink else colors.chalk
    Box(
        modifier = modifier
            .height(56.dp)
            .clip(ControlShape)
            .then(
                if (onglet.sélectionné) {
                    Modifier.background(colors.sage.copy(alpha = 0.72f))
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = onglet.sélectionné,
                role = Role.Tab,
                onClick = onglet.onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                imageVector = onglet.icône,
                contentDescription = onglet.libellé,
                tint = teinte,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = onglet.libellé,
                style = MaterialTheme.typography.labelMedium,
                color = teinte,
            )
        }
    }
}
