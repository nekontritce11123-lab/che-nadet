package app.chenadet

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import app.chenadet.core.PermissionStatus
import app.chenadet.presentation.MainViewModel
import app.chenadet.ui.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels { (application as CheNadetApp).container.factory }
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        model.permissionResult(granted, shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            CheNadetTheme(state.recommendation?.thermalState) {
                HomeScreen(state, HomeActions(
                    refresh = { model.refresh() }, search = model::search, chooseCity = model::selectCity,
                    closeSearch = model::closeSearch, setProfile = model::setProfile,
                    useDevice = { model.useDeviceLocation(); if (!granted()) requestLocationPermission() },
                    requestPermission = ::requestLocationPermission,
                    openAppSettings = { startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) },
                    openLocationSettings = { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
                    dismissNotice = model::dismissNotice,
                ))
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) { model.tick(); delay(60_000) }
            }
        }
    }
    override fun onResume() {
        super.onResume()
        model.onForeground(granted(), shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    override fun onStop() { model.onBackground(); super.onStop() }
    private fun granted(): Boolean = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    private fun requestLocationPermission() {
        if (model.state.value.permission == PermissionStatus.PERMANENTLY_DENIED) {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        } else {
            model.permissionRequested()
            permissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }
}
