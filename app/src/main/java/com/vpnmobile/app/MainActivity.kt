package com.vpnmobile.app

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.vpnmobile.app.data.TunnelProfile
import com.vpnmobile.app.ui.MainViewModel
import com.vpnmobile.app.ui.VpnApp
import com.vpnmobile.app.ui.theme.VpnMobileTheme

class MainActivity : FragmentActivity() {

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

    private var authenticated = false
    private var biometricPromptShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (com.vpnmobile.app.vpn.SecurityCheck.isDebuggerAttached() || com.vpnmobile.app.vpn.SecurityCheck.isRooted()) {
            android.widget.Toast.makeText(this, "Security: Device not secure", android.widget.Toast.LENGTH_LONG).show()
        }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        com.vpnmobile.app.vpn.VpnNotification.ensureChannel(this)
        lifecycleScope.launch {
            viewModel.secureScreen.collect { enabled ->
                if (enabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }
        lifecycleScope.launch {
            if (viewModel.biometricLock.first()) requireBiometric()
        }
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accentColor by viewModel.accentColor.collectAsStateWithLifecycle()
            val darkTheme = when (themeMode) {
                "light" -> false
                "dark" -> true
                else -> isSystemInDarkTheme()
            }
            val accent = when (accentColor) {
                "green" -> Color(0xFF00C853)
                "blue" -> Color(0xFF2563EB)
                else -> Color(0xFFFF8C00)
            }
            VpnMobileTheme(darkTheme = darkTheme, accent = accent) {
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

    override fun onResume() {
        super.onResume()
        if (!authenticated && !biometricPromptShowing) {
            lifecycleScope.launch {
                if (viewModel.biometricLock.first()) requireBiometric()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        authenticated = false
    }

    private fun requireBiometric() {
        val canAuthenticate = BiometricManager.from(this)
            .canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        if (canAuthenticate != BiometricManager.BIOMETRIC_SUCCESS) {
            authenticated = true
            return
        }
        if (biometricPromptShowing) return
        biometricPromptShowing = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    authenticated = true
                    biometricPromptShowing = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    biometricPromptShowing = false
                    if (!authenticated) finishAffinity()
                }

                override fun onAuthenticationFailed() = Unit
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock VortexHub")
                .setSubtitle("Confirm your identity to open the app")
                .setNegativeButtonText("Cancel")
                .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                .build(),
        )
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
