package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.ui.components.glassDeformation

class GlassMotionTest {
    @Test fun idleIsUnchanged() {
        val motion = glassDeformation(0f, 0f, 100f, 48f, 0f, 1f)
        assertEquals(1f, motion.scaleX, 0f)
        assertEquals(1f, motion.scaleY, 0f)
        assertEquals(0f, motion.x, 0f)
    }

    @Test fun templateStretchesAlongDrag() {
        val motion = glassDeformation(80f, 0f, 100f, 48f, 1f, 1f)
        assertTrue(motion.scaleX > motion.scaleY)
        assertTrue(motion.x > 0f && motion.x < 48f)
        assertEquals(0f, motion.y, 0f)
    }

    @Test fun emptyLayoutIsSafe() {
        assertEquals(1f, glassDeformation(10f, 10f, 0f, 0f, 1f, 1f).scaleX, 0f)
    }
}
