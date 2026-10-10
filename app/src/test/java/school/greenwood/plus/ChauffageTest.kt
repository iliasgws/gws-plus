package school.greenwood.plus

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.data.repo.Chauffé
import school.greenwood.plus.data.repo.chaufferSections
import school.greenwood.plus.data.repo.imagesÀPréparer
import school.greenwood.plus.data.repo.piecesÀTélécharger
import school.greenwood.plus.data.repo.voixÀPréparer
import school.greenwood.plus.model.Attachment
import school.greenwood.plus.model.Conversation
import school.greenwood.plus.model.Message
import school.greenwood.plus.model.Post

/*
 * Le chauffage des caches (prefetch) : toutes les sections partent ensemble,
 * une panne ne l'arrête pas, une annulation non plus — et les médias déjà
 * visibles dans les listes sont retenus pour le pré-téléchargement, bornés
 * pour ne jamais télécharger le serveur entier.
 */
class ChauffageTest {

    private fun post() = Post(
        id = "p1",
        title = "Sortie",
        image = "https://media/x/photo",
        attachments = listOf(Attachment(name = "consigne.pdf", url = "https://media/x/consigne")),
    )

    private fun conversation() = Conversation(
        id = "c1",
        sujet = "Cantine",
        messages = listOf(
            Message(
                id = "m1",
                deLAdmin = true,
                texte = "Menu",
                attachments = listOf(Attachment(name = "menu.pdf", url = "https://media/x/menu")),
                audio = Attachment(name = "vocal.m4a", url = "https://media/x/vocal"),
            ),
        ),
    )

    @Test
    fun `toutes les sources partent ensemble et le résultat porte posts et conversations`() {
        val appelés = mutableListOf<String>()
        val données = runBlocking {
            chaufferSections(
                semaine = { appelés += "cours" },
                devoirs = { appelés += "devoirs" },
                documents = { appelés += "documents" },
                bibliotheque = { appelés += "bibliotheque" },
                demandes = { appelés += "demandes" },
                conversations = { appelés += "messages"; listOf(conversation()) },
                posts = { appelés += "posts"; listOf(post()) },
            )
        }
        assertEquals(
            setOf("cours", "devoirs", "documents", "bibliotheque", "demandes", "messages", "posts"),
            appelés.toSet(),
        )
        assertEquals(listOf("p1"), données.posts.map { it.id })
        assertEquals(listOf("c1"), données.conversations.map { it.id })
    }

    @Test
    fun `une source en panne n'arrête pas le chauffage`() {
        val données = runBlocking {
            chaufferSections(
                semaine = { error("panne") },
                devoirs = { },
                documents = { },
                bibliotheque = { },
                demandes = { },
                conversations = { listOf(conversation()) },
                posts = { listOf(post()) },
            )
        }
        assertEquals(listOf("p1"), données.posts.map { it.id })
    }

    @Test
    fun `une annulation traverse le chauffage`() {
        val job = runBlocking {
            val lancement = launch(Dispatchers.Default) {
                chaufferSections(
                    semaine = { awaitCancellation() },
                    devoirs = { },
                    documents = { },
                    bibliotheque = { },
                    demandes = { },
                    conversations = { emptyList() },
                    posts = { emptyList() },
                )
            }
            delay(100)
            lancement.cancel()
            lancement.join()
            lancement
        }
        assertTrue("l'annulation du chauffage n'est pas convertie en succès", job.isCancelled)
    }

    @Test
    fun `les médias visibles sont retenus, bornés et sans doublon`() {
        val beaucoupDePosts = (1..40).map { n ->
            Post(id = "p$n", title = "Actu $n", image = "https://media/img$n")
        }
        val données = Chauffé(posts = beaucoupDePosts + post(), conversations = listOf(conversation()))

        assertEquals("12 couvertures maximum", 12, données.imagesÀPréparer().size)
        assertEquals(
            "pièce du message + pièce de l'actualité, sans doublon",
            listOf("https://media/x/menu" to "menu.pdf", "https://media/x/consigne" to "consigne.pdf"),
            données.piecesÀTélécharger(),
        )
        assertEquals(listOf("https://media/x/vocal"), données.voixÀPréparer())
    }
}
