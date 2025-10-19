package com.noglassesmode.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
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
        sliderBigPercent.value = prefs.bigPercent
        updateComputedLabel()
        updatePermissionUi()

        // ---- Listeners ----
        btnGrant.setOnClickListener { startActivity(intentForPermission(this)) }

        // Save instantly when user slides
        sliderBigPercent.addOnChangeListener { _, value, _ ->
            prefs.bigPercent = value
            updateComputedLabel()
        }
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
        val baseline = FontScaleManager.getCurrentScale(contentResolver)
        val multiplier = 1f + (sliderBigPercent.value / 100f)
        val abs = baseline * multiplier

        // Scale previews
        previewNormalText.textSize = 14f * baseline
        previewBigText.textSize = 14f * abs
    }

    companion object {
        fun intentForPermission(ctx: Context): Intent =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
