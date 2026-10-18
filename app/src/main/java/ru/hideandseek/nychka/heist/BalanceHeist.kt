package ru.hideandseek.nychka.heist

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import app.hideandseek.HideAndSeekBlocker
import ru.hideandseek.nychka.R
import ru.hideandseek.nychka.hidespots.HideSpotRegistry
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Прикол «Нычка ворует баланс»: лиса выходит справа, крадётся на цыпочках к сумме на плашке баланса,
 * тянет её за край (сумма растягивается), сумма пружиной возвращается на место — лиса пугается и удирает.
 * Тап по лисе раньше — «поймал»: она сразу пугается и убегает. Деньги, конечно, никуда не деваются.
 *
 * Слой только рисует: касания берёт лишь небольшая зона над самой лисой. Пока идёт прикол,
 * прячущаяся Нычка из модуля «Прятки» уходит (HideAndSeekBlocker), чтобы на экране не было двух лис.
 */
@Stable
class BalanceHeist {
    /** Идёт ли сейчас прикол. */
    var running by mutableStateOf(false)
        private set

    internal var runId by mutableIntStateOf(0)
        private set

    /** Насколько вытянута сумма на плашке баланса: 0 — как есть, 1 — вытянута к лисе, меньше 0 — сжалась после щелчка. */
    var stretch by mutableFloatStateOf(0f)
        internal set

    /** Сколько миллисекунд идёт прикол. */
    var timeMs by mutableFloatStateOf(0f)
        internal set

    /** Лису поймали тапом. */
    var caught by mutableStateOf(false)
        internal set

    internal var catchRequested = false

    fun start() {
        runId += 1
        running = true
        caught = false
        catchRequested = false
        stretch = 0f
        timeMs = 0f
    }

    fun stop() {
        running = false
        stretch = 0f
    }

    /** Поймать лису (как тап по ней): она пугается и убегает. */
    fun catchFox() {
        if (running) catchRequested = true
    }
}

/** Шаг сценария показа (`"demo": { "script": [...] }` в контенте), на котором Нычка ворует баланс. */
const val HEIST_STEP = "heist"

/** Плашка баланса читает отсюда, насколько растянуть сумму. */
val LocalBalanceHeist = staticCompositionLocalOf<BalanceHeist?> { null }

/** Id меток в реестре мест: вся плашка баланса и сама сумма на ней. */
const val BALANCE_SPOT = "main.balance"
const val BALANCE_AMOUNT_SPOT = "main.balance_amount"

// ---- Кадры ----

/**
 * Кадр: картинка, её высота на экране и точки в долях картинки:
 * [groundX], [groundY] — середина между ногами на земле, [handX] — кончики лап, которыми лиса тянет.
 */
private class HeistFrame(
    @DrawableRes val res: Int,
    val heightDp: Float,
    val groundX: Float,
    val groundY: Float,
    val handX: Float = 0.005f,
)

/** Все кадры нарисованы в одном масштабе: одинаковые dp на пиксель исходника — лиса везде одного роста. */
private const val DP_PER_SOURCE_PX = 0.1f

private fun frame(@DrawableRes res: Int, sourceHeightPx: Int, groundX: Float, groundY: Float) =
    HeistFrame(res, sourceHeightPx * DP_PER_SOURCE_PX, groundX, groundY)

private val Plot = frame(R.drawable.heist_plot, 1235, 0.454f, 0.995f)

// Три кадра шага вырезаны одинаково: подменяются без сдвига.
private val Sneak = frame(R.drawable.heist_sneak, 1096, 0.514f, 0.995f)
private val StepFront = frame(R.drawable.heist_step_front, 1096, 0.514f, 0.995f)
private val StepBack = frame(R.drawable.heist_step_back, 1096, 0.514f, 0.995f)
private val Aim = frame(R.drawable.heist_aim, 1057, 0.52f, 0.994f)
private val Reach = frame(R.drawable.heist_reach, 1144, 0.503f, 0.995f)
private val Pull = frame(R.drawable.heist_pull, 1108, 0.548f, 0.995f)
private val PullHard = frame(R.drawable.heist_pull_hard, 1022, 0.612f, 0.994f)
private val Startled = frame(R.drawable.heist_startled, 1179, 0.589f, 0.995f)
private val Flee = frame(R.drawable.heist_flee, 961, 0.433f, 0.994f)

