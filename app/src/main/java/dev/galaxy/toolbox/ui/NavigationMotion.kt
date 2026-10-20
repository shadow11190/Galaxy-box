package dev.galaxy.toolbox.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** One interruptible position drives the indicator, page layers and tab emphasis. */
@Stable
internal class NavigationMotion(initial: Int, val count: Int, private val scope: CoroutineScope) {
    private val animation = Animatable(initial.coerceIn(0, count - 1).toFloat())
    private var direct by mutableFloatStateOf(animation.value)
    private var directMode by mutableStateOf(false)
    private var generation = 0L
    private var job: Job? = null
    var target by mutableIntStateOf(initial.coerceIn(0, count - 1)); private set
    var dragging by mutableStateOf(false); private set
    var held by mutableStateOf(false); private set
    var velocity by mutableFloatStateOf(0f); private set
    val position: Float get() = if (directMode) direct else animation.value
    val moving: Boolean get() = held || dragging || animation.isRunning || abs(position - target) > .001f
    fun emphasis(index: Int): Float = (1f - abs(position - index)).coerceIn(0f, 1f)

    fun hold() {
        val current = position
        ++generation; job?.cancel()
        direct = current; directMode = true; held = true; dragging = false; velocity = 0f
    }
    fun dragBy(deltaTabs: Float, velocityTabs: Float) {
        if (!held || !deltaTabs.isFinite()) return
        dragging = true
        direct = (direct + deltaTabs).coerceIn(0f, (count - 1).toFloat())
        velocity = velocityTabs.takeIf { it.isFinite() }?.coerceIn(-8f, 8f) ?: 0f
    }
    fun select(index: Int) = settle(index.coerceIn(0, count - 1), if (directMode) velocity else animation.velocity)
    fun release(commit: Boolean): Int {
        val destination = if (commit && dragging) position.roundToInt().coerceIn(0, count - 1) else target
        settle(destination, if (commit) velocity else 0f)
        return destination
    }
    fun cancelGesture() { if (held || dragging) settle(target, 0f) }
    fun finish() {
        ++generation; job?.cancel(); direct = target.toFloat(); directMode = true
        held = false; dragging = false; velocity = 0f
    }
    private fun settle(index: Int, initialVelocity: Float) {
        val current = position
        val token = ++generation
        job?.cancel()
        target = index; direct = current; directMode = true; held = false; dragging = false; velocity = 0f
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            animation.snapTo(current)
            if (generation != token) return@launch
            directMode = false
            animation.animateTo(index.toFloat(), spring(dampingRatio = .68f, stiffness = 430f, visibilityThreshold = .001f),
                initialVelocity = initialVelocity.coerceIn(-8f, 8f))
        }
    }
}

@Composable internal fun rememberNavigationMotion(selected: Int, count: Int): NavigationMotion {
    val scope = rememberCoroutineScope()
    val motion = remember(count) { NavigationMotion(selected, count, scope) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(selected) { if (motion.target != selected) motion.select(selected) }
    DisposableEffect(motion, lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) motion.finish() }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer); motion.finish() }
    }
    return motion
}
