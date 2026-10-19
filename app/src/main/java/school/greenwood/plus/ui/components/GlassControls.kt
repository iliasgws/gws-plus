package school.greenwood.plus.ui.components

import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.kyant.backdrop.catalog.components.LiquidButton
import com.kyant.backdrop.catalog.components.LiquidToggle
import com.kyant.backdrop.catalog.utils.InteractiveHighlight
import com.kyant.capsule.ContinuousCapsule
import com.kyant.capsule.ContinuousRoundedRectangle
import school.greenwood.plus.ui.theme.RegistreTheme
import school.greenwood.plus.ui.theme.PageShape
import school.greenwood.plus.ui.theme.ControlShape
import school.greenwood.plus.ui.theme.AnnotationShape
import school.greenwood.plus.ui.theme.PiluleShape
import kotlin.math.abs
import kotlin.math.tanh

private fun continuousShape(shape: Shape): Shape = when (shape) {
    PageShape -> ContinuousRoundedRectangle(24.dp)
    ControlShape -> ContinuousRoundedRectangle(16.dp)
    AnnotationShape -> ContinuousRoundedRectangle(10.dp)
    PiluleShape -> ContinuousCapsule
    else -> shape
}

/** Match existing call-site contracts; only the surface rendering changes. */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = ContinuousRoundedRectangle(24.dp),
    color: Color = RegistreTheme.colors.page,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    floating: Boolean = false,
    content: @Composable () -> Unit,
): Unit = WithGlassScene {
    val backdrop = LocalGlassScene.current!!.backdrop
    val glassShape = continuousShape(shape)
    val policy = glassPolicy(Build.VERSION.SDK_INT)
    val tint = color.copy(alpha = color.alpha * if (floating) policy.chromeOpacity else policy.surfaceOpacity)
    androidx.compose.material3.Surface(
        modifier = modifier.glassChrome(backdrop, glassShape, tint),
        shape = glassShape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = border,
        shadowElevation = shadowElevation,
        content = content,
    )
}

@Composable
fun GlassSurface(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ContinuousRoundedRectangle(24.dp),
    color: Color = RegistreTheme.colors.page,
    contentColor: Color = contentColorFor(color),
    border: BorderStroke? = null,
    shadowElevation: Dp = 0.dp,
    floating: Boolean = false,
    content: @Composable () -> Unit,
): Unit = WithGlassScene {
    val glassShape = continuousShape(shape)
    val policy = glassPolicy(Build.VERSION.SDK_INT)
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.glassChrome(
            LocalGlassScene.current!!.backdrop, glassShape,
            color.copy(alpha = color.alpha * if (floating) policy.chromeOpacity else policy.surfaceOpacity),
        ),
        shape = glassShape,
        color = Color.Transparent,
        contentColor = contentColor,
        border = border,
        shadowElevation = shadowElevation,
        content = content,
    )
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ContinuousCapsule,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    blurRadius: Dp = GlassBlurDp.dp,
    content: @Composable RowScope.() -> Unit,
): Unit = WithGlassScene {
    CompositionLocalProvider(
        LocalContentColor provides if (enabled) colors.contentColor else colors.disabledContentColor,
    ) {
        ProvideTextStyle(MaterialTheme.typography.labelLarge) {
            LiquidButton(
                onClick = onClick,
                backdrop = LocalGlassScene.current!!.backdrop,
                modifier = modifier,
                enabled = enabled,
                shape = continuousShape(shape),
                // Keep filled-action contrast, including destructive actions and disabled states.
                surfaceColor = (if (enabled) colors.containerColor else colors.disabledContainerColor)
                    .let { it.copy(alpha = it.alpha * 0.94f) },
                contentPadding = contentPadding,
                blurRadius = blurRadius,
                content = content,
            )
        }
    }
}

@Composable
fun GlassTextButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    blurRadius: Dp = GlassBlurDp.dp,
    content: @Composable RowScope.() -> Unit,
): Unit = GlassButton(
    onClick, modifier, enabled,
    colors = ButtonDefaults.buttonColors(
        containerColor = RegistreTheme.colors.page.copy(alpha = glassPolicy(Build.VERSION.SDK_INT).chromeOpacity),
        contentColor = MaterialTheme.colorScheme.primary,
        disabledContainerColor = RegistreTheme.colors.page.copy(alpha = 0.35f),
        disabledContentColor = RegistreTheme.colors.chalk.copy(alpha = 0.5f),
    ),
    contentPadding = contentPadding,
    blurRadius = blurRadius,
    content = content,
)

