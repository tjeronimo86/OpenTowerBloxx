package de.tonyapps.towerbloxx.game

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** Gebaeudearten; jede hat eigene Fassade. Reihenfolge = Freischaltung im Stadtmodus. */
enum class Kind { BLUE, RED, GREEN, YELLOW }

enum class Mode { QUICK, TIME, CHALLENGE, CITY, PARTY }

data class GameConfig(
    val mode: Mode,
    val kind: Kind = Kind.BLUE,
    /** Leben; 0 = unbegrenzt (Zeitrennen). */
    val lives: Int = 3,
    /** Sekunden; 0 = ohne Zeitlimit. */
    val timeLimit: Float = 0f,
    /** Ziel in Stockwerken; 0 = endlos. */
    val targetFloors: Int = 0,
    /** Grundgeschwindigkeit des Krans (Faktor). */
    val swingSpeed: Float = 1f,
    /** Grundschwanken des Turms, auch bei perfektem Bauen. */
    val wind: Float = 0f,
    /** Am Ziel kommt ein Dach drauf (Stadtmodus). */
    val roofAtEnd: Boolean = false,
    val level: Int = 0,
    val plot: Int = -1,
    /** Wechselnde Gebaeudeart alle 10 Stockwerke. */
    val cycleKinds: Boolean = false,
    val seed: Long = System.nanoTime(),
    /** Vorgebaute Stockwerke, nur fuer die Pruefung im Emulator. */
    val startFloors: Int = 0,
)

enum class GameEvent { SPAWN, DROP, LAND, PERFECT, MISS, CRASH, RESIDENT, ROOF, OVER, WIN, TICK }

data class GameStats(
    val config: GameConfig,
    val floors: Int,
    val population: Int,
    val perfects: Int,
    val bestCombo: Int,
    val won: Boolean,
)

/**
 * Die Spiellogik eines Spielers, ohne Android-Abhaengigkeiten.
 * Einheiten: Ein Stockwerk ist 1 hoch und 2 breit, y zeigt nach oben.
 */
class Game(val config: GameConfig, private val events: (Game, GameEvent) -> Unit = { _, _ -> }) {

    companion object {
        const val W = 2f
        const val H = 1f
        const val BASE_TOP = 0.5f
        const val ROPE = 9f
        const val GRAVITY = 26f
        const val HANG_ABOVE = 3.4f
        const val PERFECT = 0.14f
        const val MISS_AT = W * 0.55f
    }

    class Floor(val off: Float, val kind: Kind, val door: Boolean, val residents: Int, val perfect: Boolean) {
        var squash = 0f
    }

    class Debris(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var rot: Float, val spin: Float, val kind: Kind,
    )

    class Para(var x: Float, var y: Float, val z: Float, val variant: Int, val phase: Float) {
        var done = false
    }

    /** Partikel: 0 = Wolke/Staub, 1 = Stern, 2 = Funkeln */
    class Fx(
        var x: Float, var y: Float, var z: Float, var vx: Float, var vy: Float,
        val life: Float, val type: Int, val size: Float, var rot: Float, val spin: Float,
    ) {
        var age = 0f
    }

    enum class Phase { LOWERING, HANGING, FALLING, WAIT, ROOFING, OVER, WIN }

    private val rnd = Random(config.seed)

    val floors = ArrayList<Floor>()
    private val cum = ArrayList<Float>()

    var phase = Phase.WAIT; private set
    private var phaseTime = 0f
    private var waitFor = 0.6f
    private var endAfterWait: Phase? = null

    var time = 0f; private set
    var swayAmp = config.wind; private set
    private val swayFreq = (2 * PI / 3.4).toFloat()

    private var swingT = 0f
    private val swingAmp = 0.2f

    // Der aktuelle Block (haengend oder fallend), Unterkante Mitte
    var bx = 0f; private set
    var by = 0f; private set
    var brot = 0f; private set
    private var bvx = 0f
    private var bvy = 0f
    var blockKind = config.kind; private set
    var pivotX = 0f; private set
    var pivotY = 0f; private set

    var lives = config.lives; private set
    var population = 0; private set
    var perfects = 0; private set
    var combo = 0; private set
    var bestCombo = 0; private set
    var comboTimer = 0f; private set
    var timeLeft = config.timeLimit; private set
    var lastGain = 0; private set
    var gainSerial = 0; private set
    var lastPerfect = false; private set
    var missSerial = 0; private set

    var camX = 0f; private set
    var camY = BASE_TOP + 1.4f; private set
    var shake = 0f; private set

    val debris = ArrayList<Debris>()
    val paras = ArrayList<Para>()
    val fx = ArrayList<Fx>()

