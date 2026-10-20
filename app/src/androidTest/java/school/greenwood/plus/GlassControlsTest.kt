package school.greenwood.plus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import school.greenwood.plus.ui.Onglets
import school.greenwood.plus.ui.components.BarreOnglets
import school.greenwood.plus.ui.components.GlassAlertDialog
import school.greenwood.plus.ui.components.GlassButton
import school.greenwood.plus.ui.components.GlassPicker
import school.greenwood.plus.ui.components.GlassScene
import school.greenwood.plus.ui.components.GlassSwitch
import school.greenwood.plus.ui.components.GlassFloatingScaffold
import school.greenwood.plus.ui.components.GlassIconButton
import school.greenwood.plus.ui.components.VisualiseurImages
import school.greenwood.plus.ui.theme.GwsPlusTheme
import school.greenwood.plus.ui.theme.RegistreTheme
import org.junit.Assert.assertTrue

class GlassControlsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun disabledActionDoesNotRun() {
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                GlassButton(onClick = { calls++ }, enabled = false) { Text("Envoyer") }
            } }
        }
        compose.onNodeWithText("Envoyer").assertIsNotEnabled().performTouchInput { click() }
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun recomposedActionUsesCurrentCallback() {
        val callback = mutableStateOf<() -> Unit>({})
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                GlassButton(onClick = callback.value) { Text("Envoyer") }
            } }
        }
        compose.runOnIdle { callback.value = { calls++ } }
        compose.onNodeWithText("Envoyer").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun settingsSwitchHasOneAccessibleAction() {
        val checked = mutableStateOf(false)
        compose.setContent {
            GwsPlusTheme { GlassScene {
                Row(Modifier.toggleable(checked.value, role = Role.Switch, onValueChange = { checked.value = it })) {
                    Text("Bannière")
                    GlassSwitch(checked.value, onCheckedChange = null)
                }
            } }
        }
        compose.onAllNodes(isToggleable()).assertCountEquals(1)
        compose.onNodeWithText("Bannière").performClick().assertIsOn()
    }

    @Test fun backCollapsesPickerBeforeLeavingScreen() {
        compose.setContent {
            GwsPlusTheme { GlassScene {
                GlassPicker("Actualisation") { close ->
                    GlassButton(onClick = close) { Text("5 minutes") }
                }
            } }
        }
        compose.onNodeWithText("Actualisation").performClick()
        compose.onNodeWithText("5 minutes").assertExists()
        compose.runOnIdle { compose.activity.onBackPressedDispatcher.onBackPressed() }
        compose.onNodeWithText("5 minutes").assertDoesNotExist()
    }

    @Test fun externalTabSelectionDoesNotRequestNavigation() {
        val route = mutableStateOf("devoirs")
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                BarreOnglets(Onglets, route.value, RegistreTheme.colors.accents, onOnglet = { calls++ })
            } }
        }
        compose.runOnIdle { route.value = "documents" }
        compose.onNodeWithText("Documents").assertIsSelected()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun refusedTabRequestKeepsNavigationOwnedSelection() {
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                BarreOnglets(Onglets, "devoirs", RegistreTheme.colors.accents, onOnglet = { calls++ })
            } }
        }
        compose.onNodeWithText("Documents").performClick()
        compose.onNodeWithText("Devoirs").assertIsSelected()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun cancelledTabDragDoesNotNavigate() {
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                BarreOnglets(Onglets, "devoirs", RegistreTheme.colors.accents, onOnglet = { calls++ })
            } }
        }
        compose.onNodeWithText("Devoirs").performTouchInput {
            down(center)
            moveBy(Offset(80f, 0f))
            cancel()
        }
        compose.onNodeWithText("Devoirs").assertIsSelected()
        compose.runOnIdle { assertEquals(0, calls) }
    }

    @Test fun dialogActionRunsInItsOwnWindow() {
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                GlassAlertDialog(
                    onDismissRequest = {},
                    title = { Text("Quitter le quiz ?") },
                    text = { Text("La partie sera abandonnée.") },
                    confirmButton = { GlassButton(onClick = { calls++ }) { Text("Quitter") } },
                )
            } }
        }
        compose.onNodeWithText("Quitter").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun floatingHeaderStaysVisibleAndLastActionClearsBottomInset() {
        var calls = 0
        compose.setContent {
            GwsPlusTheme { GlassScene {
                GlassFloatingScaffold(
                    padding = PaddingValues(top = 24.dp, bottom = 80.dp),
                    header = {
                        GlassIconButton(onClick = { calls++ }, modifier = Modifier.testTag("floating-back")) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour", tint = RegistreTheme.colors.ink)
                        }
                    },
                ) { insets ->
                    Column(Modifier.fillMaxSize().testTag("scroll-content")
                        .verticalScroll(rememberScrollState()).padding(insets)) {
                        repeat(20) { Text("Élément $it", Modifier.fillMaxWidth().height(100.dp)
                            .background(if (it % 2 == 0) Color(0xFFF6DCA3) else Color(0xFFAED8CB))) }
                        GlassButton(onClick = {}, modifier = Modifier.testTag("last-action")) { Text("Terminer") }
                    }
                }
            } }
        }
        val before = compose.onNodeWithTag("floating-back").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("last-action").performScrollTo()
        compose.onNodeWithTag("scroll-content").performTouchInput { swipeUp() }
        compose.onNodeWithTag("floating-back").assertIsDisplayed()
        saveFixture(compose.onRoot(), "floating-header-scrolled")
        assertEquals(before, compose.onNodeWithTag("floating-back").fetchSemanticsNode().boundsInRoot)
        val last = compose.onNodeWithTag("last-action").fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNodeWithTag("scroll-content").fetchSemanticsNode().boundsInRoot
        val insetPx = with(compose.density) { 80.dp.toPx() }
        assertTrue(last.bottom <= viewport.bottom - insetPx + 1f)
        compose.onNodeWithTag("floating-back").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
    }

    @Test fun imageCloseButtonStaysAboveSwipingPages() {
        var calls = 0
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val images = listOf(R.drawable.cours_banner, R.drawable.registre_banner).map { resource ->
            File.createTempFile("glass-viewer-", ".png", context.cacheDir).also { file ->
                context.resources.openRawResource(resource).use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
            }
        }
        try {
        compose.setContent {
            GwsPlusTheme {
                // Bundled artwork exercises real images without network/personal data.
                VisualiseurImages(
                    images = images.map { android.net.Uri.fromFile(it).toString() },
                    indexInitial = 0, fermer = { calls++ },
                )
            }
        }
        val before = compose.onNodeWithContentDescription("Fermer").fetchSemanticsNode().boundsInRoot
        compose.onNode(isDialog()).performTouchInput { swipeLeft() }
        compose.onNodeWithContentDescription("Fermer").assertIsDisplayed()
        saveFixture(compose.onNode(isDialog()), "image-viewer-close")
        assertEquals(before, compose.onNodeWithContentDescription("Fermer").fetchSemanticsNode().boundsInRoot)
        compose.onNodeWithContentDescription("Fermer").performClick()
        compose.runOnIdle { assertEquals(1, calls) }
        } finally {
            images.forEach { it.delete() }
        }
    }

    @Test fun floatingComposerReservesItsHeightWithoutShrinkingScrollViewport() {
        compose.setContent {
            GwsPlusTheme { GlassScene {
                school.greenwood.plus.ui.components.MessagingGlass {
                    GlassFloatingScaffold(
                        padding = PaddingValues(top = 24.dp, bottom = 80.dp),
                        header = { Text("Messages", Modifier.height(48.dp)) },
                        footer = {
                            GlassButton(onClick = {}, modifier = Modifier.height(96.dp).testTag("composer")) {
                                Text("Envoyer")
                            }
                        },
                    ) { insets ->
                        Column(Modifier.fillMaxSize().testTag("message-list")
                            .verticalScroll(rememberScrollState()).padding(insets)) {
                            repeat(20) { Text("Message $it", Modifier.height(100.dp)) }
                            Text("Dernier message", Modifier.testTag("last-message"))
                        }
                    }
                }
            } }
        }
        val before = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithTag("last-message").performScrollTo()
        compose.onNodeWithTag("message-list").performTouchInput { swipeUp() }
        val composer = compose.onNodeWithTag("composer").fetchSemanticsNode().boundsInRoot
        val last = compose.onNodeWithTag("last-message").fetchSemanticsNode().boundsInRoot
        assertEquals(before, composer)
        assertTrue(last.bottom <= composer.top + 1f)
        compose.onNodeWithTag("composer").assertIsDisplayed()
        compose.onNodeWithTag("last-message").assertIsDisplayed()
    }

    private fun saveFixture(node: SemanticsNodeInteraction, name: String) {
        if (InstrumentationRegistry.getArguments().getString("captureGlassFixtures") != "true") return
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.externalCacheDir, "$name.png")
        file.outputStream().use { output ->
            node.captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
        }
    }
}
