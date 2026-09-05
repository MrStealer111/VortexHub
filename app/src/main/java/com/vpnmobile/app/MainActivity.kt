package com.vpnmobile.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.VpnApp
import com.vpnmobile.app.ui.theme.VpnMobileTheme

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels {
        val factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as Application
                MainViewModel(app)
            }
        }
        factory
    }

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            viewModel.onVpnPermissionGranted()
        } else {
            viewModel.dismissPermissionPrompt()
        }
    }

    private val notifPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.vpnmobile.app.vpn.SecurityCheck.isDebuggerAttached() || com.vpnmobile.app.vpn.SecurityCheck.isRooted()) {
            android.widget.Toast.makeText(this, "Security: Device not secure", android.widget.Toast.LENGTH_LONG).show()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        com.vpnmobile.app.vpn.VpnNotification.ensureChannel(this)
        setContent {
            VpnMobileTheme {
                val needsPermission by viewModel.needsPermission.collectAsStateWithLifecycle()
                VpnApp(
                    viewModel = viewModel,
                    needsPermission = needsPermission,
                    onConnect = ::handleConnect,
                    onRequestVpnPermission = ::launchVpnPermission,
                )
            }
        }
    }

    private fun handleConnect(profile: TunnelProfile) {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            viewModel.connect(profile)
        } else {
            viewModel.prepareForPermission(profile)
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }

    private fun launchVpnPermission() {
        val prepareIntent = VpnService.prepare(this)
        if (prepareIntent == null) {
            viewModel.onVpnPermissionGranted()
        } else {
            vpnPermissionLauncher.launch(prepareIntent)
        }
    }
}
