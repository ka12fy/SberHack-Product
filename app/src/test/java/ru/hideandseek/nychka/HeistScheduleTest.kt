package ru.hideandseek.nychka

import app.hideandseek.core.Day
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.hideandseek.nychka.heist.HeistSchedule

class HeistScheduleTest {

    @Test
    fun oneOrTwoHeistDaysEveryWeek() {
        val monday = Day.parse("2026-10-19")!!
        assertTrue(monday.dayOfWeek == 1)
        var total = 0
        for (week in 0 until 104) {
            val days = (0 until 7).count { HeistSchedule.isHeistDay(monday + week * 7 + it) }
            assertTrue("неделя $week: $days", days in 1..2)
            total += days
        }
        // В среднем полтора раза в неделю.
        assertTrue("всего $total", total in 120..190)
    }
}
