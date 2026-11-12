package de.tonyapps.towerbloxx.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import de.tonyapps.towerbloxx.game.Game
import de.tonyapps.towerbloxx.game.Kind
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

/** Nur fuer die Emulator-Pruefung: dort laeuft GL so langsam, dass das Spiel sonst in Zeitlupe laeuft. */
object DebugFlags {
    @Volatile var simSteps = 1
}

/** Was das HUD je Spieler wissen muss; Bildschirmanteile 0..1 innerhalb des eigenen Ausschnitts. */
data class ViewInfo(val markerY: List<Float>)

/**
 * Zeichnet ein oder zwei Spiele nebeneinander. Laeuft komplett im GL-Thread,
 * auch die Spiellogik wird hier fortgeschrieben.
 */
class GameRenderer(
    private val games: List<Game>,
    @Volatile var night: Boolean,
    private val onFrame: (List<ViewInfo>) -> Unit,
) : GLSurfaceView.Renderer {

    private lateinit var lit: Program
    private lateinit var sprite: Program
    private lateinit var sky: Program
    private var quad = 0

    private val blockMeshes = arrayOfNulls<Mesh>(Kind.entries.size)
    private val doorMeshes = arrayOfNulls<Mesh>(Kind.entries.size)
    private fun blockMesh(k: Kind) = blockMeshes[k.ordinal]!!
    private fun doorMesh(k: Kind) = doorMeshes[k.ordinal]!!
    private lateinit var roofMesh: Mesh
    private lateinit var cube: Mesh
    private lateinit var metalCube: Mesh
    private lateinit var foundation: Mesh
    private lateinit var plaza: Mesh
    private lateinit var ground: Mesh

    private val kindTex = IntArray(Kind.entries.size)
    private var miscTex = 0
    private var grassTex = 0
    private val dayTowerTex = IntArray(3)
    private var nightTowerTex = 0
    private var spriteTex = 0

    private var spaceK = 0f
    private var width = 1
    private var height = 1
    private var lastNanos = 0L

    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private val tmp = FloatArray(16)
    private val eye = FloatArray(3)
    private val right = FloatArray(3)
    private val up = FloatArray(3)

    /** style: 0 weisses Raster, 1 Glas, 2 warm; top: 0 flach, 1 Antenne, 2 Spitzdach */
    private class Skyscraper(
        val x: Float, val z: Float, val w: Float, val d: Float, val h: Float, val tint: FloatArray,
        val style: Int, val tiers: Int, val top: Int,
    )
    private class Cloud(val x: Float, val y: Float, val z: Float, val size: Float, val cell: Int, val speed: Float)

    private val city = ArrayList<Skyscraper>()
    private val clouds = ArrayList<Cloud>()
    private val trees = ArrayList<FloatArray>()
    /** x, y, z, Groesse, Zelle, Drehgeschwindigkeit */
    private val space = ArrayList<FloatArray>()

    init {
        val rnd = Random(2024)
        val tints = listOf(
            floatArrayOf(0.82f, 0.9f, 1f), floatArrayOf(0.97f, 0.97f, 0.99f), floatArrayOf(1f, 0.85f, 0.88f),
            floatArrayOf(0.86f, 0.96f, 0.88f), floatArrayOf(1f, 0.92f, 0.78f), floatArrayOf(0.84f, 0.84f, 0.96f),
            floatArrayOf(0.9f, 0.9f, 0.9f), floatArrayOf(0.78f, 0.95f, 0.98f),
        )
        // Hochhaeuser mit Abstand zum Turm, damit Himmel und Tiefe sichtbar bleiben
        repeat(70) {
            val z = -40f - rnd.nextFloat() * 110f
            val spread = 30f + -z * 1.1f
            var x = (rnd.nextFloat() - 0.5f) * 2f * spread
            // Hinter dem Turm eine Gasse frei lassen, damit Himmel und Tiefe zu sehen sind
            val gap = 6f + -z * 0.12f
            if (kotlin.math.abs(x) < gap) x += if (x < 0) -gap else gap
            val w = 5f + rnd.nextFloat() * 6f
            val h = 14f + rnd.nextFloat() * rnd.nextFloat() * 110f
            val style = rnd.nextInt(3)
            city += Skyscraper(
                x, z, w, 5f + rnd.nextFloat() * 6f, h, if (style == 1) floatArrayOf(1f, 1f, 1f) else tints[rnd.nextInt(tints.size)],
                style, 1 + rnd.nextInt(3), rnd.nextInt(3),
            )
        }
        city.sortBy { it.z }
        var y = 6f
        while (y < 1200f) {
            clouds += Cloud(
                (rnd.nextFloat() - 0.5f) * 40f, y, -10f - rnd.nextFloat() * 26f, 5f + rnd.nextFloat() * 6f,
                Art.SPRITE_CLOUD + rnd.nextInt(3), 0.2f + rnd.nextFloat() * 0.5f,
            )
            y += 3f + rnd.nextFloat() * 6f
        }
        clouds.sortBy { it.z }
        var sy = 170f
        while (sy < 1400f) {
            val r = rnd.nextFloat()
            val cell = when {
                r < 0.12f -> Art.SPRITE_PLANET
                r < 0.2f -> Art.SPRITE_MOON
                r < 0.75f -> Art.SPRITE_ASTEROID
                else -> Art.SPRITE_ASTRONAUT
            }
            val far = cell == Art.SPRITE_PLANET || cell == Art.SPRITE_MOON
            space += floatArrayOf(
                (rnd.nextFloat() - 0.5f) * (if (far) 30f else 16f), sy,
                if (far) -30f - rnd.nextFloat() * 20f else -4f - rnd.nextFloat() * 10f,
                if (far) 7f + rnd.nextFloat() * 6f else 1.2f + rnd.nextFloat() * 1.4f,
                cell.toFloat(), (rnd.nextFloat() - 0.5f) * 1.2f,
            )
            sy += 4f + rnd.nextFloat() * 7f
        }
        for (side in listOf(-1f, 1f)) for (k in 0 until 4) {
            trees += floatArrayOf(side * (4.2f + k * 1.9f + rnd.nextFloat()), -3.5f + rnd.nextFloat() * 4f, if (rnd.nextFloat() < 0.3f) 1f else 0f, 2.2f + rnd.nextFloat() * 0.8f)
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        lit = Program(Shaders.LIT_VS, Shaders.LIT_FS)
        sprite = Program(Shaders.SPRITE_VS, Shaders.SPRITE_FS)
        sky = Program(Shaders.SKY_VS, Shaders.SKY_FS)

        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        quad = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        val corners = floatArrayOf(-0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, corners.size * 4, floatBuffer(corners), GLES30.GL_STATIC_DRAW)

        for (k in Kind.entries) {
            blockMeshes[k.ordinal] = block(k, door = false)
            doorMeshes[k.ordinal] = block(k, door = true)
        }
        roofMesh = roof()
        cube = MeshBuilder().apply { box(-0.5f, 0f, -0.5f, 0.5f, 1f, 0.5f, Uv.FULL) }.build()
        metalCube = MeshBuilder().apply { box(-0.5f, 0f, -0.5f, 0.5f, 1f, 0.5f, Art.UV_METAL) }.build()
        foundation = MeshBuilder().apply {
            box(-1.45f, 0f, -1.45f, 1.45f, Game.BASE_TOP, 1.45f, Art.UV_CONCRETE)
        }.build()
        plaza = MeshBuilder().apply { box(-6f, 0f, -6f, 6f, 0.04f, 6f, Art.UV_PAVING) }.build()
        ground = MeshBuilder().apply {
            quad(
                floatArrayOf(-300f, 0f, 300f), floatArrayOf(300f, 0f, 300f),
                floatArrayOf(300f, 0f, -300f), floatArrayOf(-300f, 0f, -300f),
                floatArrayOf(0f, 1f, 0f), Uv(0f, 0f, 1f, 1f),
            )
        }.build()

        for (k in Kind.entries) kindTex[k.ordinal] = Tex.upload(Art.blockAtlas(k))
        miscTex = Tex.upload(Art.miscAtlas())
        grassTex = Tex.upload(Art.grass(), repeat = true)
        for (k in 0 until 3) dayTowerTex[k] = Tex.upload(Art.skyscraper(false, k), repeat = true)
        nightTowerTex = Tex.upload(Art.skyscraper(true, 0), repeat = true)
        spriteTex = Tex.upload(Art.sprites())

        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glFrontFace(GLES30.GL_CCW)
        GLES30.glCullFace(GLES30.GL_BACK)
        lastNanos = 0L
    }

    /**
     * Ein Stockwerk: Koerper mit Fassade, Ecksaeulen, Gesims oben, Sockel unten
     * und echte 3D-Vorspruenge fuer Fensterrahmen, Simse, Blumenkaesten und Erker.
     */
    private fun block(kind: Kind, door: Boolean): Mesh = MeshBuilder().apply {
        val face = if (door) Art.UV_DOOR else Art.UV_FACADE
        box(-1f, 0.05f, -1f, 1f, 0.86f, 1f, face, Art.UV_TOP, Art.UV_STONE)
        // Gesims oben mit Ueberstand, Sockelleiste unten
        box(-1.1f, 0.86f, -1.1f, 1.1f, 1f, 1.1f, Art.UV_STONE, Art.UV_TOP, Art.UV_STONE)
        box(-1.05f, 0f, -1.05f, 1.05f, 0.06f, 1.05f, Art.UV_STONE, Art.UV_STONE, Art.UV_STONE)
        // Ecksaeulen machen die Kanten rund und kraeftig
        for (sx in listOf(-1f, 1f)) for (sz in listOf(-1f, 1f)) {
            val x0 = if (sx < 0) -1.05f else 0.89f
            val z0 = if (sz < 0) -1.05f else 0.89f
            box(x0, 0.06f, z0, x0 + 0.16f, 0.86f, z0 + 0.16f, Art.UV_STONE, Art.UV_STONE, Art.UV_STONE)
        }
        val parts = if (door) doorParts(kind) else windowParts(kind)
        // Vier Seiten: Normale und Richtung "nach rechts", von aussen gesehen
        val sides = listOf(
            floatArrayOf(0f, 0f, 1f, 1f, 0f, 0f),
            floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f),
            floatArrayOf(0f, 0f, -1f, -1f, 0f, 0f),
            floatArrayOf(-1f, 0f, 0f, 0f, 0f, 1f),
        )
        for (sd in sides) for (pr in parts) protrude(this, sd, face, pr)
    }.build()

    /** Vorsprung ueber einem Fassadenbereich in Pixeln (x0, y0, x1, y1, Tiefe). */
    private fun protrude(mb: MeshBuilder, sd: FloatArray, face: Uv, r: FloatArray) {
        val a0 = -1f + 2f * r[0] / 1024f
        val a1 = -1f + 2f * r[2] / 1024f
        val y1 = 0.05f + (1f - r[1] / 512f) * 0.81f
        val y0 = 0.05f + (1f - r[3] / 512f) * 0.81f
        val d = r[4]
        fun p(a: Float, y: Float, dd: Float) = floatArrayOf(sd[3] * a + sd[0] * (1f + dd), y, sd[5] * a + sd[2] * (1f + dd))
        val n = floatArrayOf(sd[0], sd[1], sd[2])
        val t = floatArrayOf(sd[3], sd[4], sd[5])
        val uv = Uv(
            face.u0 + (face.u1 - face.u0) * r[0] / 1024f, face.v0 + (face.v1 - face.v0) * r[1] / 512f,
            face.u0 + (face.u1 - face.u0) * r[2] / 1024f, face.v0 + (face.v1 - face.v0) * r[3] / 512f,
        )
        val st = Art.UV_STONE
        mb.quad(p(a0, y0, d), p(a1, y0, d), p(a1, y1, d), p(a0, y1, d), n, uv)
        mb.quad(p(a0, y1, d), p(a1, y1, d), p(a1, y1, 0f), p(a0, y1, 0f), floatArrayOf(0f, 1f, 0f), st)
        mb.quad(p(a0, y0, 0f), p(a1, y0, 0f), p(a1, y0, d), p(a0, y0, d), floatArrayOf(0f, -1f, 0f), st)
        mb.quad(p(a1, y0, d), p(a1, y0, 0f), p(a1, y1, 0f), p(a1, y1, d), t, st)
        mb.quad(p(a0, y0, 0f), p(a0, y0, d), p(a0, y1, d), p(a0, y1, 0f), floatArrayOf(-t[0], -t[1], -t[2]), st)
    }

    /** Lage der Fenster auf der Fassadentextur, passend zu Art.facade. */
    private fun windowParts(kind: Kind): List<FloatArray> = when (kind) {
        Kind.GREEN -> listOf(
            floatArrayOf(88f, 82f, 936f, 422f, 0.17f), // grosser Glaserker
            floatArrayOf(80f, 422f, 944f, 444f, 0.22f), // Sims darunter
        )
        Kind.BLUE -> listOf(
            floatArrayOf(149f, 100f, 451f, 380f, 0.06f), floatArrayOf(573f, 100f, 875f, 380f, 0.06f),
            floatArrayOf(87f, 114f, 147f, 378f, 0.035f), floatArrayOf(453f, 114f, 511f, 378f, 0.035f),
            floatArrayOf(513f, 114f, 571f, 378f, 0.035f), floatArrayOf(877f, 114f, 937f, 378f, 0.035f),
            floatArrayOf(147f, 380f, 453f, 404f, 0.11f), floatArrayOf(571f, 380f, 877f, 404f, 0.11f),
        )
        else -> listOf(
            // Simse mit Blumenkaesten unter den Bogenfenstern, Schlusssteine oben
            floatArrayOf(153f, 400f, 447f, 424f, 0.11f), floatArrayOf(577f, 400f, 871f, 424f, 0.11f),
            floatArrayOf(181f, 368f, 419f, 402f, 0.15f), floatArrayOf(605f, 368f, 843f, 402f, 0.15f),
            floatArrayOf(278f, 84f, 322f, 120f, 0.05f), floatArrayOf(702f, 84f, 746f, 120f, 0.05f),
        )
    }

    private fun doorParts(kind: Kind): List<FloatArray> = buildList {
        add(floatArrayOf(361f, 124f, 663f, 470f, 0.05f)) // Tuerrahmen
        if (kind == Kind.BLUE || kind == Kind.GREEN) add(floatArrayOf(300f, 118f, 724f, 192f, 0.3f)) // Vordach
        add(floatArrayOf(299f, 228f, 335f, 288f, 0.08f)) // Laternen
        add(floatArrayOf(689f, 228f, 725f, 288f, 0.08f))
        for (cx in listOf(190f, 834f)) add(floatArrayOf(cx - 100f, 368f, cx + 100f, 392f, 0.08f)) // kleine Simse
    }

    private fun roof(): Mesh = MeshBuilder().apply {
        val r = 1.15f
        val hgt = 1.0f
        val apex = floatArrayOf(0f, hgt, 0f)
        val c = listOf(floatArrayOf(-r, 0f, r), floatArrayOf(r, 0f, r), floatArrayOf(r, 0f, -r), floatArrayOf(-r, 0f, -r))
        val uv = Art.UV_ROOF
        val ua = floatArrayOf((uv.u0 + uv.u1) / 2, uv.v0)
        val ub = floatArrayOf(uv.u0, uv.v1)
        val uc = floatArrayOf(uv.u1, uv.v1)
        for (k in 0 until 4) tri(c[k], c[(k + 1) % 4], apex, ub, uc, ua)
        // Schornstein
        box(0.35f, 0.3f, -0.45f, 0.65f, 1.2f, -0.15f, Art.UV_CONCRETE, Art.UV_METAL, Art.UV_CONCRETE)
        box(-1.15f, -0.08f, -1.15f, 1.15f, 0f, 1.15f, Art.UV_CONCRETE)
    }.build()

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w
        height = h
    }

    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1e9f)
        lastNanos = now
        repeat(DebugFlags.simSteps) { games.forEach { it.update(dt) } }

        GLES30.glViewport(0, 0, width, height)
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        val n = games.size
        val infos = ArrayList<ViewInfo>(n)
        val gap = if (n > 1) 4 else 0
        val vw = (width - gap * (n - 1)) / n
        for ((i, g) in games.withIndex()) {
            val x0 = i * (vw + gap)
            GLES30.glViewport(x0, 0, vw, height)
            GLES30.glEnable(GLES30.GL_SCISSOR_TEST)
            GLES30.glScissor(x0, 0, vw, height)
            infos += drawGame(g, vw, height)
            GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
        }
        onFrame(infos)
    }

    // ------------------------------------------------------------ Szene

    private fun drawGame(g: Game, vw: Int, vh: Int): ViewInfo {
        val aspect = vw.toFloat() / vh
        val fov = 50f
        val halfTan = tan(Math.toRadians(fov / 2.0)).toFloat()
        // Schmale Ausschnitte (Party) brauchen mehr Abstand, damit der Kran ins Bild passt
        val dist = max(11f, 2.35f / (halfTan * aspect))
        val shakeX = if (g.shake > 0f) sin(g.time * 70f) * g.shake * 0.4f else 0f
        val shakeY = if (g.shake > 0f) cos(g.time * 63f) * g.shake * 0.3f else 0f
        val tx = g.camX + shakeX
        // Im Querformat ist der Ausschnitt flacher: Blick etwas hoeher, damit der Kranblock sichtbar bleibt
        val ty = g.camY + shakeY + if (aspect > 0.8f) 1.3f else 0f
        val ang = 0.34f
        eye[0] = tx + dist * sin(ang)
        eye[1] = ty + 2.6f
        eye[2] = dist * cos(ang)
        Matrix.perspectiveM(proj, 0, fov, aspect, 0.5f, 420f)
        Matrix.setLookAtM(view, 0, eye[0], eye[1], eye[2], tx, ty + 0.4f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        // Kamera-Achsen fuer Billboards
        right[0] = view[0]; right[1] = view[4]; right[2] = view[8]
        up[0] = view[1]; up[1] = view[5]; up[2] = view[9]

        val altitude = (g.camY / 160f).coerceIn(0f, 1f)
        // Ab etwa 150 Stockwerken geht es in den Weltraum
        spaceK = smooth((g.camY - 140f) / 110f)
        var skyTop: FloatArray
        var skyBottom: FloatArray
        if (night) {
            skyTop = floatArrayOf(0.02f, 0.03f, 0.1f)
            skyBottom = mixc(floatArrayOf(0.16f, 0.17f, 0.36f), floatArrayOf(0.06f, 0.08f, 0.22f), altitude)
        } else {
            skyTop = mixc(floatArrayOf(0.3f, 0.6f, 0.95f), floatArrayOf(0.14f, 0.38f, 0.86f), altitude)
            skyBottom = mixc(floatArrayOf(0.8f, 0.92f, 1f), floatArrayOf(0.55f, 0.78f, 1f), altitude)
        }
        skyTop = mixc(skyTop, floatArrayOf(0.01f, 0.01f, 0.05f), spaceK)
        skyBottom = mixc(skyBottom, floatArrayOf(0.05f, 0.08f, 0.26f), spaceK)
        val fogColor = mixc(skyBottom, skyTop, 0.25f)
        drawSky(skyTop, skyBottom, g, aspect)

        // Licht
        lit.use()
        GLES30.glUniformMatrix4fv(lit.u("uVp"), 1, false, vp, 0)
        val l = norm(floatArrayOf(-0.35f, 0.75f, 0.6f))
        GLES30.glUniform3f(lit.u("uLightDir"), l[0], l[1], l[2])
        if (night) {
            GLES30.glUniform3f(lit.u("uLightColor"), 0.55f, 0.55f, 0.7f)
            GLES30.glUniform3f(lit.u("uAmbient"), 0.38f, 0.4f, 0.58f)
        } else {
            GLES30.glUniform3f(lit.u("uLightColor"), 0.62f, 0.58f, 0.5f)
            GLES30.glUniform3f(lit.u("uAmbient"), 0.6f, 0.64f, 0.72f)
        }
        GLES30.glUniform3f(lit.u("uFogColor"), fogColor[0], fogColor[1], fogColor[2])
        GLES30.glUniform3f(lit.u("uEye"), eye[0], eye[1], eye[2])
        GLES30.glUniform2f(lit.u("uFog"), 30f, 190f)
        GLES30.glUniform1i(lit.u("uTex"), 0)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glDisable(GLES30.GL_BLEND)

        val viewBottom = g.camY - 10f

        // Stadt im Hintergrund: abgestufte Hochhaeuser mit Antennen und Spitzdaechern
        for (s in city) {
            if (s.h < viewBottom - 20f) continue
            val bx = s.x + g.camX * 0.3f
            var y = 0f
            val shares = when (s.tiers) { 1 -> floatArrayOf(1f); 2 -> floatArrayOf(0.72f, 0.28f); else -> floatArrayOf(0.6f, 0.25f, 0.15f) }
            var w = s.w
            var d = s.d
            setUnlit(if (night) 0.75f else 0f)
            bind(if (night) nightTowerTex else dayTowerTex[s.style])
            tint(s.tint[0], s.tint[1], s.tint[2])
            for (share in shares) {
                val h = s.h * share
                Matrix.setIdentityM(model, 0)
                Matrix.translateM(model, 0, bx, y, s.z)
                Matrix.scaleM(model, 0, w, h, d)
                uvScale(w / 4f, h / 8f)
                drawMesh(cube)
                y += h
                w *= 0.74f
                d *= 0.74f
            }
            setUnlit(0f)
            uvScale(1f, 1f)
            when (s.top) {
                1 -> {
                    bind(miscTex)
                    tint(0.85f, 0.87f, 0.92f)
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, bx, y, s.z)
                    Matrix.scaleM(model, 0, w * 0.12f, s.h * 0.18f, w * 0.12f)
                    drawMesh(metalCube)
                }
                2 -> {
                    bind(miscTex)
                    tint(1f, 1f, 1f)
                    Matrix.setIdentityM(model, 0)
                    Matrix.translateM(model, 0, bx, y + 0.08f * w / 2.3f, s.z)
                    Matrix.scaleM(model, 0, w / 2.3f, w * 0.9f, d / 2.3f)
                    drawMesh(roofMesh)
                }
            }
        }
        setUnlit(0f)
        uvScale(1f, 1f)
        tint(1f, 1f, 1f)

        // Boden
        if (viewBottom < 20f) {
            bind(grassTex)
            uvScale(60f, 60f)
            Matrix.setIdentityM(model, 0)
            drawMesh(ground)
            uvScale(1f, 1f)
            bind(miscTex)
            Matrix.setIdentityM(model, 0)
            drawMesh(plaza)
            drawMesh(foundation)
        }

        // Turm
        for (i in g.floors.indices) {
            val y = g.floorY(i)
            if (y < viewBottom - 2f || y > g.camY + 14f) continue
            val f = g.floors[i]
            bind(kindTex[f.kind.ordinal])
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, g.floorX(i), y, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(g.floorTilt(i).toDouble()).toFloat(), 0f, 0f, 1f)
            if (f.squash > 0f) {
                val s = 1f - 0.12f * f.squash * sin(f.squash * PI.toFloat())
                Matrix.scaleM(model, 0, 1f + (1f - s) * 0.5f, s, 1f + (1f - s) * 0.5f)
            }
            drawMesh(if (f.door) doorMesh(f.kind) else blockMesh(f.kind))
        }

        // Dach
        if (!g.roofY.isNaN() && g.floors.isNotEmpty()) {
            bind(miscTex)
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, g.floorX(g.floors.size - 1), g.roofY, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(g.floorTilt(g.floors.size - 1).toDouble()).toFloat(), 0f, 0f, 1f)
            drawMesh(roofMesh)
        }

        // Haengender oder fallender Block mit Seil
        if (g.hasBlock) {
            bind(kindTex[g.blockKind.ordinal])
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, g.bx, g.by, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(g.brot.toDouble()).toFloat(), 0f, 0f, 1f)
            drawMesh(if (g.floors.isEmpty()) doorMesh(g.blockKind) else blockMesh(g.blockKind))
            if (g.phase != Game.Phase.FALLING) drawRope(g)
        }

        // Abstuerzende Bloecke
        for (d in g.debris) {
            bind(kindTex[d.kind.ordinal])
            Matrix.setIdentityM(model, 0)
            Matrix.translateM(model, 0, d.x, d.y + 0.5f, 0f)
            Matrix.rotateM(model, 0, Math.toDegrees(d.rot.toDouble()).toFloat(), 0f, 0f, 1f)
            Matrix.translateM(model, 0, 0f, -0.5f, 0f)
            drawMesh(blockMesh(d.kind))
        }

        drawSprites(g, fogColor)

        // Bildschirmhoehe der Marken fuers HUD
        val marks = g.markers.map { (_, floors) -> screenFrac(g.camX, Game.BASE_TOP + floors * Game.H) }
        return ViewInfo(marks)
    }

    private fun drawRope(g: Game) {
        bind(miscTex)
        // Haken am Block
        val ax = g.bx - sin(g.brot) * 1.0f
        val ay = g.by + cos(g.brot) * 1.0f
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, ax, ay, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(g.brot.toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, 0.42f, 0.22f, 0.42f)
        tint(1f, 0.78f, 0.15f)
        drawMesh(metalCube)
        // Seil bis ueber den Bildrand
        val dx = g.pivotX - ax
        val dy = g.pivotY - ay
        val len = sqrt(dx * dx + dy * dy)
        Matrix.setIdentityM(model, 0)
        Matrix.translateM(model, 0, ax, ay + 0.2f, 0f)
        Matrix.rotateM(model, 0, Math.toDegrees(kotlin.math.atan2(-dx, dy).toDouble()).toFloat(), 0f, 0f, 1f)
        Matrix.scaleM(model, 0, 0.06f, len, 0.06f)
        tint(0.25f, 0.26f, 0.3f)
        drawMesh(metalCube)
        tint(1f, 1f, 1f)
    }

    private fun drawSprites(g: Game, fogColor: FloatArray) {
        sprite.use()
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        GLES30.glUniformMatrix4fv(sprite.u("uVp"), 1, false, vp, 0)
        GLES30.glUniform3f(sprite.u("uRight"), right[0], right[1], right[2])
        GLES30.glUniform3f(sprite.u("uUp"), up[0], up[1], up[2])
        GLES30.glUniform3f(sprite.u("uFogColor"), fogColor[0], fogColor[1], fogColor[2])
        GLES30.glUniform3f(sprite.u("uEye"), eye[0], eye[1], eye[2])
        GLES30.glUniform2f(sprite.u("uFog"), 25f, 150f)
        GLES30.glUniform1i(sprite.u("uTex"), 0)
        bind(spriteTex)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)

        val top = g.camY + 12f
        val bottom = g.camY - 12f
        val cloudAlpha = (if (night) 0.35f else 1f) * (1f - spaceK)
        for (c in clouds) {
            if (c.y < bottom - 5f || c.y > top + 5f) continue
            var x = c.x + (g.time * c.speed) % 60f
            if (x > 30f) x -= 60f
            spr(c.cell, x + g.camX * 0.4f, c.y, c.z, c.size, c.size, 0f, cloudAlpha)
        }
        // Heissluftballon und Voegel als Leben im Himmel
        val bx = ((g.time * 0.6f) % 70f) - 35f
        spr(Art.SPRITE_BALLOON, bx + g.camX * 0.5f, 34f + sin(g.time * 0.3f) * 1.5f, -18f, 3.2f, 3.2f, 0f, if (night) 0.5f else 1f)
        spr(Art.SPRITE_BALLOON, -bx * 0.8f + g.camX * 0.5f, 120f, -22f, 3.2f, 3.2f, 0f, if (night) 0.5f else 1f)
        if (spaceK > 0f) {
            for (o in space) {
                if (o[1] < bottom - 8f || o[1] > top + 8f) continue
                val drift = sin(g.time * 0.2f + o[1]) * 0.6f
                spr(o[4].toInt(), o[0] + drift + g.camX * 0.6f, o[1], o[2], o[3], o[3], g.time * o[5], spaceK)
            }
        }
        if (!night && g.camY < 50f) for (k in 0 until 3) {
            val t = g.time * 1.6f + k * 1.7f
            val x = ((t * 1.3f) % 40f) - 20f + g.camX
            spr(Art.SPRITE_BIRD, x, 12f + k * 2.5f + sin(t * 3f) * 0.3f, -8f, 0.9f, 0.6f + 0.2f * sin(t * 12f), 0f, 0.9f)
        }

        if (g.camY - 10f < 20f) {
            // Baustelle: Zaun hinten, Mast, Arbeiter und Pylonen vorne
            for (k in -3..3) {
                if (k == 0) continue
                spr(Art.SPRITE_FENCE, k * 2.1f, 0.95f, -3.2f, 2.2f, 2.2f, 0f, 1f)
            }
            spr(Art.SPRITE_POLE, -6.5f, 3.2f, -1.5f, 6.4f, 6.4f, 0f, 1f)
            spr(Art.SPRITE_WORKER, -2.6f, 0.7f, 2.4f, 1.4f, 1.4f, sin(g.time * 3f) * 0.05f, 1f)
            spr(Art.SPRITE_WORKER + 1, 2.8f, 0.7f, 1.8f, 1.4f, 1.4f, sin(g.time * 2.6f + 1f) * 0.05f, 1f)
            spr(Art.SPRITE_CONE, -1.7f, 0.4f, 3.0f, 0.8f, 0.8f, 0f, 1f)
            spr(Art.SPRITE_CONE, 1.8f, 0.4f, 3.1f, 0.8f, 0.8f, 0f, 1f)
            for (t in trees) {
                spr(Art.SPRITE_SHADOW, t[0] + 0.3f, 0.06f, t[1], t[3] * 0.9f, t[3] * 0.35f, 0f, 0.8f)
                spr(if (t[2] > 0f) Art.SPRITE_PINE else Art.SPRITE_TREE, t[0], t[3] / 2f, t[1], t[3], t[3], 0f, 1f)
            }
        }
        for (p in g.paras) {
            val sway = sin(g.time * 2.4f + p.z * 3f) * 0.15f
            spr(Art.SPRITE_PARA + p.variant, p.x, p.y, p.z, 1.15f, 1.15f, sway, 1f)
        }
        for (f in g.fx) {
            val k = f.age / f.life
            when (f.type) {
                0 -> spr(Art.SPRITE_PUFF, f.x, f.y, f.z, f.size * (1f + k), f.size * (1f + k), f.rot, (1f - k) * 0.9f)
                1 -> spr(Art.SPRITE_STAR, f.x, f.y, f.z, f.size, f.size, f.rot, if (k < 0.7f) 1f else (1f - k) / 0.3f)
                else -> spr(Art.SPRITE_SPARKLE, f.x, f.y, f.z, f.size * (0.6f + k), f.size * (0.6f + k), 0f, 1f - k)
            }
        }
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun spr(cell: Int, x: Float, y: Float, z: Float, w: Float, h: Float, rot: Float, a: Float) {
        if (a <= 0f) return
        val uv = Art.spriteUv(cell)
        GLES30.glUniform4f(sprite.u("uRect"), uv[0], uv[1], uv[2], uv[3])
        GLES30.glUniform3f(sprite.u("uCenter"), x, y, z)
        GLES30.glUniform2f(sprite.u("uSize"), w, h)
        GLES30.glUniform1f(sprite.u("uRot"), rot)
        GLES30.glUniform4f(sprite.u("uTint"), 1f, 1f, 1f, a)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
    }

    private fun drawSky(top: FloatArray, bottom: FloatArray, g: Game, aspect: Float) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glDisable(GLES30.GL_CULL_FACE)
        sky.use()
        GLES30.glUniform3f(sky.u("uTop"), top[0], top[1], top[2])
        GLES30.glUniform3f(sky.u("uBottom"), bottom[0], bottom[1], bottom[2])
        GLES30.glUniform1f(sky.u("uStars"), max(if (night) 1f else 0f, spaceK))
        GLES30.glUniform1f(sky.u("uScroll"), g.camY * 0.01f)
        GLES30.glUniform2f(sky.u("uAspect"), aspect, 1f)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, quad)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glDisableVertexAttribArray(1)
        GLES30.glDisableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 8, 0)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 6)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    // ------------------------------------------------------------ Hilfen

    private fun drawMesh(mesh: Mesh) {
        GLES30.glUniformMatrix4fv(lit.u("uModel"), 1, false, model, 0)
        mesh.draw()
    }

    private fun bind(tex: Int) = GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex)
    private fun tint(r: Float, g: Float, b: Float) = GLES30.glUniform4f(lit.u("uTint"), r, g, b, 1f)
    private fun uvScale(u: Float, v: Float) = GLES30.glUniform2f(lit.u("uUvScale"), u, v)
    private fun setUnlit(v: Float) = GLES30.glUniform1f(lit.u("uUnlit"), v)

    private fun screenFrac(x: Float, y: Float): Float {
        val p = floatArrayOf(x, y, 0f, 1f)
        val r = FloatArray(4)
        Matrix.multiplyMV(r, 0, vp, 0, p, 0)
        if (abs(r[3]) < 1e-5f) return -1f
        val ndcY = r[1] / r[3]
        return (1f - (ndcY * 0.5f + 0.5f))
    }

    private fun mixc(a: FloatArray, b: FloatArray, t: Float) =
        floatArrayOf(a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t)

    private fun norm(v: FloatArray): FloatArray {
        val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
        return floatArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }

    private fun smooth(x: Float): Float {
        val t = min(1f, max(0f, x))
        return t * t * (3f - 2f * t)
    }
}
