# Web development

The Web experiment for issue #165 uses Compose Multiplatform/Wasm and the real [KMPLiquidGlass](https://github.com/Kashif-E/KMPLiquidGlass) Skia shader implementation. It does not yet port the Android app or connect school data.

- [Playground and reusable components](liquid-glass-playground/README.md)
- [Agent skill: GWS Web Liquid Glass](skills/gws-web-liquid-glass/SKILL.md)
- `kotlin-js-store/wasm/yarn.lock`: reproducible Web tooling dependencies.

From the repository root:

```sh
./gradlew :liquid-glass-playground:wasmJsBrowserDevelopmentRun
./gradlew :liquid-glass-playground:wasmJsBrowserDevelopmentWebpack
```

The development server uses port 8080. The module keeps its Gradle name while its files live under `web/`. LAN access also depends on host firewall rules; those machine-specific rules are not application code.

The wallpaper is user-supplied; its redistribution rights have not been independently established. No school credentials or production records are included.
