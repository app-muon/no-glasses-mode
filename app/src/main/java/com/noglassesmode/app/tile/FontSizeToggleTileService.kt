// FontSizeToggleTileService.kt
package com.noglassesmode.app.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.os.*
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast
import com.noglassesmode.app.R
import com.noglassesmode.app.core.FontScaleManager
import com.noglassesmode.app.data.UserPrefs
import kotlin.math.abs
import kotlin.math.round

class FontSizeToggleTileService : TileService() {

    private val prefs by lazy { UserPrefs(this) }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var lastClickAt = 0L
    private var lastState: Int? = null
    private var verifyToken = 0          // avoid stale verify races
    private var writeInProgress = false  // cheap insurance vs rapid taps

    private val eps = 0.02f
    private val debounceMs = 500L
    private val verifyDelayMs = 600L
    private val noticeableDrift = 0.05f // 5%

    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            icon = android.graphics.drawable.Icon.createWithResource(
                this@FontSizeToggleTileService, R.drawable.ic_glasses_tile
            )
            label = getString(R.string.tile_label_short)
        }
        // First run: treat current as baseline “Normal” if we have no state yet.
        if (prefs.baselineScale == null && prefs.bigAppliedScale == null) {
            prefs.baselineScale = FontScaleManager.getCurrentScale(contentResolver)
        }
        refreshTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    override fun onClick() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastClickAt < debounceMs) return
        if (writeInProgress) return
        lastClickAt = now

        // Permission gate
        if (!Settings.System.canWrite(applicationContext)) {
            val intent = Intent(this, com.noglassesmode.app.ui.SettingsActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                val pi = PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
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

        val inBigNow = isBigNow(current, m)
        val goingToBig = !inBigNow

        // (1) Re-snapshot baseline on toggle-to-Big if user changed “Normal” externally
        if (goingToBig && prefs.bigAppliedScale == null) {
            val storedBase = prefs.baselineScale
            if (storedBase == null || !approx(current, storedBase)) {
                prefs.baselineScale = current
            }
        }

        val target = if (goingToBig) {
            // Use (possibly a new snapshot) baseline × multiplier
            val base = prefs.baselineScale ?: current
            round2(base * m)
        } else {
            // Return to stored baseline; keep it as the long-term anchor
            val base = prefs.baselineScale
            if (base == null) {
                Toast.makeText(this, getString(R.string.toast_state_error), Toast.LENGTH_SHORT).show()
                refreshTile()
                return
            }
            base
        }

        val thisToken = ++verifyToken

        // Apply scale
        writeInProgress = true
        val ok = try {
            FontScaleManager.applyScale(cr, target)
        } finally {
            writeInProgress = false
        }

        if (!ok) {
            Toast.makeText(this, getString(R.string.toast_failed), Toast.LENGTH_SHORT).show()
            refreshTile()
            return
        }

        // Haptic only on success (avoid haptic on permission/failed paths)
        lightHaptic()

        // Update prefs for intended state
        if (goingToBig) {
            prefs.bigAppliedScale = target
            // baselineScale already set/validated above; keep it
        } else {
            // Big → Normal: clear Big marker ONLY; keep baseline as anchor
            prefs.bigAppliedScale = null
        }

        // Optimistic tile update, then toast
        setTileState(if (goingToBig) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE)
        Toast.makeText(
            this,
            if (goingToBig) getString(R.string.toast_big) else getString(R.string.toast_normal),
            Toast.LENGTH_SHORT
        ).show()

        // Verify later; ignore if a newer click happened
        mainHandler.postDelayed({
            if (thisToken != verifyToken) return@postDelayed
            val applied = FontScaleManager.getCurrentScale(cr)
            if (!approx(applied, target)) {
                val drift = abs(applied - target)
                // If we intended to go Big, record actual Big value (rounded by system/OEM)
                if (goingToBig) prefs.bigAppliedScale = applied
                // Only refresh UI if drift is visually noticeable
                if (drift > noticeableDrift) {
                    refreshTile()
                }
            }
        }, verifyDelayMs)
    }

    private fun refreshTile() {
        val cr = contentResolver
        val current = FontScaleManager.getCurrentScale(cr)
        val m = 1f + (prefs.bigPercent / 100f)

        // (3) Prioritise expected-from-baseline; only use savedBig if baseline missing
        val newState = if (isBigNow(current, m)) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        if (lastState != newState) setTileState(newState)
    }

    private fun setTileState(state: Int) {
        lastState = state
        qsTile?.let { tile ->
            tile.state = state
            tile.updateTile()
        }
    }

    // Prioritise baseline×m; fall back to savedBig only if baseline is null.
    private fun isBigNow(current: Float, m: Float): Boolean {
        val base = prefs.baselineScale
        val expected = base?.let { round2(it * m) }
        if (expected != null && approx(current, expected)) return true
        val savedBig = prefs.bigAppliedScale
        return savedBig != null && base == null && approx(current, savedBig)
    }

    private fun approx(a: Float, b: Float) = abs(a - b) <= eps
    private fun round2(x: Float) = round(x * 100f) / 100f

    @SuppressLint("MissingPermission")
    private fun lightHaptic() {
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val vm = getSystemService(VibratorManager::class.java) ?: return
                val vib = vm.defaultVibrator
                if (vib.hasVibrator()) {
                    vib.vibrate(VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK))
                }
            } else {
                @Suppress("DEPRECATION")
                (getSystemService(VIBRATOR_SERVICE) as? Vibrator)?.let { vib ->
                    if (vib.hasVibrator()) vib.vibrate(10)
                }
            }
        } catch (_: SecurityException) {
            // no-op: missing permission on some OEMs
        }
    }

}
