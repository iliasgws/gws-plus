package school.greenwood.plus.data.session

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import school.greenwood.plus.model.Eleve

class SessionStoreTest {
    @Test
    fun laSélectionChangeLÉlèveEnvoyéÀLApi() = runBlocking {
        val dossier = Files.createTempDirectory("gws-session-test")
        val store = PreferenceDataStoreFactory.create(produceFile = {
            dossier.resolve("session.preferences_pb").toFile()
        })
        val session = SessionStore(store)
        session.enregistrer(
            keyToken = "test-key", userId = "parent", parentId = "parent",
            eleveId = "one", role = null, parent = null,
            eleves = listOf(Eleve("one", "Élève Un"), Eleve("two", "Élève Deux")),
            retenir = true,
        )

        session.sélectionnerÉlève(1)

        assertEquals("two", session.state.first()?.eleveId)
        assertEquals(1, session.eleveIndex.first())
    }
}
