# Registre banner: scroll, collapse, and pin

This document describes the banner prepared for **0.9.8**, including the
setting introduced in **0.9.8-beta.7**. The behavior is: **start fully visible, scroll upward
with the feed, then stop moving when only a short faded strip remains**.
The feed continues scrolling behind that strip.

## Source files

- `app/src/main/java/school/greenwood/plus/ui/screens/registre/BanniereRegistre.kt`:
  image, dimensions, gradients, and static Compose preview.
- `app/src/main/java/school/greenwood/plus/ui/screens/registre/RegistreScreen.kt`:
  list state, scroll offset, layering, insets, and status-icon appearance.
- `app/src/main/res/drawable-nodpi/registre_banner.png`: supplied school image.
- `app/src/main/java/school/greenwood/plus/MainActivity.kt`: existing
  `enableEdgeToEdge()` setup.
- `app/src/main/java/school/greenwood/plus/ui/AppNav.kt`: the shell's
  `Scaffold` supplies the screen padding.

## Settings toggle and persistence

Under **Paramètres → Apparence**, the **Bannière du Registre** switch
controls a device-wide app preference. It is enabled by default.

- `SessionStore.Clefs.banniereRegistre` uses the boolean DataStore key
  `banniere_registre_activee` in the existing `gws_session` store.
- `SessionStore.bannièreRegistreActivée` exposes a `Flow<Boolean>`;
  `définirBannièreRegistre()` saves changes with `dataStore.edit`.
- `ParametresViewModel` collects the preference into `ParamètresÉtat` and
  forwards switch changes to the store. The whole settings row is toggleable
  with `Role.Switch`; its visual `Switch` has no duplicate click handler.
- `RegistreScreen` observes the same flow with lifecycle-aware collection.
  Disabled means no banner is composed, no 140 dp banner gap is reserved,
  and status-icon appearance follows the plain page and current theme.
- Turning it back on restores the collapse-and-pin behavior. The list keeps
  its current scroll state, so a scrolled feed may return with the strip
  already collapsed.
- The choice survives app restarts and logout: `SessionStore.effacer()`
  removes authentication fields, not this app preference. It is local and
  is never sent to the school API.

## Dimensions and threshold

| Value | Size | Purpose |
| --- | --- | --- |
| `HauteurBannièreComplète` | 140 dp | Full image height below the status-bar inset |
| `HauteurBannièreRéduite` | 40 dp | Image strip retained below the status-bar inset after pinning |
| Collapse distance | 100 dp | Full height minus reduced height |
| Bottom fade | 64 dp | Blend the illustration into the theme's paper color |
| Initial feed spacing | 152 dp | Full height plus a 12 dp gap, inside the inset-safe feed viewport |

Let `S` be `padding.calculateTopPadding()`, supplied by the shell. The image
is measured at `140 dp + S`. Its size does **not** shrink during scrolling:
the whole image moves upward, revealing its lower portion rather than
rescaling or swapping to a differently cropped image.

The collapse distance is converted to physical pixels with `LocalDensity`
before it is compared with the list's pixel-based scroll offset.

## How the scroll offset works

`RegistreScreen` owns a `rememberLazyListState()` and passes that same state
to its `LazyColumn`. The banner's `Modifier.offset { ... }` reads it during
placement:

```kotlin
val collapsePx = with(LocalDensity.current) {
    (HauteurBannièreComplète - HauteurBannièreRéduite).roundToPx()
}

val displacement = if (listState.firstVisibleItemIndex == 0) {
    listState.firstVisibleItemScrollOffset.coerceAtMost(collapsePx)
} else {
    collapsePx
}

IntOffset(x = 0, y = -displacement)
```

The snippet uses English variable names for explanation; the source uses
`liste`, `seuilBannièrePx`, and `défilement`.

1. **At the top:** displacement is zero, so the full banner is visible.
2. **During the initial scroll:** the image moves up by the list offset.
3. **At 100 dp of displacement:** the offset is clamped. The visible image
   height is now `40 dp + S`.
4. **Farther down the feed:** the image stays pinned at that offset. The
   item-index check keeps it pinned even as later items become the first
   visible item and their per-item offsets reset.
