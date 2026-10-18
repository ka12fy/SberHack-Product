package ru.hideandseek.nychka

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.hideandseek.LocalHideAndSeek
import kotlinx.coroutines.delay
import ru.hideandseek.nychka.debug.DevPanel
import ru.hideandseek.nychka.heist.BalanceHeist
import ru.hideandseek.nychka.heist.BalanceHeistLayer
import ru.hideandseek.nychka.heist.HEIST_STEP
import ru.hideandseek.nychka.heist.LocalBalanceHeist
import ru.hideandseek.nychka.hidespots.HideSpotDebugOverlay
import ru.hideandseek.nychka.hidespots.HideSpotRegistry
import ru.hideandseek.nychka.hidespots.LocalHideSpotRegistry
import ru.hideandseek.nychka.ui.components.KidsTabBar
import ru.hideandseek.nychka.ui.components.Tab
import ru.hideandseek.nychka.ui.screens.ContentScreen
import ru.hideandseek.nychka.ui.screens.MainScreen
import ru.hideandseek.nychka.ui.screens.PiggyScreen
import ru.hideandseek.nychka.ui.screens.TasksScreen
import ru.hideandseek.nychka.ui.theme.KidsColors

/**
 * Корень прототипа: имитация вкладок СберKids + реестр мест для пряток.
 * Слой с Нычкой рисует модуль «Прятки» (HideAndSeekHost в MainActivity) поверх всего этого.
 * Долгое нажатие на лапку: рамки мест + панель демо (если корень обёрнут в HideAndSeekHost).
 */
@Composable
fun AppRoot(
    initialTab: Tab = Tab.Main,
    initialDebug: Boolean = false,
    heist: BalanceHeist = remember { BalanceHeist() },
    /** Через сколько мс после захода на «Главный» сам начнётся прикол «Нычка ворует баланс»; 0 — только из панели демо. */
    heistAutoStartMs: Long = 0L,
    /** Можно ли сейчас начать прикол самому (например, сегодня день кражи и её ещё не было). */
    heistAllowed: () -> Boolean = { true },
    /** Прикол начался сам (запомнить, что сегодня он уже был). */
    onHeistAutoStarted: () -> Unit = {},
) {
    val registry = remember { HideSpotRegistry() }
    var tab by rememberSaveable { mutableStateOf(initialTab) }
    var debug by rememberSaveable { mutableStateOf(initialDebug) }
    val hideAndSeek = LocalHideAndSeek.current

    LaunchedEffect(heistAutoStartMs, tab) {
        if (heistAutoStartMs > 0 && tab == Tab.Main && heistAllowed()) {
            delay(heistAutoStartMs)
            if (tab == Tab.Main && !heist.running && heistAllowed()) {
                heist.start()
                onHeistAutoStarted()
            }
        }
    }

    // Сценарий показа: шаг "heist" — кража баланса. Как только до него дошли и ребёнок на «Главном»,
    // Нычка крадётся к балансу; когда прикол закончился (или его прервали сменой вкладки),
    // сценарий идёт дальше.
    LaunchedEffect(hideAndSeek) { hideAndSeek?.demoActions = setOf(HEIST_STEP) }
    val heistStepNow = hideAndSeek?.demoAction == HEIST_STEP
    LaunchedEffect(heistStepNow, tab) {
        if (heistStepNow && tab == Tab.Main && !heist.running) {
            delay(1_200L)
            if (!heist.running) heist.start()
        }
    }
    var heistWasRunning by remember { mutableStateOf(false) }
    LaunchedEffect(heist.running) {
        if (heist.running) {
            heistWasRunning = true
        } else if (heistWasRunning) {
            heistWasRunning = false
            if (hideAndSeek?.demoAction == HEIST_STEP) hideAndSeek?.completeDemoAction()
        }
    }

    CompositionLocalProvider(LocalHideSpotRegistry provides registry, LocalBalanceHeist provides heist) {
        Box(Modifier.fillMaxSize().background(KidsColors.Page)) {
            when (tab) {
                Tab.Main -> MainScreen()
                Tab.Tasks -> TasksScreen()
                Tab.Content -> ContentScreen()
                Tab.Piggy -> PiggyScreen()
            }
            // Прикол «Нычка ворует баланс» — поверх «Главного», под таб-баром.
            if (tab == Tab.Main) BalanceHeistLayer(heist, registry)
            KidsTabBar(
                selected = tab,
                onSelect = {
                    // Сменили вкладку — карточка «Нашёл!» убирается.
                    if (it != tab) {
                        hideAndSeek?.onScreenChanged()
                        heist.stop()
                    }
                    tab = it
                },
                onPawLongPress = { debug = !debug },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
            if (debug) {
                HideSpotDebugOverlay(registry)
                if (hideAndSeek != null) {
                    DevPanel(
                        hideAndSeek = hideAndSeek,
                        onClose = { debug = false },
                        onHeist = {
                            debug = false
                            tab = Tab.Main
                            heist.start()
                        },
                        onRestart = {
                            heist.stop()
                            hideAndSeek.restartDemo()
                        },
                        modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding(),
                    )
                }
            }
        }
    }
}
