package io.github.jagadeeshm007.pin.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jagadeeshm007.pin.PinApp
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.accessibility.AccessibilityRegistry
import io.github.jagadeeshm007.pin.core.AppVersion
import io.github.jagadeeshm007.pin.databinding.ActivityMainBinding
import io.github.jagadeeshm007.pin.device.DeviceProfileCollector
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher screen: service status and version. Replaced by first-launch setup in PIN-20. */
class MainActivity : AppCompatActivity() {
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the notifier checks on post */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        collectDeviceProfile()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val info = packageManager.getPackageInfo(packageName, 0)
        binding.version.text =
            getString(R.string.main_version, AppVersion.label(info.versionName, info.longVersionCode))
        binding.openSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                AccessibilityRegistry.service.collect { service ->
                    val on = service != null
                    binding.serviceStatus.setText(if (on) R.string.main_service_on else R.string.main_service_off)
                    binding.openSettings.visibility = if (on) View.GONE else View.VISIBLE
                }
            }
        }
    }

    /** First launch and every open: refresh what Pin knows about this phone (PIN-15). */
    private fun collectDeviceProfile() {
        val app = application as PinApp
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.Default) { DeviceProfileCollector(this@MainActivity).collect() }
            app.profiles.saveProfile(profile)
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
