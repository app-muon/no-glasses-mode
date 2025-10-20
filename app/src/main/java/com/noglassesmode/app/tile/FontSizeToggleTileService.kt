package com.noglassesmode.app.tile

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.database.ContentObserver
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

class FontSizeToggleTileService : TileService() {

    private val prefs by lazy { UserPrefs(this) }
    private var lastClickAt = 0L

    // Observe font scale so the tile updates instantly when SYSTEM.FONT_SCALE changes
    private val fontScaleUri by lazy { Settings.System.getUriFor(Settings.System.FONT_SCALE) }
    private val fontObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) = refreshTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        // Fixed visuals (icon + label never change)
        qsTile?.apply {
            icon = Icon.createWithResource(this@FontSizeToggleTileService, R.drawable.ic_glasses_tile)
            label = getString(R.string.tile_label_short) // "No Glasses"
        }
        // Start listening to system setting changes
        contentResolver.registerContentObserver(fontScaleUri, false, fontObserver)
        refreshTile()
    }

    override fun onStopListening() {
        super.onStopListening()
        contentResolver.unregisterContentObserver(fontObserver)
    }

    override fun onClick() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 600) return  // debounce a bit more for stability
        lastClickAt = now

        // If permission not granted, open our Step 1–3 screen (not system Settings)
        if (!FontScaleManager.canWriteSettings(this)) {
            val intent = Intent(this, SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }

        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val savedBaseline = prefs.baselineScale
        val savedBig = prefs.bigAppliedScale
        val multiplier = 1f + (prefs.bigPercent / 100f)

        val expectedBig = savedBaseline?.let { it * multiplier }
        val inBigNow = when {
            savedBig != null -> FontScaleManager.approxEqual(current, savedBig)
            expectedBig != null -> FontScaleManager.approxEqual(current, expectedBig)
            else -> false
        }

        val (target, goingToBig) = if (inBigNow && savedBaseline != null) {
            // Big -> Normal
            prefs.baselineScale = null
            prefs.bigAppliedScale = null
            savedBaseline to false
        } else {
            // Normal -> Big
            val desiredBig = current * multiplier
            prefs.baselineScale = current
            desiredBig to true
        }

        // Optimistic UI: flip tile state immediately
        qsTile?.apply {
            state = if (goingToBig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            updateTile()
        }

        val ok = FontScaleManager.applyScale(cr, target)
        if (ok) {
            if (goingToBig) {
                // Read back actual applied value (OEMs may quantize)
                prefs.bigAppliedScale = FontScaleManager.getCurrentScale(cr)
            } else {
                prefs.bigAppliedScale = null
            }
            showToast(if (goingToBig) getString(R.string.toast_big) else getString(R.string.toast_normal))
            // Confirm refresh shortly after system redraw
            Handler(Looper.getMainLooper()).postDelayed({ refreshTile() }, 120)
        } else {
            showToast(getString(R.string.toast_failed))
            refreshTile() // revert optimistic state
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return

        val hasPerm = FontScaleManager.canWriteSettings(this)
        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val savedBaseline = prefs.baselineScale
        val savedBig = prefs.bigAppliedScale
        val multiplier = 1f + (prefs.bigPercent / 100f)

        val expectedBig = savedBaseline?.let { it * multiplier }
        val isBig = hasPerm && when {
            savedBig != null -> FontScaleManager.approxEqual(current, savedBig)
            expectedBig != null -> FontScaleManager.approxEqual(current, expectedBig)
            else -> false
        }

        // Keep it tappable even without permission; your onClick opens SettingsActivity
        tile.state = if (isBig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
