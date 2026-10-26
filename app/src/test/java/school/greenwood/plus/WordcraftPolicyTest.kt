package school.greenwood.plus

import org.junit.Assert.*
import org.junit.Test
import school.greenwood.plus.documents.WordcraftPolicy
import school.greenwood.plus.documents.WordcraftSandbox

class WordcraftPolicyTest {
    @Test fun supportedFormatsOnly() {
        assertEquals("docx", WordcraftPolicy.extension("Devoir.DOCX", null))
        assertEquals("doc", WordcraftPolicy.extension(null, "application/msword"))
        for (name in listOf("document.pdf", "tableur.xlsx", "présentation.pptx", "code.js")) {
            assertNull(WordcraftPolicy.extension(name, "text/plain"))
        }
    }
    @Test fun exactOfflineAllowlist() {
        assertTrue(WordcraftSandbox.allowed("https", WordcraftSandbox.host, "/document", "GET"))
        assertTrue(WordcraftSandbox.allowed("https", WordcraftSandbox.host, "/index.html", "GET"))
        assertFalse(WordcraftSandbox.allowed("https", "example.com", "/document", "GET"))
        assertFalse(WordcraftSandbox.allowed("file", WordcraftSandbox.host, "/document", "GET"))
        assertFalse(WordcraftSandbox.allowed("https", WordcraftSandbox.host, "/../private", "GET"))
        assertFalse(WordcraftSandbox.allowed("https", WordcraftSandbox.host, "/document", "POST"))
    }
}
