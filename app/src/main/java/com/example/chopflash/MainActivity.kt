package com.example.chopflash

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

@SuppressLint("UseSwitchCompatOrMaterialCode")
class MainActivity : Activity() {

    private val prefs by lazy { Prefs.get(this) }

    private lateinit var enableSwitch: Switch
    private lateinit var batteryButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        enableSwitch = findViewById(R.id.switch_enable)
        val screenOffSwitch = findViewById<Switch>(R.id.switch_screen_off)
        val vibrateSwitch = findViewById<Switch>(R.id.switch_vibrate)
        val sensitivity = findViewById<SeekBar>(R.id.seek_sensitivity)
        val sensitivityLabel = findViewById<TextView>(R.id.text_sensitivity)
        batteryButton = findViewById(R.id.button_battery)

        screenOffSwitch.isChecked = Prefs.screenOff(prefs)
        screenOffSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_SCREEN_OFF, checked).apply()
        }

        vibrateSwitch.isChecked = Prefs.vibrate(prefs)
        vibrateSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_VIBRATE, checked).apply()
        }

        sensitivity.progress = Prefs.sensitivity(prefs)
        sensitivityLabel.text = getString(R.string.sensitivity_value, sensitivity.progress)
        sensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                sensitivityLabel.text = getString(R.string.sensitivity_value, progress)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                prefs.edit().putInt(Prefs.KEY_SENSITIVITY, seekBar.progress).apply()
            }
        })

        enableSwitch.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean(Prefs.KEY_ENABLED, checked).apply()
            if (checked) {
                requestNotificationPermissionIfNeeded()
                ChopService.start(this)
            } else {
                ChopService.stop(this)
            }
        }

        batteryButton.setOnClickListener {
            @SuppressLint("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Reflect the real state (e.g. user tapped "Stop" in the notification).
        val enabled = Prefs.enabled(prefs)
        if (enabled && !ChopService.running) ChopService.start(this)
        enableSwitch.isChecked = enabled

        val pm = getSystemService(PowerManager::class.java)
        batteryButton.visibility =
            if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 0)
        }
    }
}
