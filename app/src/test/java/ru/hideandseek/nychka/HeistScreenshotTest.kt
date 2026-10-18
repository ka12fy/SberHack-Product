package ru.hideandseek.nychka

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ru.hideandseek.nychka.heist.BalanceHeist
import ru.hideandseek.nychka.ui.theme.NychkaTheme

/**
 * Прикол «Нычка ворует баланс» на «Главном»: выход, шаг на цыпочках со сменой ног, рывки за сумму,
 * испуг и бегство; тап по лисе — «поймал».
 * Запуск: ./gradlew :app:recordRoborazziDebug — картинки 4x_heist_*.png в app/build/outputs/roborazzi/.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h800dp-xxhdpi")
class HeistScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private val heist = BalanceHeist()

    private fun advance(ms: Long) {
        var left = ms
        while (left > 0) {
            val step = minOf(left, 16L)
            compose.mainClock.advanceTimeBy(step)
            left -= step
        }
        compose.waitForIdle()
    }

    private fun capture(name: String) = compose.onRoot().captureRoboImage("build/outputs/roborazzi/$name.png")

    private fun startOnMain() {
        compose.mainClock.autoAdvance = false
        compose.setContent { NychkaTheme { AppRoot(heist = heist) } }
        advance(300)
        compose.runOnIdle { heist.start() }
        advance(16)
    }

    /** Время прикола (мс) примерно = сколько прошло после старта. */
    private fun advanceTo(ms: Float) {
        val left = (ms - heist.timeMs).toLong()
        if (left > 0) advance(left)
    }

    @Test
    fun heistPlaysThroughAndEnds() {
        startOnMain()
        advanceTo(900f)
        capture("41_heist_plot")
        advanceTo(2_250f)
        capture("42_heist_sneak")
        advanceTo(3_300f)
        capture("43_heist_aim")
        advanceTo(4_700f)
        assertTrue("сумма тянется: ${heist.stretch}", heist.stretch > 0.3f)
        capture("44_heist_pull")
        advanceTo(6_000f)
        capture("45_heist_startled")
        advanceTo(6_600f)
        capture("46_heist_flee")
        advance(1_500)
        assertFalse(heist.running)
        assertEquals(0f, heist.stretch, 0.001f)
    }

    /** Шаг на цыпочках: поднята передняя лапа → обе на земле → поднята задняя. */
    @Test
    fun sneakSwitchesFeet() {
        startOnMain()
        advanceTo(1_350f + 0.3f * 220f)
        capture("47_heist_step_front")
        advanceTo(1_350f + 0.8f * 220f)
        capture("48_heist_step_planted")
        advanceTo(1_350f + 1.3f * 220f)
        capture("49_heist_step_back")
    }

    @Test
    fun tapCatchesTheFox() {
        startOnMain()
        advanceTo(2_400f)
        compose.onNodeWithContentDescription("Нажми, чтобы поймать", substring = true).performClick()
        advance(150)
        assertTrue(heist.caught)
        capture("50_heist_caught")
        advance(2_000)
        assertFalse(heist.running)
    }
}
