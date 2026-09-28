package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.data.api.auteurIdDuJeton
import school.greenwood.plus.data.api.normaliserUrlServeur
import school.greenwood.plus.util.dateSaisieVersIso
import java.time.LocalDate

/*
 * Le client du serveur communautaire (issue #88) : URL normalisée à
 * l'écriture dans les Paramètres, empreinte d'auteur identique à celle du
 * serveur (`auteurId()` de gws-community-server, Main.kt) et dates de
 * formulaire converties vers l'ISO que le serveur stocke.
 */
class CommunApiTest {

    @Test
    fun `l'empreinte d'auteur vaut le SHA-256 hex sur 16 caractères du jeton`() {
        // Vecteur calculé avec le même algorithme que le serveur :
        // SHA-256("eagle-smell-bootlace-hypnoses-saddlebag-bunkhouse").
        assertEquals(
            "50d1967c942dc6d2",
            auteurIdDuJeton("eagle-smell-bootlace-hypnoses-saddlebag-bunkhouse"),
        )
        assertEquals(16, auteurIdDuJeton("n'importe quel jeton").length)
    }

    @Test
    fun `schéma manquant - https partout, http pour les hôtes locaux`() {
        assertEquals("https://communaute.ecole.fr", normaliserUrlServeur("communaute.ecole.fr"))
        assertEquals("http://localhost:8080", normaliserUrlServeur("localhost:8080"))
        assertEquals("http://10.0.2.2:8080", normaliserUrlServeur("10.0.2.2:8080"))
        assertEquals("http://127.0.0.1:9090", normaliserUrlServeur("127.0.0.1:9090"))
    }

    @Test
    fun `espaces et slashs de fin retirés`() {
        assertEquals("https://communaute.ecole.fr", normaliserUrlServeur("  https://communaute.ecole.fr///  "))
    }

    @Test
    fun `entrée vide ou illisible rendu vide - la section reste fermée`() {
        assertEquals("", normaliserUrlServeur("   "))
        assertEquals("", normaliserUrlServeur("http://"))
        assertEquals("", normaliserUrlServeur("http:///"))
    }

    @Test
    fun `date de formulaire acceptée en format jour-mois-année comme en ISO`() {
        assertEquals("2026-09-30", dateSaisieVersIso("30/09/2026"))
        assertEquals("2026-09-30", dateSaisieVersIso("2026-09-30"))
        assertEquals(LocalDate.of(2026, 1, 5).toString(), dateSaisieVersIso("le 05/01/2026 s'il te plaît"))
        assertNull(dateSaisieVersIso("un jour en septembre"))
        assertNull(dateSaisieVersIso("31/02/2026"))
    }
}