    var roofY = Float.NaN; private set
    var autoplay = false
    private var autoTarget = 0f
    var paused = false

    /** Hoehenmarken fuers HUD (Text, Stockwerk); vom Aufrufer gesetzt. */
    var markers: List<Pair<String, Int>> = emptyList()

    private var lastTick = -1

    val finished get() = phase == Phase.OVER || phase == Phase.WIN
    val won get() = phase == Phase.WIN
    val topSurface get() = BASE_TOP + floors.size * H
    val hasBlock get() = phase == Phase.LOWERING || phase == Phase.HANGING || phase == Phase.FALLING

    init {
        repeat(config.startFloors) { n ->
            floors += Floor(0f, kindFor(n), door = n == 0, residents = 0, perfect = true)
            cum += 0f
        }
        camY = topSurface + 1.4f
    }

    fun stats() = GameStats(config, floors.size, population, perfects, bestCombo, won)

    // ---------------------------------------------------------------- Turm

    private fun swayFactor(h: Float): Float {
        val n = max(floors.size, 1).toFloat()
        return (h / n).coerceIn(0f, 1f).pow(1.6f)
    }

    fun sway(h: Float, t: Float = time): Float = swayAmp * sin(swayFreq * t) * swayFactor(h)

    /** Mitte von Stockwerk i (0 = unten). */
    fun floorX(i: Int, t: Float = time): Float = cum[i] + sway(i + 1f, t)

    /** Neigung von Stockwerk i in Radiant (gegen den Uhrzeigersinn positiv). */
    fun floorTilt(i: Int, t: Float = time): Float {
        val n = max(floors.size, 1).toFloat()
        val r = ((i + 1f) / n).coerceIn(0f, 1f)
        val slope = swayAmp * sin(swayFreq * t) * 1.6f * r.pow(0.6f) / n
        return -atan(slope)
    }

    fun floorY(i: Int): Float = BASE_TOP + i * H

    private fun topX(t: Float = time): Float = if (floors.isEmpty()) 0f else floorX(floors.size - 1, t)
    private fun topRest(): Float = if (cum.isEmpty()) 0f else cum.last()

    fun kindFor(n: Int): Kind =
        if (config.cycleKinds) Kind.entries[(n / 10) % Kind.entries.size] else config.kind

    // ---------------------------------------------------------------- Ablauf

    fun tap() {
        if (paused) return
        if (phase == Phase.HANGING || (phase == Phase.LOWERING && phaseTime > 0.2f)) drop()
    }

    private fun drop() {
        val theta = swingAmp * sin(swingT)
        val dTheta = swingAmp * cos(swingT) * swingOmega()
        // Tangentiale Geschwindigkeit am Seilende, gedaempft: der Block soll gut vorhersehbar fallen
        bvx = ROPE * dTheta * cos(theta) * 0.35f
        bvy = ROPE * dTheta * sin(theta) * 0.35f
        setPhase(Phase.FALLING)
        events(this, GameEvent.DROP)
    }

    private fun swingOmega(): Float {
        val n = floors.size
        val levelBoost = config.level * 0.08f
        return min(2.2f + n * 0.018f + levelBoost, 3.9f) * config.swingSpeed
    }

    private fun setPhase(p: Phase) {
        phase = p
        phaseTime = 0f
    }

    private fun hangBottom() = topSurface + HANG_ABOVE

    private fun updateHanging(dt: Float) {
        swingT += dt * swingOmega()
        val theta = swingAmp * sin(swingT)
        pivotX = camX
        pivotY = hangBottom() + H + ROPE
        var lift = 0f
        if (phase == Phase.LOWERING) {
            val k = (phaseTime / 0.45f).coerceIn(0f, 1f)
            lift = 6f * (1 - k) * (1 - k)
            if (k >= 1f) setPhase(Phase.HANGING)
        }
        bx = pivotX + ROPE * sin(theta)
        by = pivotY - ROPE * cos(theta) - H + lift
        brot = theta
    }

