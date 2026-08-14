package com.iskol.wifioverlay

import java.util.Calendar

/**
 * Реальная системная история подключений — из `dumpsys wifi` (mConnectionEvents),
 * которую ведёт сама Android для всех подключений, независимо от того, что их
 * инициировало (наше приложение, системные настройки, другое приложение, авто-
 * переключение). Сбрасывается при перезагрузке телефона — это метрики с момента
 * последней загрузки, не вечная история.
 */
object SystemConnectionHistory {

    data class Event(
        val timestamp: Long,
        val ssid: String,
        val durationMillis: Long,
        val success: Boolean,
        val frequency: Int
    )

    data class SsidHistory(
        val lastConnectedAt: Long?,
        val lastDisconnectedAt: Long?,
        val frequency: Int?
    )

    // startTime=8-13 10:41:42.316, SSID="Gaspar", BSSID=a4:7c:..., durationMillis=1909,
    // ... connectionResult=1, ... mChannelInfo=2462, ...
    private val eventRegex = Regex(
        """startTime=(\d{1,2})-(\d{1,2}) (\d{1,2}):(\d{1,2}):(\d{1,2})\.(\d+), SSID="([^"]*)", BSSID=[^,]+, durationMillis=(\d+),.*?connectionResult=(\d+),.*?mChannelInfo=(\d+)"""
    )

    fun fetchEvents(): List<Event> {
        val out = RootShell.execAsUid(2000, "dumpsys wifi")
        val year = Calendar.getInstance().get(Calendar.YEAR)
        val events = mutableListOf<Event>()

        eventRegex.findAll(out).forEach { m ->
            val g = m.groupValues
            val cal = Calendar.getInstance()
            cal.set(
                year, g[1].toInt() - 1, g[2].toInt(),
                g[3].toInt(), g[4].toInt(), g[5].toInt()
            )
            cal.set(Calendar.MILLISECOND, g[6].toIntOrNull() ?: 0)
            events.add(
                Event(
                    timestamp = cal.timeInMillis,
                    ssid = g[7],
                    durationMillis = g[8].toLongOrNull() ?: 0L,
                    success = g[9] == "1",
                    frequency = g[10].toIntOrNull() ?: 0
                )
            )
        }
        return events.sortedBy { it.timestamp }
    }

    /**
     * Последнее успешное подключение к сети и приблизительное время отключения
     * (момент начала следующей попытки подключения к любой сети после неё —
     * интерфейс один, значит предыдущая сессия к этому моменту завершилась).
     */
    fun historyFor(ssid: String): SsidHistory? {
        val events = fetchEvents()
        val lastSuccess = events.lastOrNull { it.ssid == ssid && it.success } ?: return null
        val nextEvent = events.firstOrNull { it.timestamp > lastSuccess.timestamp }
        return SsidHistory(
            lastConnectedAt = lastSuccess.timestamp,
            lastDisconnectedAt = nextEvent?.timestamp,
            frequency = lastSuccess.frequency
        )
    }
}
