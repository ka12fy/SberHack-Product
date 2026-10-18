package ru.hideandseek.nychka.heist

import android.content.Context
import app.hideandseek.core.Day
import app.hideandseek.core.Seeded

/**
 * Когда Нычка ворует баланс в настоящем приложении: один-два дня в каждой неделе (с понедельника
 * по воскресенье), одинаково у всех, как и место дня. В такой день прикол случается один раз —
 * на «Главном», через несколько секунд после захода.
 */
object HeistSchedule {

    /** День кражи? В каждой неделе их 1 или 2, и они не идут подряд. */
    fun isHeistDay(day: Day, salt: String = "nychka"): Boolean {
        val week = Math.floorDiv(day.epochDay + 3, 7) // недели с понедельника: 1970-01-01 — четверг
        val hash = Seeded.hash("$salt|heist|$week")
        val count = 1 + (hash % 2u).toInt()
        val first = ((hash shr 8) % 7u).toInt()
        val second = (first + 2 + ((hash shr 16) % 4u).toInt()) % 7
        val weekday = day.dayOfWeek - 1 // 0 — понедельник
        return weekday == first || (count == 2 && weekday == second)
    }

    private const val PREFS = "nychka_heist"
    private const val KEY_LAST_DAY = "lastHeistDay"

    /** Сегодня день кражи, и её сегодня ещё не было. */
    fun isDue(context: Context, today: Day): Boolean =
        isHeistDay(today) && prefs(context).getString(KEY_LAST_DAY, null) != today.toString()

    /** Запомнить, что сегодня кража уже была. */
    fun markShown(context: Context, today: Day) {
        prefs(context).edit().putString(KEY_LAST_DAY, today.toString()).apply()
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
