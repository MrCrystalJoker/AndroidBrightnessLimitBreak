package com.mrcrystaljoker.brightness

import android.app.job.JobParameters
import android.app.job.JobService

/**
 * 保活兜底：JobScheduler 每 15 分钟检查一次前台服务是否还活着。
 *
 * 进程被红魔清掉后，JobScheduler 会把进程拉起来执行这个任务，
 * 此时 [BrightnessService.isRunning] 是 false，于是重新启动服务。
 */
class KeepAliveJobService : JobService() {

    override fun onStartJob(params: JobParameters?): Boolean {
        if (!BrightnessService.isRunning && BrightnessController.hasPermission()) {
            runCatching { BrightnessService.start(this) }
        }
        jobFinished(params, false)
        return false
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        const val JOB_ID = 2001
    }
}
