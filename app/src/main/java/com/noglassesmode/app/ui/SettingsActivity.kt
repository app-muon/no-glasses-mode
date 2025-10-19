package com.noglassesmode.app.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.ComponentActivity
import com.noglassesmode.app.R
import com.noglassesmode.app.core.FontScaleManager
import com.noglassesmode.app.data.UserPrefs
import kotlin.math.round

class SettingsActivity : ComponentActivity() {

    private lateinit var normalEt: EditText
    private lateinit var bigEt: EditText
    private lateinit var statusTv: TextView
    private lateinit var grantBtn: Button
    private lateinit var saveBtn: Button
    private lateinit var previewNormal: TextView
    private lateinit var previewBig: TextView

    private val prefs by lazy { UserPrefs(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ngm_settings)

        normalEt = findViewById(R.id.etNormal)
        bigEt = findViewById(R.id.etBig)
        statusTv = findViewById(R.id.tvStatus)
        grantBtn = findViewById(R.id.btnGrant)
        saveBtn = findViewById(R.id.btnSave)
        previewNormal = findViewById(R.id.previewNormal)
        previewBig = findViewById(R.id.previewBig)

        // numeric (0.85–2.00) with 2 decimals
        listOf(normalEt, bigEt).forEach { et ->
            et.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
            et.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(4)) // e.g., 2.00
        }

        normalEt.setText(format(prefs.normalScale))
        bigEt.setText(format(prefs.bigScale))
        updatePermissionUi()

        grantBtn.setOnClickListener {
            FontScaleManager.openManageWriteSettings(this)
        }

        saveBtn.setOnClickListener {
            val n = parseScale(normalEt.text?.toString())
            val b = parseScale(bigEt.text?.toString())
            if (n == null || b == null) {
                toast("Enter values between 0.85 and 2.00")
                return@setOnClickListener
            }
            if (b < n + 0.10f) {
                toast("Big must be at least 0.10 larger than Normal")
                return@setOnClickListener
            }
            prefs.normalScale = n
            prefs.bigScale = b
            prefs.validateAndSwapIfNeeded()
            applyPreviews(n, b)
            toast("Saved")
        }

        applyPreviews(prefs.normalScale, prefs.bigScale)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUi()
    }

    private fun updatePermissionUi() {
        val granted = FontScaleManager.canWriteSettings(this)
        statusTv.text = if (granted) "System write permission: ✅" else "System write permission: ❌"
        grantBtn.visibility = if (granted) View.GONE else View.VISIBLE
    }

    private fun applyPreviews(n: Float, b: Float) {
        previewNormal.textSize = toSp(14f, n) // base 14sp scaled
        previewBig.textSize = toSp(14f, b)
        previewNormal.text = getString(R.string.normal_preview_x, format(n))
        previewBig.text = getString(R.string.big_preview_x, format(b))
    }

    private fun toSp(baseSp: Float, scale: Float) = baseSp * scale

    private fun parseScale(s: String?): Float? {
        val v = s?.toFloatOrNull() ?: return null
        if (v < 0.85f || v > 2.00f) return null
        return round(v * 100f) / 100f
    }

    private fun format(v: Float) = String.format("%.2f", v)

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()

    companion object {
        fun intentForPermission(ctx: Context): Intent =
            Intent(ctx, SettingsActivity::class.java).apply {
                putExtra("req_perm", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
    }
}
