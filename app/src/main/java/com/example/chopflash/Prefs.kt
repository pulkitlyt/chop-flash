package com.example.chopflash

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val FILE = "chopflash"
    const val KEY_ENABLED = "enabled"
    const val KEY_SENSITIVITY = "sensitivity"
    const val KEY_SCREEN_OFF = "screen_off"
    const val KEY_VIBRATE = "vibrate"

    fun get(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun enabled(p: SharedPreferences) = p.getBoolean(KEY_ENABLED, false)
    fun sensitivity(p: SharedPreferences) = p.getInt(KEY_SENSITIVITY, 50)
    fun screenOff(p: SharedPreferences) = p.getBoolean(KEY_SCREEN_OFF, true)
    fun vibrate(p: SharedPreferences) = p.getBoolean(KEY_VIBRATE, true)
}