/** Высота кулаков в кадре рывка (доля картинки). */
private const val PULL_HAND_Y = 0.56f

private val AllFrames = listOf(Plot, Sneak, StepFront, StepBack, Aim, Reach, Pull, PullHard, Startled, Flee)

// ---- Сценарий по времени, мс ----

private const val ENTER_END = 450f
private const val PLOT_END = 1350f
private const val STEPS = 8
private const val STEP_MS = 220f
private const val SNEAK_END = PLOT_END + STEPS * STEP_MS
private const val AIM_END = SNEAK_END + 400f
private const val REACH_END = AIM_END + 500f
private const val TUG_MS = 180f

/** Сумма щёлкает обратно, лиса пугается. */
internal const val SNAP_AT = REACH_END + 1800f
private const val STARTLED_END = SNAP_AT + 450f
internal const val HEIST_TOTAL_MS = STARTLED_END + 700f

/** Насколько сумма растягивается по ширине при stretch = 1. */
internal const val AMOUNT_STRETCH = 0.22f

/** Растяжение суммы во времени. [snapFrom] — сколько было в момент щелчка. */
private fun stretchAt(t: Float, snapFrom: Float): Float = when {
    t < REACH_END -> 0f
    t < SNAP_AT -> {
        val p = (t - REACH_END) / (SNAP_AT - REACH_END)
        easeOut(p) * (0.88f + 0.12f * sin(t / TUG_MS * PI.toFloat()))
    }
    else -> {
        // Щелчок: сумма сжимается и пружинит обратно.
        val s = (t - SNAP_AT) / 1000f
        -0.35f * snapFrom * exp(-s * 9f) * cos(s * 28f)
    }
}

/**
 * Слой прикола. Кладётся поверх экрана «Главный»; [registry] — откуда взять, где сейчас плашка баланса и сумма.
 */
@Composable
fun BalanceHeistLayer(heist: BalanceHeist, registry: HideSpotRegistry, modifier: Modifier = Modifier) {
    if (!heist.running) return
    HideAndSeekBlocker()
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val bitmaps = AllFrames.associateWith { ImageBitmap.imageResource(it.res) }
    val runId = heist.runId

    LaunchedEffect(runId) {
        var start = -1L
        var jumped = 0f
        var snapFrom = 1f
        var snapped = false
        var finished = false
        while (!finished) {
            withFrameMillis { now ->
                if (start < 0) start = now
                var t = (now - start) + jumped
                if (heist.catchRequested) {
                    heist.catchRequested = false
                    if (t < SNAP_AT) {
                        heist.caught = true
                        jumped += SNAP_AT - t
                        t = SNAP_AT
                    }
                }
                if (!snapped && t >= SNAP_AT) {
                    snapped = true
                    snapFrom = heist.stretch.coerceAtLeast(0.3f)
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
                heist.timeMs = t
                heist.stretch = stretchAt(t, snapFrom)
                finished = t >= HEIST_TOTAL_MS
            }
        }
        heist.stop()
    }

    val layer = remember { LayerWidth() }
    Box(modifier.fillMaxSize().onSizeChanged { layer.px = it.width.toFloat() }) {
        Canvas(Modifier.fillMaxSize()) {
            val stage = stageOf(registry, size.width) ?: return@Canvas
            val pose = poseAt(heist.timeMs, heist.stretch, stage, this, bitmaps)
            drawFrame(pose, bitmaps.getValue(pose.frame))
        }

        // Зона тапа над лисой — только пока она не испугалась. Мимо неё касания идут к экрану.
        val catchable by remember { derivedStateOf { heist.timeMs < SNAP_AT } }
        if (catchable) {
            val box = 96.dp
            Box(
                Modifier
                    .offset {
                        val stage = stageOf(registry, layer.px)
                        if (stage == null || layer.px <= 0f) {
                            IntOffset(-10_000, -10_000)
                        } else {
                            val pose = poseAt(heist.timeMs, heist.stretch, stage, density, bitmaps)
                            val center = pose.centerX(bitmaps.getValue(pose.frame), density)
                            IntOffset((center - box.toPx() / 2f).roundToInt(), (pose.y - box.toPx()).roundToInt())
                        }
                    }
                    .size(box)
                    .semantics { contentDescription = "Нычка тянет деньги с баланса. Нажми, чтобы поймать" }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                    ) { heist.catchFox() },
            )
        }
    }
}

