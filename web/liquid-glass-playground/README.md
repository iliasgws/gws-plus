# GWS+ Web Liquid Glass

Compose/Wasm component playground using KMPLiquidGlass 0.0.1-alpha02, the KMP port of Kyant Backdrop. The glass recipe and spring deformation derive from GWS+ `feature/liquid-glass`.

Run `./gradlew :liquid-glass-playground:wasmJsBrowserDevelopmentRun` and open port 8080.

Reusable API in `LiquidGlass.kt`:

- `Modifier.liquidGlass(backdrop, radius)` applies actual Skia vibrancy, blur and chromatic lens effects.
- `LiquidButton(backdrop, onClick, modifier, onDrag, content)` adds spring press/stretch motion. The caller owns drag position.
- `LiquidNavigationBar(backdrop, items, selectedIndex, onSelect, modifier)` renders controlled tabs with a draggable foreground lens. Clicks, keyboard selection and drag release share spring motion; lens compression and refraction respond to movement velocity. This demo changes selection only; production routes are not wired.

Record background content once with `rememberLayerBackdrop()` and `Modifier.layerBackdrop(backdrop)`, then pass that backdrop to the components. Keep capture and controls in the same coordinate hierarchy. Do not replace the shader with CSS transparency.

The bundled wallpaper was supplied by the user. Browser capabilities and the alpha library determine shader support; Android production is a separate module.

The navigation lens refracts only during its press/movement spring state. It expands beyond the selected tab, including while velocity compression is active; the continuous rear layer blurs and refracts beneath it. Handle gestures consume down, movement and up so underlying tab controls cannot activate from the same touch.

The rear navbar applies blur and chromatic refraction at full sampling opacity across its full width (4 dp blur; opacity must not be reduced, since that leaks the sharp wallpaper back through). The handle samples the wallpaper combined with this rear surface and labels; it has no opaque fill.
