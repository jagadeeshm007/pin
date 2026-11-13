package io.github.jagadeeshm007.pin.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.format.Formatter
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
import io.github.jagadeeshm007.pin.models.ModuleState
import io.github.jagadeeshm007.pin.speech.ListenState
import io.github.jagadeeshm007.pin.speech.SpeechEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher screen: service status and version. Replaced by first-launch setup in PIN-20. */
class MainActivity : AppCompatActivity() {
    private val micPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) (application as PinApp).speech.start()
        }

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* the notifier checks on post */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        collectDeviceProfile()
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        bindModels(binding)
        bindListening(binding)
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

    /** Voice models card (PIN-20): status, size, progress and a Download or Retry button. */
    private fun bindModels(binding: ActivityMainBinding) {
        val app = application as PinApp
        val models = app.models
        binding.modelsDownload.setOnClickListener {
            app.appScope.launch { models.downloadAll() }
        }
        lifecycleScope.launch { models.refresh() }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(models.states, models.progress) { states, progress ->
                    states to progress
                }.collect { (states, p) ->
                    val total = Formatter.formatShortFileSize(this@MainActivity, p.totalBytes)
                    val done = Formatter.formatShortFileSize(this@MainActivity, p.doneBytes)
                    val failed = states.values.filterIsInstance<ModuleState.Failed>().firstOrNull()
                    val downloading = states.values.any { it == ModuleState.Downloading }
                    binding.modelsStatus.text = when {
                        states.values.all { it == ModuleState.Ready } -> getString(R.string.models_ready, total)
                        downloading -> getString(R.string.models_downloading, done, total)
                        failed != null -> getString(R.string.models_failed, failed.reason)
                        else -> getString(R.string.models_missing, total)
                    }
                    binding.modelsProgress.visibility = if (downloading) View.VISIBLE else View.GONE
                    binding.modelsProgress.setProgressCompat((p.doneBytes * PROGRESS_MAX / p.totalBytes).toInt(), true)
                    binding.modelsDownload.visibility =
                        if (downloading || states.values.all { it == ModuleState.Ready }) View.GONE else View.VISIBLE
                    binding.modelsDownload.setText(
                        if (failed !=
                            null
                        ) {
                            R.string.models_retry
                        } else {
                            R.string.models_download
                        },
                    )
                    binding.modelsMetered.visibility = if (isMetered()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    /** Listening card (PIN-21): start and stop the session; disabled until models are ready. */
    private fun bindListening(binding: ActivityMainBinding) {
        val app = application as PinApp
        app.speech.connect()
        binding.listenToggle.setOnClickListener {
            val listening = app.speech.state.value.first.let { it != ListenState.STOPPED && it != ListenState.ERROR }
            when {
                listening -> app.speech.stop()

                checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED ->
                    app.speech.start()

                else -> micPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    app.speech.events.collect { e ->
                        if (e is SpeechEvent.Final) {
                            binding.listenHeard.text =
                                getString(R.string.listening_heard, e.text)
                        }
                    }
                }
                combine(app.speech.state, app.models.states) { s, m -> s to m }.collect { (s, models) ->
                    val (state, detail) = s
                    val ready = models.values.all { it == ModuleState.Ready }
                    binding.listenStatus.text = when {
                        !ready -> getString(R.string.listening_needs_models)
                        state == ListenState.LOADING -> getString(R.string.listening_state_loading)
                        state == ListenState.LISTENING -> getString(R.string.listening_state_listening)
                        state == ListenState.PAUSED -> getString(R.string.listening_state_paused, detail.orEmpty())
                        state == ListenState.ERROR -> getString(R.string.listening_state_error, detail.orEmpty())
                        else -> getString(R.string.listening_state_stopped)
                    }
                    val active = state != ListenState.STOPPED && state != ListenState.ERROR
                    binding.listenToggle.setText(if (active) R.string.listening_stop else R.string.listening_start)
                    binding.listenToggle.isEnabled = ready || active
                }
            }
        }
    }

    private fun isMetered(): Boolean = getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    /** First launch and every open: refresh what Pin knows about this phone (PIN-15). */
    private fun collectDeviceProfile() {
        val app = application as PinApp
        lifecycleScope.launch {
            val profile = withContext(Dispatchers.Default) { DeviceProfileCollector(this@MainActivity).collect() }
            app.profiles.saveProfile(profile)
        }
    }

    private companion object {
        const val PROGRESS_MAX = 1000L
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
