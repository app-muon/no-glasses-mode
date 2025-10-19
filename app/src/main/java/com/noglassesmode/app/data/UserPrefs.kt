package com.noglassesmode.app.data

import android.content.Context
import androidx.core.content.edit
import kotlin.math.round

class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("no_glasses_mode_prefs", Context.MODE_PRIVATE)

    var bigScale: Float
        get() = sp.getFloat(KEY_BIG, 1.30f)
        set(v) = sp.edit { putFloat(KEY_BIG, clamp(round2(v))) }

    var baselineScale: Float?
        get() = if (sp.contains(KEY_BASE)) sp.getFloat(KEY_BASE, 1.00f) else null
        set(v) {
            if (v == null) sp.edit { remove(KEY_BASE) } else sp.edit { putFloat(KEY_BASE, clamp(round2(v))) }
        }

    companion object {
        private const val KEY_BIG = "big_scale"
        private const val KEY_BASE = "baseline_scale"
        private const val MIN = 0.85f
        private const val MAX = 2.00f
        private fun clamp(v: Float) = v.coerceIn(MIN, MAX)
        private fun round2(v: Float) = (kotlin.math.round(v * 100f) / 100f)
    }
}
