package school.greenwood.plus

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.logic.GalerieImages

/*
 * La galerie d'images (issue #109) : la liste du visualiseur (couverture en
 * tête, aucun blanc, aucun doublon), les textes lus par TalkBack, puis la
 * géométrie du zoom — échelle bornée, image posée dans le cadre sans rognure,
 * décalage qui ne dépasse jamais les bords.
 */
class GalerieImagesTest {

    // ----- liste -----

    @Test
    fun `liste - couverture en tête, doublons et blancs écartés`() {
        val urls = GalerieImages.liste(
            couverture = "  https://ecole.be/a.jpg ",
            galerie = listOf(
                " ",
                "https://ecole.be/b.jpg",
                "https://ecole.be/a.jpg",
                "",
                "https://ecole.be/c.jpg",
                "   ",
            ),
        )
        assertEquals(
            listOf("https://ecole.be/a.jpg", "https://ecole.be/b.jpg", "https://ecole.be/c.jpg"),
            urls,
        )
    }

    @Test
    fun `liste - couverture blanche ou absente - la galerie garde son ordre`() {
        assertEquals(
            listOf("b", "c"),
            GalerieImages.liste(couverture = "   ", galerie = listOf(" b ", "c", "b")),
        )
        assertEquals(listOf("a"), GalerieImages.liste(couverture = null, galerie = listOf("a")))
        assertEquals(listOf("a"), GalerieImages.liste(couverture = "a", galerie = emptyList()))
    }

    @Test
    fun `liste - tout est blanc - rien ne part`() {
        assertTrue(GalerieImages.liste(couverture = null, galerie = emptyList()).isEmpty())
        assertEquals(
            emptyList<String>(),
            GalerieImages.liste(couverture = "  ", galerie = listOf("", "  ")),
        )
    }

    // ----- indexDépart -----

    @Test
    fun `indexDépart - vignette touchée trouvée`() {
        assertEquals(2, GalerieImages.indexDépart(listOf("a", "b", "c"), "c"))
        assertEquals(0, GalerieImages.indexDépart(listOf("a", "b"), "a"))
    }

    @Test
    fun `indexDépart - absente, nulle ou blanche - on part de la première`() {
        assertEquals(0, GalerieImages.indexDépart(listOf("a", "b"), "z"))
        assertEquals(0, GalerieImages.indexDépart(listOf("a"), null))
        assertEquals(0, GalerieImages.indexDépart(listOf("a"), "   "))
        assertEquals(0, GalerieImages.indexDépart(emptyList(), "a"))
    }

    // ----- libelléPosition -----

    @Test
    fun `libelléPosition - une seule image - rien à afficher`() {
        assertNull(GalerieImages.libelléPosition(0, 1))
        assertNull(GalerieImages.libelléPosition(1, 1))
        assertNull(GalerieImages.libelléPosition(0, 0))
    }

    @Test
    fun `libelléPosition - deuxième image sur cinq`() {
        assertEquals("2 / 5", GalerieImages.libelléPosition(1, 5))
        assertEquals("1 / 5", GalerieImages.libelléPosition(0, 5))
    }

    @Test
    fun `libelléPosition - index hors bornes - ramené dans la liste`() {
        assertEquals("1 / 5", GalerieImages.libelléPosition(-3, 5))
        assertEquals("5 / 5", GalerieImages.libelléPosition(42, 5))
    }

    // ----- descriptionImage -----

    @Test
    fun `descriptionImage - au singulier puis au pluriel`() {
        assertEquals("Image", GalerieImages.descriptionImage(0, 1))
        assertEquals("Image 2 sur 5", GalerieImages.descriptionImage(1, 5))
        assertEquals("Image 5 sur 5", GalerieImages.descriptionImage(99, 5))
    }

    @Test
    fun `descriptionImage - titre non blanc - ajouté derrière un tiret`() {
        assertEquals(
            "Image 1 sur 2 — Le tirage du vendredi",
            GalerieImages.descriptionImage(0, 2, titre = "Le tirage du vendredi"),
        )
        assertEquals(
            "Image 1 sur 2 — Le tirage du vendredi",
            GalerieImages.descriptionImage(0, 2, titre = "  Le tirage du vendredi  "),
        )
    }

