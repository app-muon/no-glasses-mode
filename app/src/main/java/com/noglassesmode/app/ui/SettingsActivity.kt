package com.noglassesmode.app.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.quicksettings.TileService
import android.text.method.LinkMovementMethod
import android.util.TypedValue
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.slider.Slider
import com.noglassesmode.app.R
import com.noglassesmode.app.core.FontScaleManager
import com.noglassesmode.app.data.UserPrefs

class SettingsActivity : ComponentActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnGrant: Button
    private lateinit var sliderBigPercent: Slider
    private lateinit var previewNormalText: TextView
    private lateinit var previewBigText: TextView
    private lateinit var btnDone: Button
    private var awaitingPermission = false
    private val permHandler = Handler(Looper.getMainLooper())
    private var permChecks = 0

    private val prefs by lazy { UserPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ngm_settings)

        // ---- Bind views ----
        tvStatus = findViewById(R.id.tvStatus)
        previewNormalText = findViewById(R.id.previewNormalText)
        previewBigText = findViewById(R.id.previewBigText)
        btnGrant = findViewById(R.id.btnGrant)
        sliderBigPercent = findViewById(R.id.sliderBigPercent)
        btnDone = findViewById(R.id.btnDone)
        btnDone.setOnClickListener { finish() }
        // ---- Init values ----
        sliderBigPercent.value = prefs.bigPercent
        updateComputedLabel()
        updatePermissionUi()

// ---- Listeners ----
        btnGrant.setOnClickListener {
            awaitingPermission = true
            startActivity(intentForPermission(this))
        }

        sliderBigPercent.addOnChangeListener { _, value, _ ->
            prefs.bigPercent = value
            updateComputedLabel()
        }

        findViewById<com.google.android.material.floatingactionbutton.FloatingActionButton>(R.id.btnAbout)
            .setOnClickListener { showAboutDialog() }
    }

    override fun onResume() {
        super.onResume()
        updateComputedLabel()
        updatePermissionUi()

        if (awaitingPermission) {
            permChecks = 0
            pollPermission.run()
        }
    }

    // ---- Helpers ----

    private fun updatePermissionUi() {
        val granted = Settings.System.canWrite(applicationContext)
        val step1Title = findViewById<TextView>(R.id.step1Title) // add this id in XML
        val completeMsg = findViewById<TextView>(R.id.setupCompleteMessage)
        if (granted) {
            // Hide status + button
            tvStatus.isVisible = false
            btnGrant.isVisible = false
            completeMsg.isVisible = true
            btnDone.isVisible = true

            // Change Step 1 title to confirm success
            step1Title.text = getString(R.string.step_1_permissions_done)
        } else {
            // Show status + button
            tvStatus.isVisible = true
            btnGrant.isVisible = true

            // Reset title
            step1Title.text = getString(R.string.step_1_permissions)
        }
    }

    private fun updateComputedLabel() {
        val base = stableBaseline()                               // your stable “Normal”
        val multiplier = 1f + (sliderBigPercent.value / 100f)
        val big = base * multiplier

        // Render previews independent of current system font scale
        setPreviewSizePx(previewNormalText, 18f, base)
        setPreviewSizePx(previewBigText, 18f, big)
    }

    private fun stableBaseline(): Float {
        val m = 1f + (sliderBigPercent.value / 100f)

        // 1) Prefer the saved baseline (set when switching to Big)
        prefs.baselineScale?.let { return it }

        val current = FontScaleManager.getCurrentScale(contentResolver)

        // 2) If we know the exact Big that was applied and we're currently at that value,
        //    reconstruct baseline by dividing by the multiplier
        val bigApplied = prefs.bigAppliedScale
        if (bigApplied != null && FontScaleManager.approxEqual(current, bigApplied)) {
            return bigApplied / m
        }

        // 3) Fallback: assume current is the baseline
        return current
    }


    private fun setPreviewSizePx(tv: TextView, spAtScale1: Float, scale: Float) {
        val dm = resources.displayMetrics
        val px = spAtScale1 * scale * dm.density   // use density, NOT scaledDensity
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, px)
    }

    private fun showAboutDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_about, null, false)
        val tv = v.findViewById<TextView>(R.id.tvAbout)
        tv.text = getString(R.string.about_body)
        tv.movementMethod = LinkMovementMethod.getInstance() // make links clickable

        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.about_title)
            .setView(v)
            .setPositiveButton(R.string.got_it, null)
            .show()
    }

    private val pollPermission = object : Runnable {
        override fun run() {
            val granted = Settings.System.canWrite(applicationContext)
            if (granted) {
                updatePermissionUi()
                TileService.requestListeningState(
                    this@SettingsActivity,
                    ComponentName(
                        this@SettingsActivity,
                        com.noglassesmode.app.tile.FontSizeToggleTileService::class.java
                    )
                )
                awaitingPermission = false
                permChecks = 0
            } else if (permChecks++ < 15) {           // ~15 * 120ms ≈ 1.8s max
                permHandler.postDelayed(this, 120)
            } else {
                // give up; leave UI showing not granted
                awaitingPermission = false
                permChecks = 0
            }
        }
    }

    companion object {
        fun intentForPermission(ctx: Context): Intent =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:${ctx.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
