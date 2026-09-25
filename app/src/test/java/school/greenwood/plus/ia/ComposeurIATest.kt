package school.greenwood.plus.ia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.ai.ActionIA
import school.greenwood.plus.data.ai.ComposeurIA
import school.greenwood.plus.data.ai.PresetsFournisseurs
import school.greenwood.plus.data.ai.RéglagesIA
import school.greenwood.plus.data.ai.TonIA
import school.greenwood.plus.data.ai.promptSystème

/*
 * Le composeur IA (issue #56) : prompts (actions, tons, consigne libre) et
 * extraction du contenu OpenAI-compatible. Aucun appel réseau ici.
 */
class ComposeurIATest {

    @Test
    fun `le prompt système porte la consigne d'action et le ton`() {
        val prompt = promptSystème(ActionIA.RELIRE, TonIA.PROFESSIONNEL, null)
        assertTrue(prompt.contains(ActionIA.RELIRE.consigne))
        assertTrue(prompt.contains("professionnel"))
        assertTrue(prompt.contains("français"))
    }

    @Test
    fun `la consigne libre du parent est intégrée au prompt`() {
        val prompt = promptSystème(ActionIA.RÉÉCRIRE, TonIA.AMICAL, "mentionner le cartable oublié")
        assertTrue(prompt.contains("mentionner le cartable oublié"))
    }

    @Test
    fun `une consigne vide ou blanche n'ajoute rien`() {
        val sans = promptSystème(ActionIA.LISTE, TonIA.CONCIS, null)
        val vide = promptSystème(ActionIA.LISTE, TonIA.CONCIS, "   ")
        assertEquals(sans, vide)
        assertFalse(vide.contains("Consigne supplémentaire"))
    }

    @Test
    fun `chaque action et chaque ton porte un libellé français`() {
        ActionIA.entries.forEach { assertTrue(it.libellé.isNotBlank()) }
        TonIA.entries.forEach { assertTrue(it.libellé.isNotBlank()) }
    }

    @Test
    fun `les presets couvrent les fournisseurs prévus`() {
        val noms = PresetsFournisseurs.map { it.nom }
        assertTrue(
            noms.containsAll(
                listOf(
                    "OpenRouter", "Groq", "DeepSeek", "Mistral AI",
                    "Together AI", "Fireworks AI", "Cerebras",
                ),
            ),
        )
        PresetsFournisseurs.forEach {
            assertTrue(it.base.startsWith("https://"))
        }
    }

    @Test
    fun `prête exige activation base modèle et clé`() {
        assertFalse(RéglagesIA(actif = false, base = "https://x", modèle = "m", clé = "k").prête)
        assertFalse(RéglagesIA(actif = true, base = "", modèle = "m", clé = "k").prête)
        assertFalse(RéglagesIA(actif = true, base = "https://x", modèle = "", clé = "k").prête)
        assertFalse(RéglagesIA(actif = true, base = "https://x", modèle = "m", clé = "").prête)
        assertTrue(RéglagesIA(actif = true, base = "https://x", modèle = "m", clé = "k").prête)
    }

    @Test
    fun `extraireContenu lit choices message content du format OpenAI`() {
        val corps = """{"choices":[{"message":{"role":"assistant",
            |"content":"  Voici le texte corrigé.  "}}]}""".trimMargin()
        assertEquals("Voici le texte corrigé.", ComposeurIA.extraireContenu(corps))
    }

    @Test
    fun `extraireContenu renvoie null sur une réponse inattendue`() {
        assertNull(ComposeurIA.extraireContenu("""{"erreur": true}"""))
        assertNull(ComposeurIA.extraireContenu("pas du json"))
        assertNull(ComposeurIA.extraireContenu("""{"choices":[]}"""))
    }
}
