---
name: gws-web-liquid-glass
description: Extend or tune the GWS Plus Compose/Wasm Liquid Glass playground, its reusable controls, shader composition, and touch spring interactions. Use for this Web glass implementation rather than Android production changes or generic CSS glass.
---

# GWS Web Liquid Glass

Work from the repository root. Read `web/liquid-glass-playground/README.md` and the relevant Kotlin source before changing a component. The Gradle module is `:liquid-glass-playground`; its directory is `web/liquid-glass-playground`.

## Implementation to reuse

- `LiquidGlass.kt`: real Backdrop lens recipe, anchored spring button, controlled draggable navigation bar.
- `GlassContrast.kt`: wallpaper crop-aware luminance sampling with hysteresis. Navbar labels use this; the button currently uses Difference blending. These are different implementations, so do not describe them as identical.
- `Main.kt`: shared captured wallpaper, demo state and layout. Wallpaper bytes are fetched as a bundled asset and decoded by Skia. Do not reintroduce the abandoned generated-resource path or duplicate the bitmap.

Use the pinned KMPLiquidGlass artifact (`io.github.kashif-mehmood-km:backdrop:0.0.1-alpha02`). It is a KMP port of Kyant Backdrop; Android GWS+ uses the upstream library. Preserve the real Skia runtime lens, not a CSS/transparency approximation. The current motion recipes derive from the repository's `feature/liquid-glass` implementation.

## Accepted interaction and compositing

- The button is anchored. Pulling deforms its lens with bounded elastic displacement, velocity compression and spring recoil; release returns it to its anchor. It remains clickable.
- The navigation lens is drawn above the labels and samples the wallpaper combined with the captured rear navbar and labels. Capturing only wallpaper loses text refraction; drawing text after the lens hides it.
- The rear navbar has continuous 4 dp blur and chromatic refraction, including under the handle. Keep its sampling opacity full: 30% layer alpha leaked the sharp wallpaper back through and looked unblurred. Tune blur radius separately from tint/opacity.
- The foreground handle has no opaque fill or extra blur. Its refraction is gated by the press/movement spring state and switches off when settled. Rear refraction remains active.
- During movement the handle grows beyond the selected tab, even when speed compresses it. The whole track expands while held and springs back. Click selection and drag release share the same transition and velocity-driven lens deformation.
- Keep transparent handle, rear frosting, adaptive labels and refraction as separately tunable concerns. Preserve the user's selected wallpaper and accepted appearance when making scoped changes.

## Pointer handling and validation

Consume down/move/up on the handle to prevent underlying tabs from activating from the same touch. Track the initiating pointer; use stable/root coordinates when the control itself moves. Preserve `touch-action: none` on the full-screen playground. New drags/selections must interrupt old transitions without snapping to stale targets.

Run `./gradlew :liquid-glass-playground:wasmJsBrowserDevelopmentWebpack` after source or build changes. A successful build proves compilation only; do not claim verified touch or optical behavior from it. When browser checking is requested, inspect the actual page, and verify click, drag, release, rapid retargets, rear blur and foreground refraction. Touch needs a real device or an explicitly configured touch input surface. Wasm updates may require a full reload.

Keep feature changes in sync with the root changelog, roadmap and AGENTS structure section. Follow the repository's feature-branch/PR rules; this skill does not authorize merging, releasing or publishing.
