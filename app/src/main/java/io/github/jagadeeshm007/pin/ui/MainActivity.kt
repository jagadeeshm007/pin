package io.github.jagadeeshm007.pin.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.accessibility.AccessibilityRegistry
import io.github.jagadeeshm007.pin.core.AppVersion
import io.github.jagadeeshm007.pin.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/** Launcher screen: service status and version. Replaced by first-launch setup in PIN-20. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
}
