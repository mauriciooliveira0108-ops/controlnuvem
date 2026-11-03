package com.flippermobile.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.flippermobile.core.di.AppContainer
import com.flippermobile.features.bluetooth.BluetoothScreen
import com.flippermobile.features.bluetooth.BluetoothViewModel
import com.flippermobile.features.device.DeviceInfoScreen
import com.flippermobile.features.device.DeviceInfoViewModel
import com.flippermobile.features.home.HomeScreen
import com.flippermobile.features.home.HomeViewModel
import com.flippermobile.features.infrared.InfraredScreen
import com.flippermobile.features.infrared.InfraredViewModel
import com.flippermobile.features.nfc.NfcScreen
import com.flippermobile.features.nfc.NfcViewModel
import com.flippermobile.features.saved.SavedDataScreen
import com.flippermobile.features.saved.SavedDataViewModel
import com.flippermobile.features.settings.SettingsScreen
import com.flippermobile.features.settings.SettingsViewModel
import com.flippermobile.features.terminal.TerminalScreen
import com.flippermobile.features.terminal.TerminalViewModel
import com.flippermobile.features.wifi.WifiScreen
import com.flippermobile.features.wifi.WifiViewModel

object Routes {
    const val HOME = "home"
    const val NFC = "nfc"
    const val BLUETOOTH = "bluetooth"
    const val WIFI = "wifi"
    const val INFRARED = "infrared"
    const val DEVICE = "device"
    const val TERMINAL = "terminal"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
}

@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM,
): VM = viewModel(factory = viewModelFactory { initializer { create(container) } })

@Composable
fun FlipperNavHost(container: AppContainer) {
    val nav = rememberNavController()
    val back: () -> Unit = { nav.popBackStack() }
    NavHost(
        navController = nav,
        startDestination = Routes.HOME,
        enterTransition = { slideInHorizontally { it / 4 } + fadeIn() },
        exitTransition = { fadeOut() },
        popEnterTransition = { fadeIn() },
        popExitTransition = { slideOutHorizontally { it / 4 } + fadeOut() },
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                viewModel = containerViewModel(container) { HomeViewModel(it) },
                onNavigate = { route -> nav.navigate(route) { launchSingleTop = true } },
            )
        }
        composable(Routes.NFC) { NfcScreen(containerViewModel(container) { NfcViewModel(it) }, back) }
        composable(Routes.BLUETOOTH) { BluetoothScreen(containerViewModel(container) { BluetoothViewModel(it) }, back) }
        composable(Routes.WIFI) { WifiScreen(containerViewModel(container) { WifiViewModel(it) }, back) }
        composable(Routes.INFRARED) { InfraredScreen(containerViewModel(container) { InfraredViewModel(it) }, back) }
        composable(Routes.DEVICE) { DeviceInfoScreen(containerViewModel(container) { DeviceInfoViewModel(it) }, back) }
        composable(Routes.TERMINAL) { TerminalScreen(containerViewModel(container) { TerminalViewModel(it) }, back) }
        composable(Routes.SAVED) { SavedDataScreen(containerViewModel(container) { SavedDataViewModel(it) }, back) }
        composable(Routes.SETTINGS) { SettingsScreen(containerViewModel(container) { SettingsViewModel(it) }, back) }
    }
}
