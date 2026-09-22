package school.greenwood.plus

import school.greenwood.plus.data.repo.UpdatesRepository
import school.greenwood.plus.data.repo.VersionGws
import school.greenwood.plus.data.repo.prendreRésumé
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/*
 * Mises à jour (issue #46) : comparaison de versions, choix de la
 * publication selon le canal (stable par défaut, bêtas opt-in), lecture du
 * JSON de l'API GitHub Releases et sélection de l'APK. Jeux d'essai
 * synthétiques, sans lien réel.
 */
class MiseAJourTest {

    @Test
    fun `parser les versions - stable, bêta, tag v`() {
        assertEquals(VersionGws(0, 7, 1, false, 0), VersionGws.parser("0.7.1"))
        assertEquals(VersionGws(0, 7, 1, false, 0), VersionGws.parser("v0.7.1"))
        assertEquals(VersionGws(0, 7, 1, true, 1), VersionGws.parser("0.7.1-beta.1"))
        assertEquals(VersionGws(1, 2, 3, true, 12), VersionGws.parser("v1.2.3-beta.12"))
        assertNull(VersionGws.parser(null))
        assertNull(VersionGws.parser("pas une version"))
    }

    @Test
    fun `ordre des versions - bêta avant sa stable, majeurs dominants`() {
        val stable = VersionGws.parser("0.7.1")!!
        val bêta1 = VersionGws.parser("0.7.1-beta.1")!!
        val bêta2 = VersionGws.parser("0.7.1-beta.2")!!
        val suivante = VersionGws.parser("0.8.0-beta.1")!!

        assertTrue(bêta1 < stable)
        assertTrue(bêta1 < bêta2)
        assertTrue(stable < suivante)
        assertEquals(0, stable.compareTo(VersionGws.parser("v0.7.1")!!))
    }

    private fun publication(
        tag: String,
        bêta: Boolean,
        assets: String = """[{"name": "GWS-$tag.apk", "browser_download_url": "https://exemple.test/GWS-$tag.apk"}]""",
    ) = """
        {
          "tag_name": "$tag",
          "prerelease": $bêta,
          "html_url": "https://exemple.test/releases/tag/$tag",
          "body": "Notes de la version $tag.",
          "assets": $assets
        }
    """.trimIndent()

    @Test
    fun `lecture du corps GitHub - stable et bêta, APK isolé`() {
        val corps = "[\n" + listOf(
            publication("v0.8.0-beta.1", true),
            publication(
                "v0.7.1",
                false,
                assets = """[
                   {"name": "GWS-v0.7.1.apk.idsig", "browser_download_url": "https://exemple.test/sig"},
                   {"name": "GWS-v0.7.1.apk", "browser_download_url": "https://exemple.test/GWS-v0.7.1.apk"},
                   {"name": "source.zip", "browser_download_url": "https://exemple.test/src"}
                 ]""",
            ),
            publication("v0.7.0", false),
        ).joinToString(",\n") + "\n]"

        val publications = UpdatesRepository.publicationsDuCorps(corps)
        assertEquals(3, publications.size)
        assertEquals("0.8.0", publications[0].version.toStringCorrectif())
        assertTrue(publications[0].estBêta)

        val stable = publications[1]
        assertFalse(stable.estBêta)
        assertEquals("https://exemple.test/GWS-v0.7.1.apk", stable.apkUrl)
        assertEquals("GWS-v0.7.1.apk", stable.nomApk)
        assertEquals("https://exemple.test/releases/tag/v0.7.1", stable.page)
    }

    @Test
    fun `choix selon le canal - stable seule par défaut, bêtas opt-in`() {
        val corps = "[\n" + listOf(
            publication("v0.8.0-beta.1", true),
            publication("v0.7.1", false),
        ).joinToString(",\n") + "\n]"
        val publications = UpdatesRepository.publicationsDuCorps(corps)

        // Canal stable (défaut) : la bêta est ignorée, la stable proposée.
        assertEquals("0.7.1", UpdatesRepository.choisirPublication(publications, canalBêta = false)?.version?.toStringCorrectif())
        // Canal bêta (opt-in) : la version la plus haute, bêta comprise.
        assertTrue(UpdatesRepository.choisirPublication(publications, canalBêta = true)?.estBêta == true)
    }

    @Test
    fun `choix indépendant de l'ordre de la liste`() {
        // GitHub trie par date de création : une stable peut arriver APRÈS sa
        // propre bêta dans la liste — la version la plus haute gagne.
        val corps = "[\n" + listOf(
            publication("v0.7.1-beta.1", true),
            publication("v0.7.1", false),
            publication("v0.7.0", false),
        ).joinToString(",\n") + "\n]"
        val publications = UpdatesRepository.publicationsDuCorps(corps)

        assertEquals("0.7.1", UpdatesRepository.choisirPublication(publications, canalBêta = false)?.version?.toStringCorrectif())
        assertEquals("0.7.1", UpdatesRepository.choisirPublication(publications, canalBêta = true)?.version?.toStringCorrectif())
        assertFalse(UpdatesRepository.choisirPublication(publications, canalBêta = true)!!.estBêta)
    }

    @Test
    fun `publication sans APK lisible - aucun lien de téléchargement`() {
        val corps = "[\n" + publication("v0.9.0", false, assets = "[]") + "\n]"
        val publications = UpdatesRepository.publicationsDuCorps(corps)
        assertEquals(1, publications.size)
        assertNull(publications[0].apkUrl)
    }

    @Test
    fun `résumé des notes - titres et empreintes coupés, texte tenu`() {
        val notes = """
            ## Ajouté

            - **Bibliothèque des enseignants** : les documents des professeurs
              apparaissent enfin dans l'onglet Documents.

            s'installe au-dessus de la 0.7.0 sans désinstallation.

            SHA-256 : `abc123…`
        """.trimIndent()
        val résumé = notes.prendreRésumé()
        assertFalse(résumé.contains("Ajouté"))
        assertFalse(résumé.contains("SHA-256"))
        assertTrue(résumé.contains("Bibliothèque"))
        assertTrue(résumé.length <= 220)
    }

    private fun VersionGws.toStringCorrectif(): String = "$majeur.$mineur.$correctif"
}
