package com.noglassesmode.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.noglassesmode.app.R
import com.noglassesmode.app.core.FontScaleManager
import com.noglassesmode.app.data.UserPrefs
import com.noglassesmode.app.ui.SettingsActivity
import kotlin.math.abs
import kotlin.math.round

class FontSizeToggleTileService : TileService() {

    private val prefs by lazy { UserPrefs(this) }

    // Debounce + state cache
    private var lastClickAt = 0L
    private var lastState: Int? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    // Guard during write (very short)
    private var writeInProgress = false

    // Helpers
    private val EPS = 0.02f
    private fun approx(a: Float, b: Float) = abs(a - b) <= EPS
    private fun round2(x: Float) = round(x * 100f) / 100f

    override fun onStartListening() {
        super.onStartListening()
        // Fixed visuals (icon + label never change)
        qsTile?.apply {
            icon = Icon.createWithResource(this@FontSizeToggleTileService, R.drawable.ic_glasses_tile)
            label = getString(R.string.tile_label_short) // "No Glasses"
        }
        lastState = null
        refreshTile()
    }

    override fun onClick() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (writeInProgress || now - lastClickAt < 450L) return
        lastClickAt = now

        // Permission gate → open our setup screen
        if (!FontScaleManager.canWriteSettings(this)) {
            val intent = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION") startActivityAndCollapse(intent)
            }
            return
        }

        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val m = 1f + (prefs.bigPercent / 100f)

        // Detect Big
        val savedBaseline = prefs.baselineScale
        val savedBig = prefs.bigAppliedScale
        val expectedFromBaseline = savedBaseline?.let { round2(it * m) }
        val inBigNow = when {
            savedBig != null -> approx(current, savedBig)
            expectedFromBaseline != null -> approx(current, expectedFromBaseline)
            else -> false
        }

        // Decide target + manage baseline
        val target: Float
        val goingToBig: Boolean
        if (inBigNow && savedBaseline != null) {
            // Big → Normal
            target = savedBaseline
            goingToBig = false
        } else {
            // Normal → Big (snapshot baseline first)
            prefs.baselineScale = current
            target = round2(current * m)
            goingToBig = true
        }

        // Write (optimistic)
        writeInProgress = true
        val ok = FontScaleManager.applyScale(cr, target)
        writeInProgress = false

        if (!ok) {
            showToast(getString(R.string.toast_failed))
            return
        }

        // Trust what we wrote; update prefs immediately
        if (goingToBig) {
            prefs.bigAppliedScale = target
        } else {
            prefs.bigAppliedScale = null
            prefs.baselineScale = null
        }

        showToast(if (goingToBig) getString(R.string.toast_big) else getString(R.string.toast_normal))
        refreshTile()

        // Optional background verify (non-blocking); adjust only if large mismatch
        mainHandler.postDelayed({
            val applied = FontScaleManager.getCurrentScale(contentResolver)
            if (!approx(applied, target)) {
                if (goingToBig) prefs.bigAppliedScale = applied
                if (abs(applied - target) > 0.10f) {
                    refreshTile()
                }
            }
        }, 500L)
    }

    private fun refreshTile() {
        val tile = qsTile ?: return

        val hasPerm = Settings.System.canWrite(applicationContext)
        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val m = 1f + (prefs.bigPercent / 100f)

        val savedBaseline = prefs.baselineScale
        val savedBig = prefs.bigAppliedScale
        val expectedFromBaseline = savedBaseline?.let { round2(it * m) }

        val isBig = hasPerm && when {
            savedBig != null -> approx(current, savedBig)
            expectedFromBaseline != null -> approx(current, expectedFromBaseline)
            else -> false
        }

        val newState = if (isBig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (lastState == newState) return

        lastState = newState
        tile.state = newState
        tile.updateTile()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
