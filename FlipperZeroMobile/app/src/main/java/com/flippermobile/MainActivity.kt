package com.flippermobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flippermobile.ui.navigation.FlipperNavHost
import com.flippermobile.ui.theme.FlipperTheme

class MainActivity : ComponentActivity() {

    private val container get() = (application as FlipperApp).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by container.settings.settings.collectAsStateWithLifecycle(
                initialValue = com.flippermobile.data.prefs.AppSettings()
            )
            FlipperTheme(scanlines = settings.scanlinesEnabled) {
                FlipperNavHost(container = container)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        container.nfcController.onActivityResumed(this)
        container.statusMonitor.refresh()
    }

    override fun onPause() {
        container.nfcController.onActivityPaused(this)
        super.onPause()
    }
}
