package com.noglassesmode.app.core

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

object FontScaleManager {
    private const val EPS = 0.05f

    fun canWriteSettings(ctx: Context): Boolean =
        Settings.System.canWrite(ctx)

    fun getCurrentScale(cr: ContentResolver): Float =
        try { Settings.System.getFloat(cr, Settings.System.FONT_SCALE) } catch (_: Throwable) { 1.0f }

    fun applyScale(cr: ContentResolver, value: Float): Boolean =
        try { Settings.System.putFloat(cr, Settings.System.FONT_SCALE, value); true } catch (_: Throwable) { false }

    fun approxEqual(a: Float, b: Float) = kotlin.math.abs(a - b) <= EPS

}
