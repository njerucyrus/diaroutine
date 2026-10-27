package com.diaroutine.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : ComponentActivity() {
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        diaRoutine.container.rescheduleSoon()
    }

    private val askForNotifications: () -> Unit = {
        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Permission requests need an activity; the shared app asks through the platform services.
        diaRoutine.platform.permissionRequester = askForNotifications
        setContent { App(diaRoutine.container) }
    }

    override fun onDestroy() {
        if (diaRoutine.platform.permissionRequester === askForNotifications) {
            diaRoutine.platform.permissionRequester = null
        }
        super.onDestroy()
    }
}
