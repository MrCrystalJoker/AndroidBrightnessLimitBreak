package com.mrcrystaljoker.brightness

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import rikka.shizuku.Shizuku
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 亮度引擎：通过 Shizuku（shell 权限）调用 `cmd display set-brightness`。
 *
 * 单位说明：
 * - 对外统一用 0..100 的百分比
 * - 内部映射到 0.0102..1.0 的对数曲线，低端分得更细
 * - 0.0102 是红魔 8 Pro 实测的背光下限，再低硬件不接受
 */
object BrightnessController {

    const val MIN_VALUE = 0.0102f
    const val MAX_VALUE = 1.0f
    const val DEFAULT_PCT = 38
    private const val THROTTLE_MS = 80L
    private const val MASK_START_PCT = 30
    private const val MAX_MASK_ALPHA = 0.87f

    private val main = Handler(Looper.getMainLooper())
    private var lastApplyAt = 0L
    private var pendingRunnable: Runnable? = null

    @Volatile
    var currentPct: Int = DEFAULT_PCT
        private set

    @Volatile
    var lastError: String? = null
        private set

    /** 档位变化回调，Service 用来同步遮罩和通知 */
    var listener: ((Int) -> Unit)? = null

    private fun setPct(pct: Int) {
        currentPct = pct
        listener?.invoke(pct)
    }

    /**
     * 极暗档的遮罩透明度：30 档以下开始叠加，0 档最重（0.87）。
     * 这段区间里背光已经压到底，靠画面压暗继续降低视觉亮度。
     */
    fun maskAlphaFor(pct: Int): Float {
        if (pct >= MASK_START_PCT) return 0f
        val t = (MASK_START_PCT - pct).toFloat() / MASK_START_PCT
        return t * MAX_MASK_ALPHA
    }

    fun isShizukuAlive(): Boolean = try {
        Shizuku.pingBinder()
    } catch (t: Throwable) {
        false
    }

    fun hasPermission(): Boolean = try {
        isShizukuAlive() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        false
    }

    fun pctToValue(pct: Int): Float {
        val t = pct.coerceIn(0, 100) / 100.0
        return (MIN_VALUE * (MAX_VALUE / MIN_VALUE).toDouble().pow(t)).toFloat()
    }

    fun valueToPct(value: Float): Int {
        val v = value.coerceIn(MIN_VALUE, MAX_VALUE).toDouble()
        val t = ln(v / MIN_VALUE) / ln(MAX_VALUE.toDouble() / MIN_VALUE)
        return (t * 100).roundToInt().coerceIn(0, 100)
    }

    /** 立即下发，返回是否成功 */
    fun applyNow(pct: Int): Boolean {
        val clamped = pct.coerceIn(0, 100)
        setPct(clamped)
        val value = pctToValue(clamped)
        val ok = runShell("cmd", "display", "set-brightness", String.format("%.5f", value))
        lastError = if (ok) null else "set-brightness 执行失败"
        return ok
    }

    /** 拖动时用，带节流，避免频繁 fork 进程 */
    fun applyThrottled(pct: Int, onDone: (() -> Unit)? = null) {
        val clamped = pct.coerceIn(0, 100)
        setPct(clamped)
        val now = System.currentTimeMillis()
        val wait = THROTTLE_MS - (now - lastApplyAt)

        pendingRunnable?.let { main.removeCallbacks(it) }

        val task = Runnable {
            lastApplyAt = System.currentTimeMillis()
            val value = pctToValue(currentPct)
            runShell("cmd", "display", "set-brightness", String.format("%.5f", value))
            onDone?.invoke()
        }
        pendingRunnable = task

        if (wait <= 0) {
            main.post(task)
        } else {
            main.postDelayed(task, wait)
        }
    }

    /** 接管时关掉自动亮度，避免系统把亮度抢回去 */
    fun disableAutoBrightness(): Boolean =
        runShell("settings", "put", "system", "screen_brightness_mode", "0")

    fun enableAutoBrightness(): Boolean =
        runShell("settings", "put", "system", "screen_brightness_mode", "1")

    /** 用 shell 权限把 App 加进 Doze 电池优化白名单 */
    fun whitelistSelf(pkg: String): Boolean =
        runShell("dumpsys", "deviceidle", "whitelist", "+$pkg")

    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        runCatching {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java
            ).apply { isAccessible = true }
        }.getOrNull()
    }

    private fun runShell(vararg cmd: String): Boolean {
        val method = newProcessMethod
        if (method == null) {
            lastError = "Shizuku newProcess 不可用"
            return false
        }
        return try {
            val process = method.invoke(null, arrayOf(*cmd), null, null) as Process
            val code = process.waitFor()
            if (code != 0) {
                val err = process.errorStream.bufferedReader().readText().trim()
                lastError = err.ifEmpty { "exit=$code" }
            }
            code == 0
        } catch (t: Throwable) {
            lastError = t.message ?: t.javaClass.simpleName
            false
        }
    }

    fun describeState(context: Context): String {
        val shizuku = when {
            !isShizukuAlive() -> "Shizuku 未运行"
            !hasPermission() -> "Shizuku 未授权"
            else -> "Shizuku 已就绪"
        }
        return "$shizuku\n当前档位 ${currentPct}%（背光 ${String.format("%.4f", pctToValue(currentPct))}）"
    }
}
