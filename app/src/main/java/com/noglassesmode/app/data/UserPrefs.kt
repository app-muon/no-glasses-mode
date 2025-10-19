package com.noglassesmode.app.data

import android.content.Context
import androidx.core.content.edit
import kotlin.math.round

class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("no_glasses_mode_prefs", Context.MODE_PRIVATE)

    var normalScale: Float
        get() = sp.getFloat(KEY_NORMAL, 1.00f)
        set(v) = sp.edit { putFloat(KEY_NORMAL, clamp(round2(v))) }

    var bigScale: Float
        get() = sp.getFloat(KEY_BIG, 1.30f)
        set(v) = sp.edit { putFloat(KEY_BIG, clamp(round2(v))) }

    fun validateAndSwapIfNeeded() {
        val n = normalScale
        val b = bigScale
        if (b < n + 0.10f) { // keep at least 0.10 difference
            bigScale = n + 0.10f
        }
    }

    companion object {
        private const val KEY_NORMAL = "normal_scale"
        private const val KEY_BIG = "big_scale"
        private const val MIN = 0.85f
        private const val MAX = 2.00f

        private fun clamp(v: Float) = v.coerceIn(MIN, MAX)
        private fun round2(v: Float) = (round(v * 100f) / 100f)
    }
}