/** Ширина слоя в px (для зоны тапа; читается при размещении, а не при композиции). */
private class LayerWidth {
    var px = 0f
}

// ---- Где лиса в момент t ----

/** Сцена в пикселях корня: точка «хвата» на правом краю суммы, линия земли и ширина экрана. */
private class Stage(val grip: Offset, val amountWidth: Float, val groundY: Float, val screenWidth: Float)

private fun Density.stageOf(registry: HideSpotRegistry, screenWidth: Float): Stage? {
    val plate = registry.spots[BALANCE_SPOT]?.bounds?.takeIf { !it.isEmpty } ?: return null
    val amount = registry.spots[BALANCE_AMOUNT_SPOT]?.bounds?.takeIf { !it.isEmpty }
    val grip = Offset(amount?.right ?: (plate.left + plate.width * 0.45f), amount?.center?.y ?: plate.center.y)
    // Ноги лисы — ниже суммы ровно настолько, чтобы в кадре рывка её кулаки были на уровне суммы.
    val groundY = grip.y + (Pull.groundY - PULL_HAND_Y) * Pull.heightDp.dp.toPx()
    return Stage(grip, amount?.width ?: plate.width * 0.35f, groundY, screenWidth)
}

/**
 * Поза: кадр и куда его поставить. [x] — по горизонтали точка рук ([byHand]) или середина между ногами,
 * [y] — линия земли под ногами (с подскоком).
 */
private class Pose(
    val frame: HeistFrame,
    val x: Float,
    val y: Float,
    val byHand: Boolean = false,
    val rotation: Float = 0f,
) {
    fun anchorX(): Float = if (byHand) frame.handX else frame.groundX

    fun size(bitmap: ImageBitmap, density: Density): IntSize {
        val h = with(density) { frame.heightDp.dp.toPx() }
        return IntSize((h * bitmap.width / bitmap.height).roundToInt(), h.roundToInt())
    }

    fun centerX(bitmap: ImageBitmap, density: Density): Float {
        val w = size(bitmap, density).width
        return x + (0.5f - anchorX()) * w
    }
}

