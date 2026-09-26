package school.greenwood.plus.data.repo

import com.google.gson.stream.JsonReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals

class NouveautesStreamingTest {
    @Test
    fun extraitSeulementLesCorpsValidesDansUnFlux() {
        val réponse = """{"status":"ok","data":[
            {"id":"12","description":"<p>École ouverte</p>","image":{"large":"ignored"}},
            {"id":"13","description":null},
            {"id":"14","description":""}
        ],"extra":{"large":[1,2,3]}}"""

        val corps = NouveautesRepository.extraireCorps(JsonReader(StringReader(réponse)))

        assertEquals(mapOf("12" to "<p>École ouverte</p>"), corps)
    }
}
