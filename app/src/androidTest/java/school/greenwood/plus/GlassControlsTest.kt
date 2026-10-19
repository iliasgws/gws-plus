package school.greenwood.plus

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.isToggleable
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
import school.greenwood.plus.ui.theme.GwsPlusTheme
import school.greenwood.plus.ui.theme.RegistreTheme

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
}
