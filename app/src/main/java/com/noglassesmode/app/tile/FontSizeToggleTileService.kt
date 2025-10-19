package com.noglassesmode.app.tile

import android.app.PendingIntent
import android.content.Intent
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

    override fun onStartListening() {
        super.onStartListening()
        refreshTile()
    }

    override fun onClick() {
        super.onClick()

        if (!FontScaleManager.canWriteSettings(this)) {
            val intent = SettingsActivity.intentForPermission(this)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // API 34+: PendingIntent overload
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

// Are we currently in Big? Compare to the exact Big we applied last time.
        val inBigNow = savedBig != null && FontScaleManager.approxEqual(current, savedBig)

        val (target, goingToBig) = if (inBigNow && savedBaseline != null) {
            // Big -> Normal
            prefs.baselineScale = null
            prefs.bigAppliedScale = null
            savedBaseline to false
        } else {
            // Normal -> Big: snapshot current as baseline, compute desired Big
            val desiredBig = current * multiplier
            prefs.baselineScale = current
            desiredBig to true
        }

        val ok = FontScaleManager.applyScale(cr, target)

        if (ok) {
            if (goingToBig) {
                // Read back the actual applied value (OEMs may clamp/quantize)
                val applied = FontScaleManager.getCurrentScale(cr)
                prefs.bigAppliedScale = applied
            } else {
                // Returned to normal: ensure Big marker cleared
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
        val savedBig = prefs.bigAppliedScale
        val isBig = hasPerm && savedBig != null && FontScaleManager.approxEqual(current, savedBig)

        tile.state = when {
            !hasPerm -> Tile.STATE_UNAVAILABLE
            isBig -> Tile.STATE_ACTIVE
            else -> Tile.STATE_INACTIVE
        }

        // Keep label short — avoids marquee scroll when tile is not yet pinned
        tile.label = getString(R.string.tile_label_short) // e.g. "No Glasses"
        tile.updateTile()
    }

    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
