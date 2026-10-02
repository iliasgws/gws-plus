package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.model.ThemeMessage
import school.greenwood.plus.ui.screens.messages.refusCatégorieNouveau
import school.greenwood.plus.ui.screens.messages.refusCatégorieRéponse
import school.greenwood.plus.ui.screens.messages.themeDeRéponse

/*
 * Règles de catégorie sur les deux chemins d'envoi (issue #99) : le genre du
 * message ne part plus jamais en blanc par inadvertance, et rien n'est masqué
 * en silence quand le serveur ne répond pas.
 */
class CatégoriesMessageTest {

    private val scolarité = ThemeMessage(id = "8", label = "Scolarité")
    private val transport = ThemeMessage(id = "13", label = "Service Transport")
    private val themes = listOf(scolarité, transport)

    @Test
    fun `nouveau — catégorie obligatoire quand le serveur en propose`() {
        assertNull(refusCatégorieNouveau(themeChoisi = "8", themes = themes, themesEnÉchec = false))
        assertNotNull(refusCatégorieNouveau(themeChoisi = null, themes = themes, themesEnÉchec = false))
        assertEquals(
            "Choisis une catégorie",
            refusCatégorieNouveau(themeChoisi = "  ", themes = themes, themesEnÉchec = false),
        )
    }

    @Test
    fun `nouveau — serveur sans aucune catégorie — rien à choisir, l'envoi passe`() {
        assertNull(refusCatégorieNouveau(themeChoisi = null, themes = emptyList(), themesEnÉchec = false))
    }

    @Test
    fun `nouveau — catégories en échec sans cache — envoi bloqué jusqu'à Réessayer`() {
        assertEquals(
            "Catégories indisponibles — réessaie",
            refusCatégorieNouveau(themeChoisi = null, themes = emptyList(), themesEnÉchec = true),
        )
    }

    @Test
    fun `nouveau — catégories encore en chargement — rien ne part avant`() {
        assertEquals(
            "Chargement des catégories…",
            refusCatégorieNouveau(
                themeChoisi = null,
                themes = emptyList(),
                themesEnÉchec = false,
                chargement = true,
            ),
        )
    }

    @Test
    fun `réponse — fil nu pendant le chargement — l'envoi attend, un fil catégorisé passe`() {
        assertEquals(
            "Chargement des catégories…",
            refusCatégorieRéponse(
                choisi = null,
                fil = null,
                themes = emptyList(),
                themesEnÉchec = false,
                chargement = true,
            ),
        )
        assertNull(
            refusCatégorieRéponse(
                choisi = null,
                fil = "13",
                themes = emptyList(),
                themesEnÉchec = false,
                chargement = true,
            ),
        )
    }

    @Test
    fun `réponse — la catégorie choisie au composeur prime sur celle du fil`() {
        assertEquals("8", themeDeRéponse(choisi = "8", fil = "13"))
        assertEquals("8", themeDeRéponse(choisi = "8", fil = null))
        assertNull(refusCatégorieRéponse(choisi = "8", fil = null, themes = themes, themesEnÉchec = false))
    }

    @Test
    fun `réponse — à défaut la catégorie du fil part telle quelle`() {
        assertEquals("13", themeDeRéponse(choisi = null, fil = "13"))
        assertEquals("13", themeDeRéponse(choisi = "", fil = "13"))
        assertNull(refusCatégorieRéponse(choisi = null, fil = "13", themes = emptyList(), themesEnÉchec = false))
    }

    @Test
    fun `réponse — fil sans catégorie — une doit être choisie au composeur`() {
        assertEquals("", themeDeRéponse(choisi = null, fil = null))
        assertEquals(
            "Choisis une catégorie",
            refusCatégorieRéponse(choisi = null, fil = null, themes = themes, themesEnÉchec = false),
        )
        assertNull(refusCatégorieRéponse(choisi = "8", fil = "", themes = themes, themesEnÉchec = false))
    }

    @Test
    fun `réponse — catégories en échec et fil nu — envoi bloqué`() {
        assertEquals(
            "Catégories indisponibles — réessaie",
            refusCatégorieRéponse(choisi = null, fil = null, themes = emptyList(), themesEnÉchec = true),
        )
    }

    @Test
    fun `réponse — serveur sans aucun genre et fil nu — rien à imposer`() {
        assertNull(refusCatégorieRéponse(choisi = null, fil = null, themes = emptyList(), themesEnÉchec = false))
    }
}
