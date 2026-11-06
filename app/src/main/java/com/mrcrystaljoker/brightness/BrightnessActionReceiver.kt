package com.mrcrystaljoker.brightness

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BrightnessActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val delta = intent.getIntExtra(BrightnessService.EXTRA_DELTA, 0)
        if (delta != 0) {
            BrightnessController.applyNow(BrightnessController.currentPct + delta)
        }
        // 通知服务刷新界面
        context.sendBroadcast(
            Intent(BrightnessService.ACTION_REFRESH).setPackage(context.packageName)
        )
    }
}