5. **Returning to the top:** once the first item returns and its offset is
   below the threshold, the banner expands back into view with the feed.

There is no separate banner gesture handler, nested-scroll consumer, or
animation state machine. The list remains responsible for scrolling.

## Layering: content goes behind the banner

The drawer's content contains a full-screen `Box` with a paper background
and `clipToBounds()`. Inside it:

```text
System clock and status icons (Android window layer)
Banner image and gradients     (Compose zIndex = 1)
PullToRefreshBox + LazyColumn   (Compose default zIndex = 0)
Paper background               (outer Box)
```

The banner's `zIndex(1f)` makes it draw above the feed even though it is
declared before `PullToRefreshBox`. This is what makes cards and text pass
**behind** the pinned strip. The banner does not reserve a fixed layout
row that would stop the list below it.

The list has a transparent background. Its initial top `contentPadding`
is `140 dp + 12 dp`; this padding scrolls away with the feed. The surrounding
pull-to-refresh viewport separately applies the shell's top inset, so the
feed does not draw through the system clock area. Bottom padding still
uses the shell's bottom-bar padding plus the existing 16 dp gap.

`clipToBounds()` clips the translated image at the screen's top edge.
The drawer is outside this layered content, so opening it still draws the
drawer and its scrim above the banner.

## The bottom fade

The image uses `ContentScale.Crop` to fill its full-width measured bounds.
A bottom-aligned 64 dp `Box` overlays this gradient:

```kotlin
Brush.verticalGradient(
    listOf(Color.Transparent, RegistreTheme.colors.paper),
)
```

This is an overlay of the page color, **not** a global opacity applied to
the image or to the entire screen. At the bottom edge the overlay is opaque
paper, blending into the surrounding page. The gradient moves with the
banner, so it is retained after the offset clamps. In the pinned state,
much of the small remaining strip is already inside this fade.

Using `RegistreTheme.colors.paper` keeps the blend consistent in light and
dark themes. To soften or shorten the blend, change the fade height in
`BanniereRegistre.kt`; do not change the collapse threshold for that purpose.

## Status bar: edge-to-edge, not fullscreen

The activity already calls `enableEdgeToEdge()`. The banner starts at the
top of the screen without status-bar padding and includes `S` in its height,
so it initially draws behind the visible system status bar.

The banner also has a top gradient, from 50%-opaque black to transparent,
with height `S + 24 dp`. This gradient belongs to the image and moves with
it during collapse; it is not an independently pinned system-bar scrim.

`RegistreScreen` uses `WindowCompat.getInsetsController()` in a
`DisposableEffect` to request light-colored status icons over the image.
When the drawer is open on a light page it requests dark-colored icons.
The previous appearance is saved and restored when the effect is disposed,
including when the Registre is left.

The implementation does not hide system bars, set immersive/fullscreen
flags, or change navigation routes. The decorative image has
`contentDescription = null` and no tap or long-press action, so it does not
open the existing fullscreen image viewer.

## Loading, errors, and refresh

The banner sits outside the data-state branches, so it is also present
during the initial skeleton load or a first-load error. Those branches
reserve the full initial banner height for their content. They do not have
the loaded feed's scrolling behavior.

Warm content remains in the existing `LazyColumn`. Pull-to-refresh still
refreshes the Registre and silently checks for application updates through
the existing callbacks. The banner does not modify these requests, the
per-tab navigation stacks, or Android back handling.

## Verification

Build checks:

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleRelease
```

For manual visual verification, install the beta APK and check:

- Full banner on a fresh top-of-feed opening.
- Smooth initial upward movement, then pinning at the small faded strip.
- Cards continue behind the strip while the clock remains visible.
- Returning to the top restores the full banner.
- Pull-to-refresh, drawer, profile sheet, tab switches, and Android back
  retain their existing behavior.
- Light and dark themes, landscape, and a device with a display cutout.
- Disable the banner in Settings: no illustration or reserved gap remains.
  Re-enable it, restart the app, and verify the saved preference.

The static Compose preview covers the banner artwork and gradients only;
the collapse interaction requires the `RegistreScreen` list. Gradle checks
verify compilation and unit tests, not the on-device visual interaction.
Device verification in this work is manual, following the no-ADB instruction.
