package school.greenwood.plus.documents

/** Exact offline resource allowlist: no arbitrary asset paths, filesystem or network. */
internal object WordcraftSandbox {
    const val host = "wordcraft.gws.invalid"
    const val origin = "https://$host"
    const val script = "wordcraft.js"
    const val wasm = "wordcraft_bg.wasm"
    val assets = mapOf(
        "/index.html" to ("wordcraft/index.html" to "text/html"),
        "/preview.js" to ("wordcraft/preview.js" to "text/javascript"),
        "/engine/$script" to ("wordcraft/engine/$script" to "text/javascript"),
        "/engine/$wasm" to ("wordcraft/engine/$wasm.gzip" to "application/wasm"),
    )
    fun allowed(scheme: String?, authority: String?, path: String?, method: String): Boolean =
        scheme == "https" && authority == host && method == "GET" &&
            (path in assets || path == "/document")

    const val csp = "default-src 'none'; script-src 'self' 'unsafe-eval'; " +
        "style-src 'unsafe-inline'; connect-src 'self'; img-src 'self' data: blob:; " +
        "font-src 'self' data:; worker-src 'none'; frame-src 'none'; object-src 'none'; " +
        "base-uri 'none'; form-action 'none'"
}
