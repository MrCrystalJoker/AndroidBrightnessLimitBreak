package com.mrcrystaljoker.brightness

import android.Manifest
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var statusText: TextView

    private val permissionListener =
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            if (requestCode == SHIZUKU_REQUEST) {
                statusText.text = BrightnessController.describeState(this)
                if (grantResult == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Shizuku 已授权", Toast.LENGTH_SHORT).show()
                    // 授权完成立刻把常驻服务拉起来，不用用户再点一次
                    BrightnessService.start(this)
                    // 顺手加进 Doze 白名单，减少被系统清掉的概率
                    BrightnessController.whitelistSelf(packageName)
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.tv_status)

        findViewById<Button>(R.id.btn_shizuku).setOnClickListener { requestShizuku() }
        findViewById<Button>(R.id.btn_start).setOnClickListener {
            if (!BrightnessController.hasPermission()) {
                Toast.makeText(this, "先授权 Shizuku", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            BrightnessService.start(this)
            Toast.makeText(this, "已启动常驻服务", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.btn_panel).setOnClickListener {
            startActivity(Intent(this, PanelActivity::class.java))
        }
        findViewById<Button>(R.id.btn_notify).setOnClickListener { askNotificationPermission() }
        findViewById<Button>(R.id.btn_overlay).setOnClickListener {
            if (Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "悬浮窗权限已具备", Toast.LENGTH_SHORT).show()
            } else {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            }
        }

        Shizuku.addRequestPermissionResultListener(permissionListener)
        findViewById<Button>(R.id.btn_battery).setOnClickListener { requestBatteryWhitelist() }

        scheduleKeepAlive()

        // App 一打开就把常驻服务拉起来（已有授权的前提下）
        if (BrightnessController.hasPermission()) {
            BrightnessService.start(this)
        }
    }

    /** 每 15 分钟让 JobScheduler 检查一次服务存活，被红魔清掉后能自己回来 */
    private fun scheduleKeepAlive() {
        val js = getSystemService(JobScheduler::class.java)
        if (js.getPendingJob(KeepAliveJobService.JOB_ID) != null) return
        val job = JobInfo.Builder(
            KeepAliveJobService.JOB_ID,
            ComponentName(this, KeepAliveJobService::class.java)
        )
            .setPeriodic(15 * 60 * 1000L)
            .setPersisted(true)
            .build()
        runCatching { js.schedule(job) }
    }

    private fun requestBatteryWhitelist() {
        val pm = getSystemService(PowerManager::class.java)
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            Toast.makeText(this, "已经在电池优化白名单里", Toast.LENGTH_SHORT).show()
            return
        }
        // 有 Shizuku 就直接用 shell 权限加，免去系统对话框
        if (BrightnessController.hasPermission() &&
            BrightnessController.whitelistSelf(packageName)
        ) {
            Toast.makeText(this, "已通过 Shizuku 加入白名单", Toast.LENGTH_SHORT).show()
            return
        }
        runCatching {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }.onFailure {
            runCatching {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    override fun onResume() {
        super.onResume()
        statusText.text = BrightnessController.describeState(this)
        // 每次回到前台都确保服务在跑（重复调用是幂等的）
        if (BrightnessController.hasPermission()) {
            BrightnessService.start(this)
        }
    }

    override fun onDestroy() {
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        super.onDestroy()
    }

    private fun requestShizuku() {
        if (!BrightnessController.isShizukuAlive()) {
            Toast.makeText(this, "Shizuku 未运行，请先启动 Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        if (BrightnessController.hasPermission()) {
            Toast.makeText(this, "已经有权限了", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            Shizuku.requestPermission(SHIZUKU_REQUEST)
        } catch (t: Throwable) {
            Toast.makeText(this, "请求失败: ${t.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2)
        } else {
            Toast.makeText(this, "通知权限已具备", Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        private const val SHIZUKU_REQUEST = 1001
    }
}
