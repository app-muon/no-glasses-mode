package com.noglassesmode.app.data

import android.content.Context
import androidx.core.content.edit

class UserPrefs(context: Context) {
    private val sp = context.getSharedPreferences("no_glasses_mode_prefs", Context.MODE_PRIVATE)

    var bigPercent: Float
        get() = sp.getFloat(KEY_BIG_PCT, 30f)
        set(v) = sp.edit { putFloat(KEY_BIG_PCT, v.coerceIn(0f, 200f)) }

    /** Snapshot of baseline when we switched to Big (so we can return). */
    var baselineScale: Float?
        get() = if (sp.contains(KEY_BASE)) sp.getFloat(KEY_BASE, 1.00f) else null
        set(v) { if (v == null) sp.edit { remove(KEY_BASE) } else sp.edit { putFloat(KEY_BASE, v) } }

    /** The actual Big value that ended up applied (post-write). */
    var bigAppliedScale: Float?
        get() = if (sp.contains(KEY_BIG_APPLIED)) sp.getFloat(KEY_BIG_APPLIED, 0f) else null
        set(v) { if (v == null) sp.edit { remove(KEY_BIG_APPLIED) } else sp.edit { putFloat(KEY_BIG_APPLIED, v) } }

    companion object {
        private const val KEY_BIG_PCT = "big_percent"
        private const val KEY_BASE = "baseline_scale"
        private const val KEY_BIG_APPLIED = "big_applied_scale"
    }
}
