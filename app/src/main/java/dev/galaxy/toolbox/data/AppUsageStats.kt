package dev.galaxy.toolbox.data

import android.app.AppOpsManager
import android.app.usage.NetworkStats
import android.app.usage.NetworkStatsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.net.ConnectivityManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

internal enum class UsagePeriod(val title: String) {
    DAY("今天"), WEEK("本週"), MONTH("本月");

    fun start(now: Long, zone: ZoneId): Long {
        val date = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val firstDay = when (this) {
            DAY -> date
            WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            MONTH -> date.withDayOfMonth(1)
        }
        return firstDay.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

internal enum class UsageMetric(val title: String) {
    SCREEN("前景使用時間"), WIFI("Wi-Fi"), MOBILE("行動數據")
}

internal data class UsageEntry(val label: String, val source: String, val value: Long)
internal data class UsageRanking(val from: Long, val to: Long, val rows: List<UsageEntry>?, val firstRecorded: Long? = null)

internal object AppUsageStats {
    @Suppress("DEPRECATION")
    fun allowed(context: Context): Boolean = runCatching {
        context.getSystemService(AppOpsManager::class.java)
            .checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /** Read Android's existing history on IO. No background collection. */
    fun read(context: Context, period: UsagePeriod, metric: UsageMetric): UsageRanking {
        val now = System.currentTimeMillis()
        val start = period.start(now, ZoneId.systemDefault())
        if (!allowed(context)) return UsageRanking(start, now, null)
        return runCatching {
            when (metric) {
                UsageMetric.SCREEN -> foreground(context, start, now)
                UsageMetric.WIFI -> dataUsage(context, ConnectivityManager.TYPE_WIFI, start, now)
                UsageMetric.MOBILE -> if (Build.VERSION.SDK_INT >= 29)
                    dataUsage(context, ConnectivityManager.TYPE_MOBILE, start, now) else UsageRanking(start, now, null)
            }
        }.getOrElse { UsageRanking(start, now, null) }
    }

    private fun foreground(context: Context, start: Long, end: Long): UsageRanking {
        val usage = context.getSystemService(UsageStatsManager::class.java)
        val records = usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, start, end)
            ?: return UsageRanking(start, end, null)
        // The framework may expand the query to an interval boundary. Omit spillover from older intervals.
        val covered = records.filter { it.firstTimeStamp in start until end && it.lastTimeStamp >= it.firstTimeStamp }
        val totals = mutableMapOf<String, Long>()
        covered.forEach { stat ->
            if (stat.totalTimeInForeground > 0) totals.merge(stat.packageName, stat.totalTimeInForeground, Long::plus)
        }
        return UsageRanking(start, end, totals.entries.sortedByDescending { it.value }.take(20).map {
            UsageEntry(label(context, it.key), it.key, it.value)
        }, covered.minOfOrNull { it.firstTimeStamp })
    }

    private fun dataUsage(context: Context, networkType: Int, start: Long, end: Long): UsageRanking {
        val manager = context.getSystemService(NetworkStatsManager::class.java)
        val totals = mutableMapOf<Int, Long>()
        manager.querySummary(networkType, null, start, end).use { stats ->
            val bucket = NetworkStats.Bucket()
            while (stats.hasNextBucket() && stats.getNextBucket(bucket)) {
                val uid = bucket.uid
                if (uid < 10_000 || UserHandle.getUserHandleForUid(uid) != Process.myUserHandle()) continue
                val bytes = bucket.rxBytes.coerceAtLeast(0) + bucket.txBytes.coerceAtLeast(0)
                if (bytes > 0) totals.merge(uid, bytes, Long::plus)
            }
        }
        val pm = context.packageManager
        return UsageRanking(start, end, totals.entries.sortedByDescending { it.value }.take(20).map { (uid, bytes) ->
            val packages = pm.getPackagesForUid(uid).orEmpty().distinct()
            val title = when (packages.size) {
                0 -> "UID $uid"
                1 -> label(context, packages.single())
                else -> AppUsageStats.sharedLabel(context, uid)
            }
            val source = if (packages.isEmpty()) "" else "UID $uid · " + packages.joinToString(", ")
            UsageEntry(title, source, bytes)
        })
    }

    private fun sharedLabel(context: Context, uid: Int) =
        dev.galaxy.toolbox.ui.AppText.forContext(context, "共用 UID") + " $uid"

    @Suppress("DEPRECATION")
    private fun label(context: Context, packageName: String): String = runCatching {
        context.packageManager.getApplicationInfo(packageName, 0)
            .loadLabel(context.packageManager).toString()
    }.getOrDefault(packageName)
}
