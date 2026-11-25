package com.materialtube.downloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.materialtube.downloader.ui.MainViewModel
import com.materialtube.downloader.ui.screens.MainScreen
import com.materialtube.downloader.ui.theme.MaterialTubeTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _ -> }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNeededPermissions()
        handleIntent(intent)

        setContent {
            val settings by viewModel.settings.collectAsState()
            MaterialTubeTheme(
                themeMode = settings.themeMode,
                dynamicColor = settings.dynamicColor,
                seedPresetIndex = settings.seedPresetIndex,
            ) {
                MainScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            intent.getStringExtra(Intent.EXTRA_TEXT)?.let { sharedText ->
                viewModel.handleSharedUrl(sharedText)
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                if (ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }
        }
        if (needed.isNotEmpty()) {
            requestPermissionLauncher.launch(needed.toTypedArray())
        }
    }
}
