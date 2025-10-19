package com.noglassesmode.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.net.toUri
import com.noglassesmode.app.R
import com.noglassesmode.app.core.FontScaleManager
import com.noglassesmode.app.data.UserPrefs
import java.util.Locale
import kotlin.math.round

class SettingsActivity : ComponentActivity() {

    private lateinit var bigEt: EditText
    private lateinit var baselineTv: TextView
    private lateinit var previewBig: TextView
    private lateinit var statusTv: TextView
    private lateinit var grantBtn: Button
    private lateinit var saveBtn: Button

    private val prefs by lazy { UserPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ngm_settings)

        bigEt = findViewById(R.id.etBig)
        baselineTv = findViewById(R.id.tvBaseline)
        previewBig = findViewById(R.id.previewBig)
        statusTv = findViewById(R.id.tvStatus)
        grantBtn = findViewById(R.id.btnGrant)
        saveBtn = findViewById(R.id.btnSave)

        bigEt.setText(format(prefs.bigScale))
        baselineTv.text = getString(
            R.string.label_baseline,
            FontScaleManager.getCurrentScale(contentResolver)
        )

        grantBtn.setOnClickListener {
            // Open system page to allow "Modify system settings"
            startActivity(intentForPermission(this))
        }

        saveBtn.setOnClickListener {
            val b = parseScale(bigEt.text?.toString())
            if (b == null) {
                toast("Enter 0.85–2.00")
                return@setOnClickListener
            }
            prefs.bigScale = b
            applyPreview(b)
            toast(getString(R.string.save))
        }

        applyPreview(prefs.bigScale)
        updatePermissionUi()
    }

    override fun onResume() {
        super.onResume()
        baselineTv.text = getString(
            R.string.label_baseline,
            FontScaleManager.getCurrentScale(contentResolver)
        )
        updatePermissionUi()
    }

    private fun updatePermissionUi() {
        val granted = FontScaleManager.canWriteSettings(this)
        statusTv.text = if (granted)
            getString(R.string.system_write_permission) + " ✅"
        else
            getString(R.string.system_write_permission) + " ❌"

        grantBtn.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun applyPreview(b: Float) {
        previewBig.textSize = toSp(14f, b)
        previewBig.text = getString(R.string.label_big)
    }

    private fun toSp(baseSp: Float, scale: Float) = baseSp * scale

    private fun parseScale(s: String?): Float? {
        val v = s?.toFloatOrNull() ?: return null
        if (v < 0.85f || v > 2.00f) return null
        return round(v * 100f) / 100f
    }

    private fun format(v: Float) = String.format(Locale.US, "%.2f", v)

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    companion object {
        fun intentForPermission(ctx: Context): Intent =
            Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS).apply {
                data = "package:${ctx.packageName}".toUri()
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
    }
}
