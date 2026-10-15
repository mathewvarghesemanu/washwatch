package space.mathewvarghese.washwatchalerts

/** The most recent finished cycle of a machine: how long it ran and when its "done" alert arrived (Unix seconds). */
data class LastCycle(val seconds: Long, val finishedAt: Long)

/** A cycle whose "started" alert arrived but whose "done" alert hasn't yet. */
data class RunningCycle(val machine: Machine, val startedAt: Long, val last: LastCycle?)

/** One row of the in-app history: a cycle folded from its "started" and "done" alerts, or a lone alert. */
sealed interface HistoryEntry {
    val key: String

    /** Unix seconds the entry is ordered and dated by. */
    val time: Long

    /** Either alert can be missing: a "started" that was never heard, or a cycle that never reported finishing. */
    data class Cycle(val machine: Machine, val started: Alert?, val done: Alert?) : HistoryEntry {
        init { require(started != null || done != null) }
        override val key: String get() = (done ?: started)!!.id
        override val time: Long get() = (done ?: started)!!.time

        /** The host's own measurement when there is one, else the gap between the two alerts. */
        val ranSeconds: Long?
            get() = done?.let { Cycles.parseRanFor(it.message) }
                ?: if (started != null && done != null) done.time - started.time else null
    }

    data class Single(val alert: Alert) : HistoryEntry {
        override val key: String get() = alert.id
        override val time: Long get() = alert.time
    }
}

/** Cycle timing derived from the alert history, so nothing extra needs to be stored or kept in sync. */
object Cycles {
    /** Stop timing a cycle this long after it started; by then the "done" alert was almost certainly missed. */
    const val MAX_RUNNING_SECONDS = 3 * 60 * 60L

    private val DURATION_PART = Regex("(\\d+)\\s*([hms])")

    /** Seconds in the host's "Ran for 1h 2m 3s." message, or null if the message isn't in that form. */
    fun parseRanFor(message: String): Long? {
        val text = message.substringAfter("Ran for", "")
        val parts = DURATION_PART.findAll(text).toList()
        if (parts.isEmpty()) return null
        return parts.sumOf { part ->
            val n = part.groupValues[1].toLong()
            when (part.groupValues[2]) {
                "h" -> n * 3600
                "m" -> n * 60
                else -> n
            }
        }
    }

    /** [history] is newest first, as stored. */
    fun lastCycle(history: List<Alert>, machine: Machine): LastCycle? = history.firstNotNullOfOrNull { alert ->
        if (alert.machine != machine || alert.kind != AlertKind.DONE) return@firstNotNullOfOrNull null
        parseRanFor(alert.message)?.let { LastCycle(it, alert.time) }
    }

    /** Machines whose latest cycle alert is "started", less than [MAX_RUNNING_SECONDS] ago. */
    fun running(history: List<Alert>, nowSeconds: Long): List<RunningCycle> = Machine.entries.mapNotNull { machine ->
        val latest = history.firstOrNull {
            it.machine == machine && (it.kind == AlertKind.STARTED || it.kind == AlertKind.DONE)
        } ?: return@mapNotNull null
        if (latest.kind != AlertKind.STARTED || nowSeconds - latest.time >= MAX_RUNNING_SECONDS) return@mapNotNull null
        RunningCycle(machine, latest.time, lastCycle(history, machine))
    }

    /** Older history entries are tucked away behind a "Show older" button. */
    const val RECENT_SECONDS = 7 * 24 * 60 * 60L

    /**
     * Folds [history] into one entry per cycle, newest first. Each "done" closes the latest open "started" of the
     * same machine. A cycle that is still running is left out (the app shows it as a live timer instead), and
     * test or "hasn't started" messages stay as single entries.
     */
    fun timeline(history: List<Alert>, nowSeconds: Long): List<HistoryEntry> {
        val entries = mutableListOf<HistoryEntry>()
        val open = mutableMapOf<Machine, Alert>()
        for (alert in history.sortedBy { it.time }) {
            val machine = alert.machine
            when {
                machine != null && alert.kind == AlertKind.STARTED ->
                    // A second start before any "done" means the first cycle's finish was never heard.
                    open.put(machine, alert)?.let { entries += HistoryEntry.Cycle(machine, it, null) }
                machine != null && alert.kind == AlertKind.DONE ->
                    entries += HistoryEntry.Cycle(machine, open.remove(machine), alert)
                else -> entries += HistoryEntry.Single(alert)
            }
        }
        open.forEach { (machine, started) ->
            if (nowSeconds - started.time >= MAX_RUNNING_SECONDS) entries += HistoryEntry.Cycle(machine, started, null)
        }
        return entries.sortedByDescending { it.time }
    }

    /** The "started" alert that [done] closes, if it was received. [history] is newest first and contains [done]. */
    fun startedFor(history: List<Alert>, done: Alert): Alert? {
        val machine = done.machine ?: return null
        val previous = history.dropWhile { it.id != done.id }.drop(1).firstOrNull {
            it.machine == machine && (it.kind == AlertKind.STARTED || it.kind == AlertKind.DONE)
        }
        return previous?.takeIf { it.kind == AlertKind.STARTED }
    }

    /** "1h 2m", "42m 17s" or "45s", matching how the host words durations. */
    fun formatDuration(totalSeconds: Long): String {
        val h = totalSeconds / 3600
        val m = totalSeconds % 3600 / 60
        val s = totalSeconds % 60
        return when {
            h > 0 -> "${h}h ${m}m"
            m > 0 -> "${m}m ${s}s"
            else -> "${s}s"
        }
    }

    /** Stopwatch style: "1:02:03" or "12:34". */
    fun formatElapsed(totalSeconds: Long): String {
        val seconds = totalSeconds.coerceAtLeast(0)
        val h = seconds / 3600
        val m = seconds % 3600 / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
