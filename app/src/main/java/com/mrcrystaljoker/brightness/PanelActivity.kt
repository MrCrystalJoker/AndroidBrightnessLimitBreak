package com.mrcrystaljoker.brightness

import android.os.Bundle
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PanelActivity : AppCompatActivity() {

    private lateinit var valueText: TextView

    override fun onStart() {
        super.onStart()
        // Dialog 主题默认按内容收缩，这里强制拉到屏幕宽度的 92%
        val width = (resources.displayMetrics.widthPixels * 0.92f).toInt()
        window?.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_panel)
        setFinishOnTouchOutside(true)

        valueText = findViewById(R.id.tv_panel_value)
        val seek = findViewById<SeekBar>(R.id.seek_brightness)

        seek.max = 100
        seek.progress = BrightnessController.currentPct
        valueText.text = "${BrightnessController.currentPct}%"

        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return
                valueText.text = "$progress%"
                BrightnessController.applyThrottled(progress)
            }

            override fun onStartTrackingTouch(sb: SeekBar?) = Unit

            override fun onStopTrackingTouch(sb: SeekBar?) {
                BrightnessController.applyNow(seek.progress)
                sendBroadcast(
                    android.content.Intent(BrightnessService.ACTION_REFRESH)
                        .setPackage(packageName)
                )
            }
        })

        findViewById<android.view.View>(R.id.btn_close).setOnClickListener { finish() }
    }
}
