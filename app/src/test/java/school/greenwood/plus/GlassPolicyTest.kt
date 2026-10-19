package school.greenwood.plus

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import school.greenwood.plus.ui.components.glassPolicy

class GlassPolicyTest {
    @Test fun olderDevicesKeepLegibleOpaqueSurfaces() {
        for (api in 26..30) {
            val policy = glassPolicy(api)
            assertFalse(policy.blur)
            assertFalse(policy.lens)
            assertEquals(0.97f, policy.surfaceOpacity, 0f)
        }
    }

    @Test fun android12BlursWithoutRuntimeShaders() {
        for (api in 31..32) {
            val policy = glassPolicy(api)
            assertTrue(policy.blur)
            assertFalse(policy.lens)
            assertEquals(0.80f, policy.surfaceOpacity, 0f)
        }
    }

    @Test fun android13AndNewerCanLens() {
        for (api in 33..37) {
            val policy = glassPolicy(api)
            assertTrue(policy.blur)
            assertTrue(policy.lens)
            assertEquals(0.80f, policy.surfaceOpacity, 0f)
        }
    }
}
