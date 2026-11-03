package com.flippermobile

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun homeShowsTitleAndOpensDeviceInfo() {
        rule.onNodeWithText("FLIPPER ZERO").assertIsDisplayed()
        rule.onNodeWithContentDescription("Device Information: Hardware e sistema").performClick()
        rule.onNodeWithText("DEVICE INFORMATION").assertIsDisplayed()
        rule.onNodeWithContentDescription("Voltar").performClick()
        rule.onNodeWithText("FLIPPER ZERO").assertIsDisplayed()
    }

    @Test
    fun terminalOpens() {
        rule.onNodeWithContentDescription("Activity Terminal: Logs em tempo real").performClick()
        rule.onNodeWithText("ACTIVITY TERMINAL").assertIsDisplayed()
    }
}
