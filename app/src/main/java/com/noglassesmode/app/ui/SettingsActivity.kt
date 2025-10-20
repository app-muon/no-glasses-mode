package com.noglassesmode.app.ui
import android.text.method.LinkMovementMethod
import android.view.LayoutInflater
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.ComponentActivity
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

        // ---- Init values ----
        // ---- Init values ----
        sliderBigPercent.value = prefs.bigPercent

// now update previews based on the stable baseline
        sliderBigPercent.value = prefs.bigPercent
        updateComputedLabel()
        updatePermissionUi()

// ---- Listeners ----
        btnGrant.setOnClickListener { startActivity(intentForPermission(this)) }
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
    }


    // ---- Helpers ----

    private fun updatePermissionUi() {
        val granted = FontScaleManager.canWriteSettings(this)
        tvStatus.text = if (granted)
            getString(R.string.system_write_permission) + " ✅"
        else
            getString(R.string.system_write_permission) + " ❌"
        btnGrant.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun updateComputedLabel() {
        val base = stableBaseline()                               // your stable “Normal”
        val multiplier = 1f + (sliderBigPercent.value / 100f)
        val big = base * multiplier

        // Render previews independent of current system font scale
        setPreviewSizePx(previewNormalText, 18f, base)
        setPreviewSizePx(previewBigText,    18f, big)
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
    companion object {
        fun intentForPermission(ctx: Context): Intent =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