    fun update(dtIn: Float) {
        if (paused) return
        val dt = min(dtIn, 1f / 30f)
        time += dt
        phaseTime += dt

        // Kamera folgt dem Turm
        val wantY = max(topSurface + 1.4f, BASE_TOP + 1.4f)
        camY += (wantY - camY) * min(1f, dt * 3f)
        camX += (topRest() - camX) * min(1f, dt * 2f)
        shake = max(0f, shake - dt * 1.2f)

        if (comboTimer > 0f) {
            comboTimer = max(0f, comboTimer - dt / 7f)
            if (comboTimer == 0f) combo = 0
        }

        if (config.timeLimit > 0f && !finished) {
            timeLeft = max(0f, timeLeft - dt)
            val sec = timeLeft.toInt()
            if (timeLeft < 10f && sec != lastTick) {
                lastTick = sec
                events(this, GameEvent.TICK)
            }
            if (timeLeft <= 0f && phase != Phase.FALLING) {
                setPhase(Phase.OVER)
                events(this, GameEvent.OVER)
            }
        }

        when (phase) {
            Phase.WAIT -> if (phaseTime >= waitFor) {
                val end = endAfterWait
                if (end != null) {
                    endAfterWait = null
                    setPhase(end)
                    events(this, if (end == Phase.WIN) GameEvent.WIN else GameEvent.OVER)
                } else {
                    spawn()
                }
            }
            Phase.LOWERING, Phase.HANGING -> {
                updateHanging(dt)
                if (autoplay && phase == Phase.HANGING && phaseTime > 0.25f) {
                    val dx = predictLandingDx()
                    if (dx != null && abs(dx - autoTarget) < 0.06f) drop()
                }
            }
            Phase.FALLING -> {
                bvy -= GRAVITY * dt
                bx += bvx * dt
                by += bvy * dt
                brot *= exp(-7f * dt)
                if (by <= topSurface) land()
            }
            Phase.ROOFING -> {
                val k = (phaseTime / 0.55f).coerceIn(0f, 1f)
                roofY = topSurface + 5f * (1 - k * k)
                if (k >= 1f) {
                    roofY = topSurface
                    events(this, GameEvent.ROOF)
                    burst(topX(), topSurface + 0.5f, 14)
                    endAfterWait = Phase.WIN
                    waitFor = 1.4f
                    setPhase(Phase.WAIT)
                }
            }
            Phase.OVER, Phase.WIN -> {}
        }

        floors.forEach { if (it.squash > 0f) it.squash = max(0f, it.squash - dt * 4f) }
        updateDebris(dt)
        updateParas(dt)
        updateFx(dt)
    }

    private fun spawn() {
        blockKind = kindFor(floors.size)
        swingT = rnd.nextFloat() * (2 * PI).toFloat()
        // Der Computer trifft meistens perfekt, manchmal bewusst daneben: zeigt Schwanken und Fehlwuerfe
        val r = rnd.nextFloat()
        autoTarget = when {
            r < 0.7f -> 0f
            r < 0.95f -> (if (rnd.nextBoolean()) 1f else -1f) * (0.25f + rnd.nextFloat() * 0.4f)
            else -> 1.4f
        }
        setPhase(Phase.LOWERING)
        updateHanging(0f)
        events(this, GameEvent.SPAWN)
    }

    private fun land() {
        val tx = topX()
        val dx = bx - tx
        val a = abs(dx)
        if (a >= MISS_AT) miss(dx) else place(dx, tx)
    }

    private fun place(dx: Float, tx: Float) {
        val perfect = a(dx) < PERFECT
        val n = floors.size
        if (perfect) {
            perfects++
            combo = if (comboTimer > 0f) combo + 1 else 1
            comboTimer = 1f
            bestCombo = max(bestCombo, combo)
            swayAmp = max(config.wind, swayAmp * 0.7f)
        } else {
            combo = 0
            comboTimer = 0f
            swayAmp = min(1.3f, swayAmp + a(dx) * 0.45f)
        }
        val gain = when {
            perfect -> min(10 + 5 * combo, 60)
            a(dx) < 0.45f -> 8
            else -> 5
        }
        // Das erste Stockwerk sitzt immer mittig auf dem Fundament
        val off = if (perfect || n == 0) 0f else dx
        val floor = Floor(off, blockKind, door = n == 0, residents = gain, perfect = perfect)
        floor.squash = 1f
        floors += floor
        cum += (if (cum.isEmpty()) 0f else cum.last()) + off
        population += gain
        lastGain = gain
        lastPerfect = perfect
        gainSerial++
        shake = if (perfect) 0.08f else 0.16f

        val y = topSurface - H
        val cx = if (perfect) tx else bx
        puffs(cx, y, 6)
        if (perfect) burst(cx, y + 0.6f, 8 + min(combo, 6) * 2)
        spawnParas(min(1 + gain / 10, 5))

        events(this, GameEvent.LAND)
        if (perfect) events(this, GameEvent.PERFECT)

        val target = config.targetFloors
        when {
            target > 0 && floors.size >= target && config.roofAtEnd -> setPhase(Phase.ROOFING)
            target > 0 && floors.size >= target -> {
                endAfterWait = Phase.WIN
                waitFor = 1.2f
                setPhase(Phase.WAIT)
            }
            else -> {
                waitFor = 0.35f
                setPhase(Phase.WAIT)
            }
        }
    }

    private fun a(x: Float) = abs(x)

