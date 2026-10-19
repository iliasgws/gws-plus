package school.greenwood.plus.ui.components

/** Pure compatibility policy, independently testable without an Android runtime. */
internal data class GlassPolicy(val blur: Boolean, val lens: Boolean, val surfaceOpacity: Float)

internal fun glassPolicy(api: Int): GlassPolicy = GlassPolicy(
    blur = api >= 31,
    lens = api >= 33,
    surfaceOpacity = if (api >= 31) 0.80f else 0.97f,
)