    @Test
    fun `descriptionImage - titre blanc ou absent - rien de plus`() {
        assertEquals("Image", GalerieImages.descriptionImage(0, 1, titre = "   "))
        assertEquals("Image 1 sur 2", GalerieImages.descriptionImage(0, 2, titre = null))
    }

    // ----- zoomBorné -----

    @Test
    fun `zoomBorné - sous les bornes et au-dessus - écrasé à 1 et 8`() {
        assertEquals(1f, GalerieImages.zoomBorné(0.5f), 0f)
        assertEquals(1f, GalerieImages.zoomBorné(0f), 0f)
        assertEquals(8f, GalerieImages.zoomBorné(12f), 0f)
    }

    @Test
    fun `zoomBorné - dans les bornes - inchangé`() {
        assertEquals(1f, GalerieImages.zoomBorné(1f), 0f)
        assertEquals(3.5f, GalerieImages.zoomBorné(3.5f), 0f)
        assertEquals(8f, GalerieImages.zoomBorné(8f), 0f)
    }

    @Test
    fun `zoomBorné - valeur inutilisable - retour à 1`() {
        assertEquals(1f, GalerieImages.zoomBorné(Float.NaN), 0f)
        assertEquals(1f, GalerieImages.zoomBorné(Float.POSITIVE_INFINITY), 0f)
        assertEquals(1f, GalerieImages.zoomBorné(Float.NEGATIVE_INFINITY), 0f)
    }

    // ----- tailleAffichée -----

    @Test
    fun `tailleAffichée - portrait dans un cadre carré - à la hauteur`() {
        assertEquals(
            IntSize(200, 400),
            GalerieImages.tailleAffichée(IntSize(100, 200), IntSize(400, 400)),
        )
    }

    @Test
    fun `tailleAffichée - paysage dans un cadre portrait - à la largeur`() {
        assertEquals(
            IntSize(200, 100),
            GalerieImages.tailleAffichée(IntSize(400, 200), IntSize(200, 400)),
        )
    }

    @Test
    fun `tailleAffichée - dimension inutilisable - le cadre fait foi`() {
        assertEquals(
            IntSize(400, 300),
            GalerieImages.tailleAffichée(IntSize.Zero, IntSize(400, 300)),
        )
        assertEquals(
            IntSize.Zero,
            GalerieImages.tailleAffichée(IntSize(10, 10), IntSize.Zero),
        )
    }

    // ----- décalageBorné -----

    @Test
    fun `décalageBorné - déjà dans les bornes - inchangé`() {
        val décalage = GalerieImages.décalageBorné(
            décalage = Offset(100f, -200f),
            zoom = 2f,
            affiché = IntSize(1000, 1000),
            cadre = IntSize(500, 500),
        )
        assertEquals(Offset(100f, -200f), décalage)
    }

    @Test
    fun `décalageBorné - au-delà des bornes - coincé aux bords`() {
        val décalage = GalerieImages.décalageBorné(
            décalage = Offset(900f, -900f),
            zoom = 2f,
            affiché = IntSize(1000, 1000),
            cadre = IntSize(500, 500),
        )
        assertEquals(Offset(750f, -750f), décalage)
    }

    @Test
    fun `décalageBorné - retour à l'échelle 1 - remis à zéro`() {
        val décalage = GalerieImages.décalageBorné(
            décalage = Offset(120f, -80f),
            zoom = 1f,
            affiché = IntSize(400, 400),
            cadre = IntSize(500, 500),
        )
        assertEquals(Offset.Zero, décalage)
    }

    @Test
    fun `décalageBorné - cadre inutilisable - rien à déplacer`() {
        val décalage = GalerieImages.décalageBorné(
            décalage = Offset(50f, 50f),
            zoom = 2f,
            affiché = IntSize(1000, 1000),
            cadre = IntSize(0, 500),
        )
        assertEquals(Offset.Zero, décalage)
    }
}