    private fun miss(dx: Float) {
        val dir = if (dx == 0f) 1f else sign(dx)
        debris += Debris(bx, by, bvx + dir * 1.6f, max(bvy, -2f), brot, dir * -3.2f, blockKind)
        combo = 0
        comboTimer = 0f
        missSerial++
        shake = 0.2f
        puffs(bx - dir * 1f, topSurface, 4)
        events(this, GameEvent.MISS)
        if (config.lives > 0) {
            lives--
            if (lives <= 0) {
                endAfterWait = Phase.OVER
                waitFor = 1.6f
                setPhase(Phase.WAIT)
                return
            }
        }
        waitFor = 0.9f
        setPhase(Phase.WAIT)
    }

    /** Wo landet der Block, wenn er jetzt faellt? Abweichung zur Turmmitte, oder null. */
    fun predictLandingDx(): Float? {
        if (phase != Phase.HANGING) return null
        val theta = swingAmp * sin(swingT)
        val dTheta = swingAmp * cos(swingT) * swingOmega()
        var x = bx
        var y = by
        val vx = ROPE * dTheta * cos(theta) * 0.35f
        var vy = ROPE * dTheta * sin(theta) * 0.35f
        val step = 1f / 240f
        var t = 0f
        while (y > topSurface && t < 3f) {
            vy -= GRAVITY * step
            x += vx * step
            y += vy * step
            t += step
        }
        return x - topX(time + t)
    }

    // ---------------------------------------------------------------- Effekte

    private fun updateDebris(dt: Float) {
        val it = debris.iterator()
        while (it.hasNext()) {
            val d = it.next()
            d.vy -= GRAVITY * dt
            d.x += d.vx * dt
            d.y += d.vy * dt
            d.rot += d.spin * dt
            if (d.y < camY - 14f) {
                it.remove()
                events(this, GameEvent.CRASH)
            }
        }
    }

    private fun spawnParas(count: Int) {
        repeat(count) {
            paras += Para(
                x = camX + (rnd.nextFloat() - 0.5f) * 7f,
                y = camY + 6.5f + rnd.nextFloat() * 3f,
                z = 0.8f + rnd.nextFloat() * 1.6f,
                variant = rnd.nextInt(4),
                phase = rnd.nextFloat() * 6f,
            )
        }
    }

    private fun updateParas(dt: Float) {
        val tx = topX()
        val ty = topSurface + 0.35f
        val it = paras.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.y -= dt * 1.9f
            val dist = p.y - ty
            val pull = min(1f, dt * (if (dist < 3f) 2.5f else 0.6f))
            p.x += (tx - p.x) * pull + sin(time * 2f + p.phase) * dt * 0.4f
            if (dist <= 0f || p.y < camY - 10f) {
                it.remove()
                if (dist <= 0f) {
                    events(this, GameEvent.RESIDENT)
                    fx += Fx(p.x, ty, p.z, 0f, 0.6f, 0.5f, 2, 0.8f, 0f, 0f)
                }
            }
        }
    }

    private fun puffs(x: Float, y: Float, count: Int) {
        repeat(count) { i ->
            val side = if (i % 2 == 0) -1f else 1f
            fx += Fx(
                x + side * (1.05f + rnd.nextFloat() * 0.2f), y + rnd.nextFloat() * 0.2f, 1.1f - rnd.nextFloat() * 2.2f,
                side * (0.6f + rnd.nextFloat() * 0.8f), 0.3f + rnd.nextFloat() * 0.4f,
                0.7f + rnd.nextFloat() * 0.3f, 0, 0.7f + rnd.nextFloat() * 0.4f, rnd.nextFloat() * 6f, rnd.nextFloat() - 0.5f,
            )
        }
    }

    private fun burst(x: Float, y: Float, count: Int) {
        repeat(count) {
            val ang = rnd.nextFloat() * 2 * PI.toFloat()
            val sp = 2f + rnd.nextFloat() * 2.5f
            fx += Fx(
                x, y, 1.4f, cos(ang) * sp, sin(ang) * sp * 0.8f + 1.2f,
                0.8f + rnd.nextFloat() * 0.4f, 1, 0.45f + rnd.nextFloat() * 0.3f, ang, (rnd.nextFloat() - 0.5f) * 8f,
            )
        }
    }

    private fun updateFx(dt: Float) {
        val it = fx.iterator()
        while (it.hasNext()) {
            val f = it.next()
            f.age += dt
            if (f.age >= f.life) {
                it.remove()
                continue
            }
            f.x += f.vx * dt
            f.y += f.vy * dt
            f.rot += f.spin * dt
            if (f.type == 1) f.vy -= 4f * dt else { f.vx *= exp(-2f * dt); f.vy *= exp(-1f * dt) }
        }
    }
}