private fun poseAt(t: Float, stretch: Float, stage: Stage, density: Density, bitmaps: Map<HeistFrame, ImageBitmap>): Pose {
    fun width(f: HeistFrame): Float = with(density) {
        val b = bitmaps.getValue(f)
        f.heightDp.dp.toPx() * b.width / b.height
    }
    fun dp(v: Float): Float = with(density) { v.dp.toPx() }

    val ground = stage.groundY
    val xStart = stage.screenWidth + Plot.groundX * width(Plot) + dp(4f)
    val xPlot = stage.screenWidth - (1f - Plot.groundX) * width(Plot) - dp(8f)
    // Где стоит, когда целится: коготь чуть-чуть не достаёт до суммы.
    val xAim = stage.grip.x + (Aim.groundX - Aim.handX) * width(Aim) + dp(10f)
    // Правый край суммы, пока её тянут (растягивается вправо, к лисе).
    val gripX = stage.grip.x + stage.amountWidth * AMOUNT_STRETCH * stretch.coerceAtLeast(0f)
    // Где стоят ноги в конце рывков — отсюда лиса пугается и удирает.
    val xPulled = stage.grip.x + stage.amountWidth * AMOUNT_STRETCH * 0.9f + (Pull.groundX - Pull.handX) * width(Pull)

    return when {
        t < ENTER_END -> {
            val p = t / ENTER_END
            Pose(Plot, lerp(xStart, xPlot, easeOut(p)), ground - sin(PI.toFloat() * p) * dp(10f))
        }
        t < PLOT_END -> {
            // Ухмыляется и чуть покачивается: задумала.
            val p = (t - ENTER_END) / (PLOT_END - ENTER_END)
            Pose(Plot, xPlot, ground, rotation = 3f * sin(p * 4f * PI.toFloat()))
        }
        t < SNEAK_END -> {
            // Шаг на цыпочках: поднята передняя лапа → обе на земле → поднята задняя → обе на земле…
            val q = (t - PLOT_END) / STEP_MS
            val k = floor(q).toInt()
            val local = q - k
            val lift = (local / 0.55f).coerceAtMost(1f)
            val lifted = local < 0.55f
            val frame = when {
                !lifted -> Sneak
                k % 2 == 0 -> StepFront
                else -> StepBack
            }
            val progress = (k + easeInOut(lift)) / STEPS
            val bob = if (lifted) sin(PI.toFloat() * lift) * dp(3f) else 0f
            Pose(frame, lerp(xPlot, xAim, progress), ground - bob)
        }
        t < AIM_END -> {
            val p = (t - SNEAK_END) / (AIM_END - SNEAK_END)
            Pose(Aim, xAim - dp(3f) * easeOut(p), ground, rotation = -4f * (1f - p))
        }
        t < REACH_END -> {
            // Тянется щепоткой к краю суммы.
            val p = (t - AIM_END) / (REACH_END - AIM_END)
            Pose(Reach, lerp(stage.grip.x + dp(14f), stage.grip.x + dp(2f), easeOut(p)), ground, byHand = true)
        }
        t < SNAP_AT -> {
            // Рывки: два кадра по очереди, лапы держат край суммы, тело дёргается.
            val tug = floor((t - REACH_END) / TUG_MS).toInt()
            val frame = if (tug % 2 == 0) Pull else PullHard
            val shake = if (tug % 2 == 0) dp(1.5f) else -dp(1.5f)
            Pose(frame, gripX + shake, ground, byHand = true, rotation = if (tug % 2 == 0) 0f else 2f)
        }
        t < STARTLED_END -> {
            val p = (t - SNAP_AT) / (STARTLED_END - SNAP_AT)
            Pose(Startled, xPulled + dp(30f) * easeOut(p), ground - sin(PI.toFloat() * p) * dp(28f))
        }
        else -> {
            val p = ((t - STARTLED_END) / (HEIST_TOTAL_MS - STARTLED_END)).coerceIn(0f, 1f)
            val from = xPulled + dp(30f)
            val to = stage.screenWidth + width(Flee)
            Pose(Flee, lerp(from, to, p * p), ground - sin(PI.toFloat() * p) * dp(22f) - dp(6f))
        }
    }
}

private fun DrawScope.drawFrame(pose: Pose, bitmap: ImageBitmap) {
    val size = pose.size(bitmap, this)
    val left = -pose.anchorX() * size.width
    val top = -pose.frame.groundY * size.height
    withTransform({
        translate(pose.x, pose.y)
        rotate(pose.rotation, pivot = Offset.Zero)
    }) {
        drawImage(bitmap, dstOffset = IntOffset(left.roundToInt(), top.roundToInt()), dstSize = size)
    }
}

private fun lerp(a: Float, b: Float, p: Float): Float = a + (b - a) * p

private fun easeOut(x: Float): Float {
    val v = 1f - x.coerceIn(0f, 1f)
    return 1f - v * v
}

private fun easeInOut(x: Float): Float {
    val v = x.coerceIn(0f, 1f)
    return v * v * (3f - 2f * v)
}
