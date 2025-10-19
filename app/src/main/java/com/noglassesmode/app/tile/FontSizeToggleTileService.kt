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
                // ✅ new API: PendingIntent path (no warning)
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
                startActivityAndCollapse(pi)
            } else {
                // ✅ safely suppress deprecation only for older Androids
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
            return
        }


        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val big = prefs.bigScale

        val target = if (FontScaleManager.approxEqual(current, big)) {
            // Going from Big → Baseline
            (prefs.baselineScale ?: 1.00f)
        } else {
            // Going to Big: snapshot current as new baseline
            prefs.baselineScale = current
            big
        }

        val ok = FontScaleManager.applyScale(cr, target)

        if (ok) {
            val toBig = FontScaleManager.approxEqual(target, big)
            showToast(if (toBig) getString(R.string.toast_big) else getString(R.string.toast_normal))
            refreshTile()
        } else {
            showToast(getString(R.string.toast_failed))
        }
    }

    private fun refreshTile() {
        val tile = qsTile ?: return
        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val big = prefs.bigScale

        val stateLabel = if (FontScaleManager.approxEqual(current, big)) {
            getString(R.string.tile_label_big)
        } else {
            getString(R.string.tile_label_normal)
        }

        tile.state = if (FontScaleManager.canWriteSettings(this)) Tile.STATE_ACTIVE else Tile.STATE_UNAVAILABLE
        tile.label = getString(R.string.tile_label_prefix, stateLabel)
        tile.updateTile()
    }



    private fun showToast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
