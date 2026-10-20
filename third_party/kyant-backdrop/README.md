# Kyant Backdrop catalog adaptations

Source: <https://github.com/Kyant0/AndroidLiquidGlass>, tag `1.0.0`, commit
`eada9a8600142c1bd6a5c5fc143d06f599cdfaef`. Copyright Kyant, Apache-2.0;
the upstream license is reproduced in [`LICENSE`](LICENSE).

Vendored from `catalog/src/main/java/com/kyant/backdrop/catalog/` into the same
package below `app/src/main/java/`:

- `components/LiquidButton.kt`
- `components/LiquidBottomTab.kt`
- `components/LiquidBottomTabs.kt`
- `components/LiquidToggle.kt`
- `utils/DampedDragAnimation.kt`
- `utils/InteractiveHighlight.kt`
- `utils/DragGestureInspector.kt`

Modified files retain their upstream names. Local adaptations:

- `LiquidButton`: enabled input handling; customizable shape, padding and blur;
  minimum 48 dp rather than a fixed height, so large text can grow.
  Post-beta.3 restores the original catalog deformation via shared `GlassMotion`,
  applied outside the backdrop so the foreground stretches with the glass.
  Beta.2 reduces default blur to 0.75 dp, increases lens strength and enables
  chromatic edge dispersion, also on the tab shell/selection lens at rest.
- `LiquidBottomTabs`: theme tint parameters; latest callbacks; geometry-aware
  remember keys; selection changes from navigation animate without issuing a
  second navigation request. A drag requests navigation only on release and
  does not locally commit selection (the quiz confirmation can reject it).
- `LiquidToggle`: theme accent; latest callbacks and values; layout-aware keys;
  decorative mode for the existing accessible settings row; no duplicate thumb
  semantics. Cancellation restores external state without writing a setting.
- `DampedDragAnimation`: instance-keyed pointer handling and a separate
  cancellation callback; cancelling a gesture must not commit navigation or a
  setting.
- `LiquidBottomTab`, `InteractiveHighlight`, `DragGestureInspector`: unchanged.

The rendering implementation is consumed from Maven Central, not vendored:
`io.github.kyant0:backdrop:1.0.0` and `io.github.kyant0:capsule:2.1.1`.
No Buds-specific protocol, assets, build tasks or distribution scripts are copied.
