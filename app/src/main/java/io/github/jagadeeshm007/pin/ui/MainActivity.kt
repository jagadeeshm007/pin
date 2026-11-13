package io.github.jagadeeshm007.pin.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import io.github.jagadeeshm007.pin.R
import io.github.jagadeeshm007.pin.core.AppVersion
import io.github.jagadeeshm007.pin.databinding.ActivityMainBinding

/** Launcher screen. Replaced by the first-launch setup flow in PIN-20. */
class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val info = packageManager.getPackageInfo(packageName, 0)
        binding.version.text =
            getString(R.string.main_version, AppVersion.label(info.versionName, info.longVersionCode))
    }
}
