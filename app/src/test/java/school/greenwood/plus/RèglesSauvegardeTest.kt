package school.greenwood.plus

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Garde-fou de configuration (issue #137) : les fichiers de règles de
 * sauvegarde et le manifeste doivent rester câblés comme attendu — une
 * régression silencieuse (attribut retiré, chemin oublié) remettrait les
 * jetons de session dans les transferts Android.
 *
 * Le Répertoire de travail des tests unitaires est le module `app/`.
 */
class RèglesSauvegardeTest {

    private fun res(nom: String): String =
        File("src/main/res/xml/$nom").readText(Charsets.UTF_8)

    private val manifeste: String =
        File("src/main/AndroidManifest.xml").readText(Charsets.UTF_8)

    @Test
    fun `le manifeste branche les deux formats de regles`() {
        assertTrue(
            "android:fullBackupContent manquant (API ≤ 30)",
            manifeste.contains("""android:fullBackupContent="@xml/backup_rules""""),
        )
        assertTrue(
            "android:dataExtractionRules manquant (API 31+)",
            manifeste.contains("""android:dataExtractionRules="@xml/data_extraction_rules""""),
        )
    }

    @Test
    fun `les regles legacy excluent la session les documents et le registre`() {
        val xml = res("backup_rules.xml")
        assertTrue(xml.contains("<full-backup-content>"))
        listOf(
            """<exclude domain="file" path="datastore/gws_session.preferences_pb" />""",
            """<exclude domain="file" path="documents/" />""",
            """<exclude domain="file" path="telechargements-publics.json" />""",
        ).forEach { règle ->
            assertTrue("exclusion manquante dans backup_rules.xml : $règle", xml.contains(règle))
        }
    }

    @Test
    fun `les regles API 31+ excluent session documents et registre des deux transferts`() {
        val xml = res("data_extraction_rules.xml")
        assertTrue(xml.contains("<data-extraction-rules>"))
        val cloud = xml.substringAfter("<cloud-backup>").substringBefore("</cloud-backup>")
        val transfert = xml.substringAfter("<device-transfer>").substringBefore("</device-transfer>")
        listOf(
            """<exclude domain="file" path="datastore/gws_session.preferences_pb" />""",
            """<exclude domain="file" path="documents/" />""",
            """<exclude domain="file" path="telechargements-publics.json" />""",
        ).forEach { règle ->
            assertTrue("exclusion cloud-backup manquante : $règle", cloud.contains(règle))
            assertTrue("exclusion device-transfer manquante : $règle", transfert.contains(règle))
        }
    }
}
