package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import school.greenwood.plus.ui.ongletPourRoute

class OngletRouteTest {
    @Test
    fun `les détails restent associés à leur onglet`() {
        assertEquals("documents", ongletPourRoute("quiz/{quizId}"))
        assertEquals("messages", ongletPourRoute("conversation/{conversationId}"))
        assertEquals("messages", ongletPourRoute("nouveau-message"))
        assertEquals("registre", ongletPourRoute("post/{postId}"))
        assertEquals("registre", ongletPourRoute("demandes"))
    }

    @Test
    fun `une route inconnue ne sélectionne pas`() {
        assertNull(ongletPourRoute(null))
        assertNull(ongletPourRoute("inconnue"))
    }
}
