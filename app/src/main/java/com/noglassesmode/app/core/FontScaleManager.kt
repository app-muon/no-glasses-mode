package com.noglassesmode.app.core

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

object FontScaleManager {
    private const val EPS = 0.02f

    fun canWriteSettings(ctx: Context): Boolean =
        Settings.System.canWrite(ctx)

    fun openManageWriteSettings(ctx: Context) {
        val i = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
        i.data = "package:${ctx.packageName}".toUri()
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
    }

    fun getCurrentScale(cr: ContentResolver): Float =
        try { Settings.System.getFloat(cr, Settings.System.FONT_SCALE) } catch (_: Throwable) { 1.0f }

    fun applyScale(cr: ContentResolver, value: Float): Boolean =
        try { Settings.System.putFloat(cr, Settings.System.FONT_SCALE, value); true } catch (_: Throwable) { false }

    fun approxEqual(a: Float, b: Float) = kotlin.math.abs(a - b) <= EPS

    fun pickTarget(current: Float, normal: Float, big: Float): Float {
        return when {
            approxEqual(current, normal) -> big
            approxEqual(current, big)    -> normal
            else -> {
                // If user changed scale elsewhere, jump to the nearer preset
                val dN = kotlin.math.abs(current - normal)
                val dB = kotlin.math.abs(current - big)
                if (dN <= dB) big else normal
            }
        }
    }
}
