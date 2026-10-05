package school.greenwood.plus.ui.components

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import school.greenwood.plus.ui.theme.GwsPlusTheme
import school.greenwood.plus.ui.theme.RegistreTheme

/** AndroidX's expressive morphing loader, shared by inline and button loading states. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun GwsLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = RegistreTheme.colors.ink,
) {
    // Use the official Apache-2.0 AndroidX component directly. Transparent
    // containment keeps the same morph sequence readable on each existing surface.
    ContainedLoadingIndicator(
        modifier = modifier.size(40.dp),
        containerColor = Color.Transparent,
        indicatorColor = color,
    )
}

/** Native Material gestures, drag-driven morphing and expressive refresh animation. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
internal fun GwsPullToRefreshBox(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    indicatorTopInset: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier,
        state = state,
        indicator = {
            // Avoid composing an infinite animation while the indicator is hidden.
            if (isRefreshing || state.distanceFraction > 0f) {
                PullToRefreshDefaults.LoadingIndicator(
                    state = state,
                    isRefreshing = isRefreshing,
                    // Translate only the indicator: the list and drag threshold
                    // keep their native geometry, below any enabled artwork.
                    modifier = Modifier.align(Alignment.TopCenter).offset(y = indicatorTopInset),
                    containerColor = RegistreTheme.colors.page,
                    color = RegistreTheme.colors.ink,
                )
            }
        },
        content = content,
    )
}

@Preview(showBackground = true)
@Composable
private fun LoadingPreview() {
    GwsPlusTheme { GwsLoadingIndicator() }
}

@Preview(showBackground = true)
@Composable
private fun LoadingDarkPreview() {
    GwsPlusTheme(darkTheme = true) { GwsLoadingIndicator() }
}
