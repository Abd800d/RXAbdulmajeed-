package com.almakhedhi.rxreader

import android.app.Application
import android.content.Intent
import android.os.Process
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlin.system.exitProcess

@HiltAndroidApp
class RxReaderApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override fun onCreate() {
        // Install first so even Hilt/Firebase init failures are reported.
        installCrashReporter()
        super.onCreate()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    /**
     * Last-resort handler: forwards any uncaught crash to
     * [CrashReportActivity] (which offers copy + retry) instead of a
     * silent close.
     */
    private fun installCrashReporter() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val trace = Log.getStackTraceString(error).take(CRASH_MAX)
                val intent = Intent(this, CrashReportActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    .putExtra(CrashReportActivity.EXTRA_TRACE, trace)
                startActivity(intent)
            } catch (_: Exception) {
                previous?.uncaughtException(thread, error)
            } finally {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    companion object {
        const val CRASH_MAX = 20000
    }
}