@Composable
fun GlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = RegistreTheme.colors.page.copy(alpha = glassPolicy(Build.VERSION.SDK_INT).chromeOpacity),
    content: @Composable () -> Unit,
): Unit = WithGlassScene {
    androidx.compose.material3.IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.glassChrome(
            LocalGlassScene.current!!.backdrop, CircleShape,
            containerColor,
        ),
        content = content,
    )
}

/** Settings already expose one toggleable row: the visual switch must not steal taps or semantics. */
@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier): Unit =
    WithGlassScene {
        LiquidToggle(
            selected = { checked },
            onSelect = { onCheckedChange?.invoke(it) },
            backdrop = LocalGlassScene.current!!.backdrop,
            modifier = modifier.heightIn(min = 48.dp),
            interactive = onCheckedChange != null,
            accentColor = RegistreTheme.accent.teinte,
        )
    }

/** A fresh scene inside this Dialog's window, not a cross-window backdrop. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
    shape: Shape = ContinuousRoundedRectangle(28.dp),
    properties: DialogProperties = DialogProperties(),
) {
    BasicAlertDialog(onDismissRequest = onDismissRequest, modifier = modifier, properties = properties) {
        GlassScene {
            GlassSurface(shape = shape, contentColor = RegistreTheme.colors.ink) {
                Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    title?.let { ProvideTextStyle(MaterialTheme.typography.headlineSmall) { it() } }
                    // Text slots may already scroll (community forms). Keep finite constraints.
                    Column(Modifier.weight(1f, fill = false)) {
                        text?.let { ProvideTextStyle(MaterialTheme.typography.bodyMedium) { it() } }
                    }
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/** Inline expansion leaves Back to navigation only after the picker has collapsed. */
@Composable
fun GlassPicker(title: String, modifier: Modifier = Modifier, content: @Composable (() -> Unit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val expansion = remember { Animatable(0f) }
    LaunchedEffect(expanded) { expansion.animateTo(if (expanded) 1f else 0f, spring()) }
    val scope = rememberCoroutineScope()
    val highlight = remember(scope) { InteractiveHighlight(scope) }
    BackHandler(enabled = expanded) { expanded = false }
    val radius by animateFloatAsState(if (expanded) 24f else 32f, spring(), label = "glassPickerRadius")
    GlassSurface(
        modifier = modifier
            .semantics { stateDescription = if (expanded) "Développé" else "Réduit" }
            .graphicsLayer {
                val bound = 6.dp.toPx()
                translationX = bound * tanh(highlight.offset.x / (bound * 10f))
                translationY = bound * tanh(highlight.offset.y / (bound * 10f))
                scaleX = 1f + 0.008f * highlight.pressProgress
                scaleY = scaleX
                // Directional motion-blur approximation, cleared completely at rest.
                if (Build.VERSION.SDK_INT >= 31) {
                    val blur = (abs(expansion.velocity) * 0.12f).coerceAtMost(1.2f).dp.toPx()
                    renderEffect = if (blur > 0.05f) android.graphics.RenderEffect.createBlurEffect(
                        0.1f, blur, android.graphics.Shader.TileMode.CLAMP,
                    ).asComposeRenderEffect() else null
                }
            }
            .then(highlight.modifier)
            .then(highlight.gestureModifier),
        shape = ContinuousRoundedRectangle(radius.dp),
    ) {
        Column(Modifier.animateContentSize(spring())) {
            GlassTextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                Text(title)
            }
            AnimatedVisibility(expanded) {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    content { expanded = false }
                }
            }
        }
    }
}

/** Keep Material's focus, outside-tap dismissal and menu semantics in its popup window. */
@Composable
fun GlassDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        containerColor = Color.Transparent,
        shadowElevation = 0.dp,
    ) {
        GlassScene {
            GlassSurface { Column(content = content) }
        }
    }
}
