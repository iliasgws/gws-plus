package school.greenwood.plus.documents

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import school.greenwood.plus.ui.theme.GwsPlusTheme

/** Private experimental viewer: bundled engine + a single bounded local document, no JS bridge. */
class WordcraftActivity : ComponentActivity() {
    private var localFile: File? = null
    private var webView: WebView? = null
    private var problem by mutableStateOf<String?>(null)
    private var prepared by mutableStateOf(false)
    private var loading by mutableStateOf(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val uri = intent.data
        val extension = intent.getStringExtra("extension")
        if (!WordcraftSettings.enabled(this) || uri?.scheme != "content" || extension !in WordcraftPolicy.extensions) {
            finish()
            return
        }
        lifecycleScope.launch {
            try {
                localFile = withContext(Dispatchers.IO) {
                    val file = File.createTempFile("wordcraft-", ".$extension", cacheDir)
                    try {
                        contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                while (true) {
                                    ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    total += n
                                    require(total <= WordcraftPolicy.maxBytes) { "size" }
                                    output.write(buffer, 0, n)
                                }
                                require(total > 0) { "empty" }
                            }
                        } ?: error("unreadable")
                        if (extension in setOf("docx", "odt")) {
                            // Reject archive bombs before passing bytes to the experimental parser.
                            ZipFile(file).use { zip ->
                                require(zip.size() <= 2048)
                                var bytes = 0L
                                for (entry in zip.entries()) {
                                    require(entry.size >= 0)
                                    bytes += entry.size
                                    require(bytes <= 50L * 1024 * 1024)
                                }
                            }
                        }
                        ensureActive()
                        file
                    } catch (e: Throwable) {
                        file.delete()
                        throw e
                    }
                }
                prepared = true
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                problem = "Impossible de préparer ce document (vide, endommagé ou supérieur à 10 Mo)."
                loading = false
            }
        }
        setContent {
            GwsPlusTheme {
                Surface {
                    Column(Modifier.fillMaxSize().systemBarsPadding()) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { finish() }) { Text("Fermer") }
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = ::openExternally) { Text("Ouvrir avec…") }
                        }
                        Text(intent.getStringExtra("title") ?: "Document", Modifier.padding(horizontal = 16.dp), maxLines = 2)
                        Text(
                            "WordCraft — bêta · Hors ligne · Modifications non enregistrées",
                            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                        problem?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
                        if (prepared) AndroidView(
                            factory = { createWebView(extension!!) },
                            modifier = Modifier.fillMaxWidth().weight(1f),
                        )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(extension: String): WebView = WebView(this).apply {
        webView = this
        settings.apply {
            javaScriptEnabled = true // Required by the bundled, allowlisted WASM engine only.
            allowFileAccess = false
            allowContentAccess = false
            domStorageEnabled = false
            databaseEnabled = false
            cacheMode = WebSettings.LOAD_NO_CACHE
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            mediaPlaybackRequiresUserGesture = true
            blockNetworkLoads = true
        }
        // No JavaScript interfaces, arbitrary file pickers, downloads or external browsing.
        setDownloadListener { _, _, _, _, _ -> }
        webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean = true
            override fun onPermissionRequest(request: PermissionRequest) { request.deny() }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                when (title) {
                    "gws:ready" -> loading = false
                    "gws:error" -> {
                        loading = false
                        problem = "Le lecteur expérimental n’a pas pu démarrer. Essaie « Ouvrir avec… »."
                    }
                }
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
                (view.parent as? android.view.ViewGroup)?.removeView(view)
                view.destroy()
                webView = null
                prepared = false
                loading = false
                problem = "Le lecteur a été interrompu. Essaie « Ouvrir avec… »."
                return true
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = true
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest): WebResourceResponse {
                val url = request.url
                if (!WordcraftSandbox.allowed(url.scheme, url.authority, url.path, request.method)) return denied()
                val headers = mapOf(
                    "Cache-Control" to "no-store", "Content-Security-Policy" to WordcraftSandbox.csp,
                    "X-Content-Type-Options" to "nosniff", "Referrer-Policy" to "no-referrer",
                )
                return try {
                    if (url.path == "/document") {
                        WebResourceResponse("application/octet-stream", null, 200, "OK", headers, localFile!!.inputStream())
                    } else {
                        val (asset, mime) = WordcraftSandbox.assets.getValue(url.path!!)
                        val input = assets.open(asset)
                        val body = if (asset.endsWith(".gzip")) java.util.zip.GZIPInputStream(input) else input
                        WebResourceResponse(mime, "utf-8", 200, "OK", headers, body)
                    }
                } catch (_: Exception) { denied() }
            }
        }
        loadUrl("${WordcraftSandbox.origin}/index.html?webgl&ext=$extension")
    }

    private fun denied() = WebResourceResponse(
        "text/plain", "utf-8", 403, "Forbidden", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)),
    )

    private fun openExternally() {
        try {
            startActivity(WordcraftIntents.external(intent.data!!, intent.type ?: "application/octet-stream"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Aucune application ne peut ouvrir ce document.", Toast.LENGTH_LONG).show()
        } catch (_: SecurityException) {
            Toast.makeText(this, "Impossible de partager ce document avec une autre application.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        webView?.apply { stopLoading(); loadUrl("about:blank"); destroy() }
        webView = null
        localFile?.delete()
        super.onDestroy()
    }
}
