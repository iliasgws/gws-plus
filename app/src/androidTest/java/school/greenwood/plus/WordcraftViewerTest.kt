package school.greenwood.plus

import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.content.FileProvider
import android.webkit.WebView
import android.view.View
import android.view.ViewGroup
import org.junit.Assert.*
import org.junit.Test
import school.greenwood.plus.documents.WordcraftIntents
import school.greenwood.plus.documents.WordcraftSettings
import school.greenwood.plus.documents.WordcraftActivity
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class WordcraftViewerTest {
    @Test fun defaultOffThenOpenLocalDocxOffline() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        WordcraftSettings.setEnabled(context, false)
        assertFalse(WordcraftSettings.enabled(context))
        val file = File(context.filesDir, "documents/wordcraft-test.docx").apply { parentFile!!.mkdirs() }
        ZipOutputStream(file.outputStream()).use { zip ->
            fun entry(name: String, body: String) {
                zip.putNextEntry(ZipEntry(name)); zip.write(body.toByteArray()); zip.closeEntry()
            }
            entry("[Content_Types].xml", """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/></Types>""")
            entry("_rels/.rels", """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/></Relationships>""")
            entry("word/document.xml", """<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main"><w:body><w:p><w:r><w:t>Document de test — élève</w:t></w:r></w:p></w:body></w:document>""")
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        val mime = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        try {
            assertNull(WordcraftIntents.preview(context, uri, mime, file.name))
            WordcraftSettings.setEnabled(context, true)
            val intent = WordcraftIntents.preview(context, uri, mime, file.name)!!
            ActivityScenario.launch<WordcraftActivity>(intent).use { scenario ->
                var title: String? = null
                fun web(view: View): WebView? {
                    if (view is WebView) return view
                    if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
                    return null
                }
                val deadline = System.currentTimeMillis() + 60000
                while (System.currentTimeMillis() < deadline) {
                    scenario.onActivity { title = web(it.window.decorView)?.title }
                    if (title == "gws:ready" || title == "gws:error") break
                    Thread.sleep(250)
                }
                assertEquals("Offline WebAssembly renderer should start and accept the local DOCX drop", "gws:ready", title)
            }
        } finally {
            WordcraftSettings.setEnabled(context, false)
            file.delete()
        }
    }
}
