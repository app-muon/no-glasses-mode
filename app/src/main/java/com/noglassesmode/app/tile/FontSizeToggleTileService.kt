package com.noglassesmode.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
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

    override fun onStartListening() {
        super.onStartListening()
        // Set fixed visuals once
        qsTile?.apply {
            icon = Icon.createWithResource(this@FontSizeToggleTileService, R.drawable.ic_glasses_tile)
            label = getString(R.string.tile_label_short) // "No Glasses"
        }
        refreshTile()
    }

    override fun onClick() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastClickAt < 400) return
        lastClickAt = now
        // super.onClick() // not needed

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
            prefs.baselineScale = null
            prefs.bigAppliedScale = null
            savedBaseline to false
        } else {
            val desiredBig = current * multiplier
            prefs.baselineScale = current
            desiredBig to true
        }

        val ok = FontScaleManager.applyScale(cr, target)
        if (ok) {
            if (goingToBig) {
                prefs.bigAppliedScale = FontScaleManager.getCurrentScale(cr)
            } else {
                prefs.bigAppliedScale = null
            }
            showToast(if (goingToBig) getString(R.string.toast_big) else getString(R.string.toast_normal))
            refreshTile()
        } else {
            showToast(getString(R.string.toast_failed))
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val hasPerm = FontScaleManager.canWriteSettings(this)

        val current = FontScaleManager.getCurrentScale(contentResolver)
        val savedBaseline = prefs.baselineScale
        val savedBig = prefs.bigAppliedScale
        val multiplier = 1f + (prefs.bigPercent / 100f)

        val expectedBig = savedBaseline?.let { it * multiplier }
        val isBig = hasPerm && when {
            savedBig != null -> FontScaleManager.approxEqual(current, savedBig)
            expectedBig != null -> FontScaleManager.approxEqual(current, expectedBig)
            else -> false
        }

        // Never use STATE_UNAVAILABLE; keep it tappable to request permission
        tile.state = if (isBig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
