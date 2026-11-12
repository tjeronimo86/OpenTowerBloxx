package de.tonyapps.towerbloxx.gl

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import de.tonyapps.towerbloxx.game.Kind
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Alle Texturen werden zur Laufzeit gezeichnet: keine fremden Grafiken, scharf auf jeder Aufloesung.
 * Stil: Cartoon mit weichen Verlaeufen, dunklen Konturen und Glanzlichtern.
 */
object Art {
    // ------------------------------------------------------------ Farben

    fun mix(a: Int, b: Int, t: Float): Int {
        val u = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * u).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * u).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * u).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * u).toInt(),
        )
    }

    fun alpha(c: Int, a: Float): Int = Color.argb((a.coerceIn(0f, 1f) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))
    private fun light(c: Int, t: Float) = mix(c, Color.WHITE, t)
    private fun dark(c: Int, t: Float) = mix(c, Color.BLACK, t)

    private const val OUTLINE = 0xFF2A2638.toInt()

    class Palette(
        val wall: Int, val wallDark: Int, val trim: Int, val trimDark: Int,
        val frame: Int, val glassTop: Int, val glassBottom: Int, val accent: Int,
    )

    fun palette(kind: Kind) = when (kind) {
        Kind.BLUE -> Palette(
            0xFF5AB4EF.toInt(), 0xFF2F7DC6.toInt(), 0xFFF0F8FF.toInt(), 0xFFA8C8E8.toInt(),
            0xFF1F3B63.toInt(), 0xFFD9F7FF.toInt(), 0xFF3B93D6.toInt(), 0xFFFFC531.toInt(),
        )
        Kind.RED -> Palette(
            0xFFE6693F.toInt(), 0xFFB5432A.toInt(), 0xFFF8E0B4.toInt(), 0xFFD3AE78.toInt(),
            0xFFFFFFFF.toInt(), 0xFFC6F1FF.toInt(), 0xFF3778C6.toInt(), 0xFF5DB85F.toInt(),
        )
        Kind.GREEN -> Palette(
            0xFF55C963.toInt(), 0xFF2E9140.toInt(), 0xFFEEFFE6.toInt(), 0xFFA3D69C.toInt(),
            0xFF1D5C35.toInt(), 0xFFC4FFF2.toInt(), 0xFF17A985.toInt(), 0xFFFF8A3D.toInt(),
        )
        Kind.YELLOW -> Palette(
            0xFFF5CC55.toInt(), 0xFFD0982A.toInt(), 0xFFFFF7DA.toInt(), 0xFFE2C688.toInt(),
            0xFF8A4B1C.toInt(), 0xFFDDF4FF.toInt(), 0xFF4A8BD0.toInt(), 0xFFE84A5F.toInt(),
        )
    }

    private fun paint() = Paint(Paint.ANTI_ALIAS_FLAG)

    private fun fill(color: Int) = paint().apply { this.color = color; style = Paint.Style.FILL }

    private fun stroke(color: Int, w: Float) = paint().apply {
        this.color = color
        style = Paint.Style.STROKE
        strokeWidth = w
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private fun vGrad(y0: Float, y1: Float, c0: Int, c1: Int) = paint().apply {
        shader = LinearGradient(0f, y0, 0f, y1, c0, c1, Shader.TileMode.CLAMP)
    }

    private fun hGrad(x0: Float, x1: Float, c0: Int, c1: Int) = paint().apply {
        shader = LinearGradient(x0, 0f, x1, 0f, c0, c1, Shader.TileMode.CLAMP)
    }

    // ------------------------------------------------------------ Gebaeudeatlas

    /** Atlas 1024 x 1536: Fassade, Fassade mit Tuer, Dachflaeche, Gesims. */
    const val ATLAS_W = 1024
    const val ATLAS_H = 1536
    val UV_FACADE = Uv.px(0, 0, 1024, 512, ATLAS_W, ATLAS_H)
    val UV_DOOR = Uv.px(0, 512, 1024, 1024, ATLAS_W, ATLAS_H)
    val UV_TOP = Uv.px(0, 1024, 512, 1536, ATLAS_W, ATLAS_H)
    val UV_STONE = Uv.px(512, 1024, 1024, 1536, ATLAS_W, ATLAS_H)

    fun blockAtlas(kind: Kind): Bitmap {
        val bmp = Bitmap.createBitmap(ATLAS_W, ATLAS_H, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = palette(kind)
        val rnd = Random(kind.ordinal * 31 + 7)
        c.save(); c.clipRect(0, 0, 1024, 512); facade(c, p, kind, rnd, door = false); c.restore()
        c.save(); c.translate(0f, 512f); c.clipRect(0, 0, 1024, 512); facade(c, p, kind, rnd, door = true); c.restore()
        c.save(); c.translate(0f, 1024f); c.clipRect(0, 0, 512, 512); roofDeck(c, p, rnd); c.restore()
        c.save(); c.translate(512f, 1024f); c.clipRect(0, 0, 512, 512); cornice(c, p); c.restore()
        return bmp
    }

    private fun facade(c: Canvas, p: Palette, kind: Kind, rnd: Random, door: Boolean) {
        val w = 1024f
        val h = 512f
        c.drawRect(0f, 0f, w, h, vGrad(0f, h, light(p.wall, 0.14f), mix(p.wall, p.wallDark, 0.35f)))

        when (kind) {
            Kind.RED -> bricks(c, p, rnd)
            Kind.BLUE -> {
                var y = 60f
                while (y < 470f) {
                    c.drawRect(0f, y, w, y + 4f, fill(alpha(p.wallDark, 0.45f)))
                    c.drawRect(0f, y + 4f, w, y + 7f, fill(alpha(Color.WHITE, 0.18f)))
                    y += 30f
                }
            }
            Kind.GREEN -> {
                var x = 64f
                while (x < w) {
                    c.drawRect(x, 0f, x + 5f, h, fill(alpha(p.wallDark, 0.4f)))
                    c.drawRect(x + 5f, 0f, x + 8f, h, fill(alpha(Color.WHITE, 0.2f)))
                    x += 128f
                }
            }
            Kind.YELLOW -> repeat(2600) {
                val x = rnd.nextFloat() * w
                val y = rnd.nextFloat() * h
                val dot = if (rnd.nextBoolean()) alpha(Color.WHITE, 0.16f) else alpha(p.wallDark, 0.18f)
                c.drawCircle(x, y, 1.5f + rnd.nextFloat() * 2.5f, fill(dot))
            }
        }

        // Ecklisenen geben der Kante Tiefe
        pilaster(c, p, kind, 0f, 58f, true)
        pilaster(c, p, kind, w - 58f, w, false)

        if (door) {
            smallWindow(c, p, kind, rnd, 190f)
            smallWindow(c, p, kind, rnd, w - 190f)
            entrance(c, p, kind)
        } else {
            when (kind) {
                Kind.GREEN -> bayWindow(c, p, rnd)
                Kind.BLUE -> {
                    rectWindow(c, p, rnd, 300f, 262f, 120f, 372f, shutters = true)
                    rectWindow(c, p, rnd, w - 300f, 262f, 120f, 372f, shutters = true)
                }
                else -> {
                    archWindow(c, p, rnd, 300f, 250f, 108f, 392f, flowers = rnd.nextFloat() < 0.8f)
                    archWindow(c, p, rnd, w - 300f, 250f, 108f, 392f, flowers = rnd.nextFloat() < 0.6f)
                }
            }
        }

        // Fries oben mit Zahnschnitt, Sockel unten
        c.drawRect(0f, 0f, w, 36f, vGrad(0f, 36f, light(p.trim, 0.2f), p.trim))
        c.drawRect(0f, 36f, w, 40f, fill(dark(p.trimDark, 0.15f)))
        var x = 8f
        while (x < w) {
            c.drawRect(x, 40f, x + 20f, 56f, fill(p.trimDark))
            c.drawRect(x, 52f, x + 20f, 56f, fill(dark(p.trimDark, 0.25f)))
            x += 34f
        }
        c.drawRect(0f, 56f, w, 92f, vGrad(56f, 92f, alpha(Color.BLACK, 0.28f), alpha(Color.BLACK, 0f)))
        c.drawRect(0f, 466f, w, 470f, fill(alpha(Color.WHITE, 0.5f)))
        c.drawRect(0f, 470f, w, h, vGrad(470f, h, p.trimDark, dark(p.trimDark, 0.3f)))
        c.drawRect(0f, 430f, w, 466f, vGrad(430f, 466f, alpha(Color.BLACK, 0f), alpha(Color.BLACK, 0.18f)))
        // Kanten etwas dunkler: wirkt wie Umgebungsverdeckung
        c.drawRect(0f, 0f, 26f, h, hGrad(0f, 26f, alpha(Color.BLACK, 0.25f), alpha(Color.BLACK, 0f)))
        c.drawRect(w - 26f, 0f, w, h, hGrad(w - 26f, w, alpha(Color.BLACK, 0f), alpha(Color.BLACK, 0.25f)))
    }

    private fun bricks(c: Canvas, p: Palette, rnd: Random) {
        val rowH = 28f
        var row = 0
        var y = 0f
        while (y < 512f) {
            var x = if (row % 2 == 0) 0f else -38f
            while (x < 1024f) {
                val tone = mix(p.wall, if (rnd.nextBoolean()) p.wallDark else Color.WHITE, rnd.nextFloat() * 0.18f)
                val r = RectF(x + 3f, y + 3f, x + 73f, y + rowH - 1f)
                c.drawRoundRect(r, 4f, 4f, fill(tone))
                c.drawRect(r.left, r.top, r.right, r.top + 4f, fill(alpha(Color.WHITE, 0.16f)))
                c.drawRect(r.left, r.bottom - 4f, r.right, r.bottom, fill(alpha(Color.BLACK, 0.12f)))
                x += 76f
            }
            y += rowH
            row++
        }
    }

    private fun pilaster(c: Canvas, p: Palette, kind: Kind, x0: Float, x1: Float, left: Boolean) {
        val shade = if (left) hGrad(x0, x1, light(p.trim, 0.1f), p.trimDark) else hGrad(x0, x1, p.trimDark, dark(p.trimDark, 0.12f))
        c.drawRect(x0, 0f, x1, 512f, shade)
        if (kind == Kind.RED || kind == Kind.YELLOW) {
            var y = 60f
            var i = 0
            while (y < 470f) {
                val wide = i % 2 == 0
                val l = if (left) x0 else if (wide) x0 - 18f else x0
                val r = if (left) (if (wide) x1 + 18f else x1) else x1
                c.drawRoundRect(RectF(l, y + 3f, r, y + 46f), 6f, 6f, fill(if (wide) light(p.trim, 0.05f) else p.trimDark))
                c.drawRect(l, y + 42f, r, y + 46f, fill(alpha(Color.BLACK, 0.15f)))
                y += 52f
                i++
            }
        }
        val edge = if (left) x1 else x0
        c.drawRect(edge - 2f, 0f, edge + 2f, 512f, fill(alpha(OUTLINE, 0.35f)))
    }

    private fun glass(c: Canvas, p: Palette, path: Path, top: Float, bottom: Float, rnd: Random) {
        c.drawPath(path, vGrad(top, bottom, p.glassTop, p.glassBottom))
        c.save()
        c.clipPath(path)
        // Vorhaenge in manchen Fenstern
        if (rnd.nextFloat() < 0.45f) {
            val curtain = listOf(0xFFFF8FB1.toInt(), 0xFFFFB347.toInt(), 0xFFB39DFF.toInt(), 0xFFFFFFFF.toInt(), 0xFF7FE0C7.toInt())[rnd.nextInt(5)]
            val b = RectF()
            path.computeBounds(b, true)
            val cw = b.width() * 0.22f
            for (side in 0..1) {
                val l = if (side == 0) b.left else b.right - cw
                c.drawRect(l, b.top, l + cw, b.bottom, vGrad(b.top, b.bottom, light(curtain, 0.2f), dark(curtain, 0.1f)))
                var fx = l + 10f
                while (fx < l + cw) {
                    c.drawRect(fx, b.top, fx + 4f, b.bottom, fill(alpha(Color.BLACK, 0.12f)))
                    fx += 16f
                }
            }
        }
        // Spiegelungen
        val b = RectF()
        path.computeBounds(b, true)
        val ref = Path().apply {
            moveTo(b.left + b.width() * 0.15f, b.bottom)
            lineTo(b.left + b.width() * 0.45f, b.bottom)
            lineTo(b.left + b.width() * 0.95f, b.top)
            lineTo(b.left + b.width() * 0.65f, b.top)
            close()
        }
        c.drawPath(ref, fill(alpha(Color.WHITE, 0.28f)))
        val ref2 = Path().apply {
            moveTo(b.left + b.width() * 0.52f, b.bottom)
            lineTo(b.left + b.width() * 0.6f, b.bottom)
            lineTo(b.left + b.width() * 1.1f, b.top)
            lineTo(b.left + b.width() * 1.02f, b.top)
            close()
        }
        c.drawPath(ref2, fill(alpha(Color.WHITE, 0.22f)))
        c.drawRect(b.left, b.top, b.right, b.top + 34f, vGrad(b.top, b.top + 34f, alpha(Color.BLACK, 0.3f), alpha(Color.BLACK, 0f)))
        c.restore()
    }

    private fun archPath(cx: Float, w: Float, top: Float, bottom: Float, grow: Float = 0f): Path {
        val l = cx - w / 2 - grow
        val r = cx + w / 2 + grow
        val rad = (r - l) / 2
        return Path().apply {
            moveTo(l, bottom + grow)
            lineTo(l, top + rad - grow)
            arcTo(RectF(l, top - grow, r, top - grow + 2 * rad), 180f, 180f)
            lineTo(r, bottom + grow)
            close()
        }
    }

    private fun sill(c: Canvas, p: Palette, cx: Float, w: Float, y: Float) {
        c.drawRect(cx - w / 2 - 14f, y + 22f, cx + w / 2 + 14f, y + 44f, vGrad(y + 22f, y + 44f, alpha(Color.BLACK, 0.3f), alpha(Color.BLACK, 0f)))
        c.drawRoundRect(RectF(cx - w / 2 - 22f, y, cx + w / 2 + 22f, y + 24f), 6f, 6f, vGrad(y, y + 24f, light(p.trim, 0.25f), p.trimDark))
        c.drawRoundRect(RectF(cx - w / 2 - 22f, y, cx + w / 2 + 22f, y + 24f), 6f, 6f, stroke(alpha(OUTLINE, 0.4f), 3f))
    }

    private fun flowerBox(c: Canvas, p: Palette, rnd: Random, cx: Float, w: Float, y: Float) {
        val box = RectF(cx - w / 2 + 6f, y - 30f, cx + w / 2 - 6f, y + 2f)
        repeat(9) { i ->
            val x = box.left + 14f + i * (box.width() - 28f) / 8f
            c.drawCircle(x, box.top - 2f + rnd.nextFloat() * 6f, 18f + rnd.nextFloat() * 8f, fill(mix(0xFF3E9A3A.toInt(), 0xFF7BD35A.toInt(), rnd.nextFloat())))
        }
        repeat(10) {
            val x = box.left + 10f + rnd.nextFloat() * (box.width() - 20f)
            val fy = box.top - 14f + rnd.nextFloat() * 14f
            val col = listOf(0xFFFF5D7A.toInt(), 0xFFFFE14D.toInt(), 0xFFFFFFFF.toInt(), 0xFFB98CFF.toInt())[rnd.nextInt(4)]
            c.drawCircle(x, fy, 7f, fill(col))
            c.drawCircle(x, fy, 2.5f, fill(0xFFFFB300.toInt()))
        }
        c.drawRoundRect(box, 6f, 6f, vGrad(box.top, box.bottom, 0xFFA8643A.toInt(), 0xFF6E3B1F.toInt()))
        c.drawRoundRect(box, 6f, 6f, stroke(alpha(OUTLINE, 0.5f), 3f))
    }

    private fun archWindow(c: Canvas, p: Palette, rnd: Random, cx: Float, w: Float, top: Float, bottom: Float, flowers: Boolean) {
        // Schatten des Rahmens auf die Wand
        c.drawPath(archPath(cx + 6f, w, top + 8f, bottom + 6f, 20f), fill(alpha(Color.BLACK, 0.18f)))
        c.drawPath(archPath(cx, w, top, bottom, 20f), vGrad(top, bottom, light(p.trim, 0.25f), p.trimDark))
        c.drawPath(archPath(cx, w, top, bottom, 20f), stroke(alpha(OUTLINE, 0.55f), 4f))
        // Schlussstein
        val ks = Path().apply {
            moveTo(cx - 22f, top - 24f); lineTo(cx + 22f, top - 24f); lineTo(cx + 14f, top + 12f); lineTo(cx - 14f, top + 12f); close()
        }
        val glassPath = archPath(cx, w, top, bottom)
        glass(c, p, glassPath, top, bottom, rnd)
        c.drawPath(glassPath, stroke(alpha(OUTLINE, 0.6f), 4f))
        // Sprossen: Mittelpfosten, Kaempfer und Faecher im Bogen
        val bar = fill(p.frame)
        val rad = w / 2
        c.drawRect(cx - 6f, top + 10f, cx + 6f, bottom, bar)
        c.drawRect(cx - w / 2, top + rad, cx + w / 2, top + rad + 12f, bar)
        for (k in 1..3) {
            val a = PI - k * PI / 4
            c.drawLine(cx, top + rad, cx + (rad * cos(a)).toFloat(), top + rad - (rad * sin(a)).toFloat(), stroke(p.frame, 8f))
        }
        c.drawRect(cx - w / 2, (top + rad + bottom) / 2 + 20f, cx + w / 2, (top + rad + bottom) / 2 + 28f, bar)
        c.drawPath(ks, vGrad(top - 24f, top + 12f, light(p.trim, 0.3f), p.trimDark))
        c.drawPath(ks, stroke(alpha(OUTLINE, 0.5f), 3f))
        sill(c, p, cx, w, bottom + 8f)
        if (flowers) flowerBox(c, p, rnd, cx, w, bottom + 8f)
    }

    private fun rectWindow(c: Canvas, p: Palette, rnd: Random, cx: Float, w: Float, top: Float, bottom: Float, shutters: Boolean) {
        if (shutters) {
            for (side in listOf(-1f, 1f)) {
                val l = if (side < 0) cx - w / 2 - 82f else cx + w / 2 + 22f
                val r = RectF(l, top - 6f, l + 60f, bottom + 6f)
                c.drawRoundRect(r, 6f, 6f, vGrad(r.top, r.bottom, light(p.accent, 0.15f), dark(p.accent, 0.15f)))
                var y = r.top + 14f
                while (y < r.bottom - 8f) {
                    c.drawRect(r.left + 8f, y, r.right - 8f, y + 5f, fill(alpha(Color.BLACK, 0.2f)))
                    c.drawRect(r.left + 8f, y + 5f, r.right - 8f, y + 8f, fill(alpha(Color.WHITE, 0.3f)))
                    y += 22f
                }
                c.drawRoundRect(r, 6f, 6f, stroke(alpha(OUTLINE, 0.55f), 4f))
            }
        }
        val outer = RectF(cx - w / 2 - 20f, top - 20f, cx + w / 2 + 20f, bottom + 8f)
        c.drawRoundRect(RectF(outer.left + 6f, outer.top + 8f, outer.right + 6f, outer.bottom + 8f), 14f, 14f, fill(alpha(Color.BLACK, 0.18f)))
        c.drawRoundRect(outer, 14f, 14f, vGrad(outer.top, outer.bottom, light(p.trim, 0.3f), p.trimDark))
        c.drawRoundRect(outer, 14f, 14f, stroke(alpha(OUTLINE, 0.55f), 4f))
        val gp = Path().apply { addRoundRect(RectF(cx - w / 2, top, cx + w / 2, bottom), 8f, 8f, Path.Direction.CW) }
        glass(c, p, gp, top, bottom, rnd)
        c.drawPath(gp, stroke(alpha(OUTLINE, 0.6f), 4f))
        c.drawRect(cx - 8f, top, cx + 8f, bottom, fill(p.frame))
        c.drawRect(cx - w / 2, top + (bottom - top) * 0.42f, cx + w / 2, top + (bottom - top) * 0.42f + 12f, fill(p.frame))
        sill(c, p, cx, w, bottom + 8f)
    }

    private fun bayWindow(c: Canvas, p: Palette, rnd: Random) {
        val l = 110f
        val r = 914f
        val top = 104f
        val bottom = 404f
        c.drawRoundRect(RectF(l - 14f, top - 4f, r + 26f, bottom + 30f), 18f, 18f, fill(alpha(Color.BLACK, 0.2f)))
        c.drawRoundRect(RectF(l - 22f, top - 22f, r + 22f, bottom + 18f), 18f, 18f, vGrad(top, bottom, light(p.trim, 0.3f), p.trimDark))
        c.drawRoundRect(RectF(l - 22f, top - 22f, r + 22f, bottom + 18f), 18f, 18f, stroke(alpha(OUTLINE, 0.55f), 4f))
        val gp = Path().apply { addRoundRect(RectF(l, top, r, bottom), 10f, 10f, Path.Direction.CW) }
        glass(c, p, gp, top, bottom, rnd)
        c.drawPath(gp, stroke(alpha(OUTLINE, 0.6f), 4f))
        val seg = (r - l) / 4
        for (i in 1..3) {
            val x = l + seg * i
            c.drawRect(x - 9f, top, x + 9f, bottom, fill(p.frame))
            c.drawRect(x - 9f, top, x - 4f, bottom, fill(alpha(Color.WHITE, 0.25f)))
        }
        c.drawRect(l, top + 96f, r, top + 108f, fill(p.frame))
        // Blumen auf dem Sims zwischen den Scheiben
        c.drawRect(l - 30f, bottom + 18f, r + 30f, bottom + 40f, vGrad(bottom + 18f, bottom + 40f, light(p.trim, 0.2f), p.trimDark))
    }

    private fun smallWindow(c: Canvas, p: Palette, kind: Kind, rnd: Random, cx: Float) {
        when (kind) {
            Kind.RED, Kind.YELLOW -> archWindow(c, p, rnd, cx, 150f, 150f, 360f, flowers = false)
            else -> rectWindow(c, p, rnd, cx, 160f, 160f, 360f, shutters = false)
        }
    }

    private fun entrance(c: Canvas, p: Palette, kind: Kind) {
        val cx = 512f
        val w = 250f
        val top = 150f
        val bottom = 470f
        val arched = kind == Kind.RED || kind == Kind.YELLOW
        val outer = if (arched) archPath(cx, w, top, bottom, 26f) else Path().apply {
            addRoundRect(RectF(cx - w / 2 - 26f, top - 26f, cx + w / 2 + 26f, bottom), 14f, 14f, Path.Direction.CW)
        }
        c.drawPath(outer, vGrad(top, bottom, light(p.trim, 0.3f), p.trimDark))
        c.drawPath(outer, stroke(alpha(OUTLINE, 0.6f), 4f))
        val inner = if (arched) archPath(cx, w, top, bottom) else Path().apply { addRect(RectF(cx - w / 2, top, cx + w / 2, bottom), Path.Direction.CW) }
        c.drawPath(inner, vGrad(top, bottom, 0xFF9A5A32.toInt(), 0xFF5E331B.toInt()))
        c.save()
        c.clipPath(inner)
        // Zwei Fluegel mit Kassetten und Glas oben
        c.drawRect(cx - 4f, top, cx + 4f, bottom, fill(0xFF3E2213.toInt()))
        for (side in listOf(-1f, 1f)) {
            val l = if (side < 0) cx - w / 2 + 22f else cx + 22f
            val glassR = RectF(l, top + (if (arched) 70f else 30f), l + w / 2 - 44f, top + 170f)
            c.drawRoundRect(glassR, 8f, 8f, vGrad(glassR.top, glassR.bottom, p.glassTop, p.glassBottom))
            c.drawRoundRect(glassR, 8f, 8f, stroke(0xFF3E2213.toInt(), 5f))
            val panel = RectF(l, top + 196f, l + w / 2 - 44f, bottom - 28f)
            c.drawRoundRect(panel, 8f, 8f, fill(alpha(Color.BLACK, 0.18f)))
            c.drawRoundRect(RectF(panel.left + 6f, panel.top + 6f, panel.right - 6f, panel.bottom - 6f), 6f, 6f, fill(alpha(Color.WHITE, 0.08f)))
        }
        c.restore()
        c.drawPath(inner, stroke(alpha(OUTLINE, 0.7f), 5f))
        c.drawCircle(cx - 20f, 330f, 9f, fill(0xFFFFD34D.toInt()))
        c.drawCircle(cx + 20f, 330f, 9f, fill(0xFFFFD34D.toInt()))
        // Vordach bei den modernen Haeusern, Laternen links und rechts
        if (!arched) {
            val aw = Path().apply {
                moveTo(cx - w / 2 - 60f, top - 30f); lineTo(cx + w / 2 + 60f, top - 30f)
                lineTo(cx + w / 2 + 90f, top + 40f); lineTo(cx - w / 2 - 90f, top + 40f); close()
            }
            c.save()
            c.clipPath(aw)
            var x = cx - w / 2 - 100f
            var i = 0
            while (x < cx + w / 2 + 100f) {
                c.drawRect(x, top - 40f, x + 34f, top + 50f, fill(if (i % 2 == 0) p.accent else Color.WHITE))
                x += 34f
                i++
            }
            c.drawRect(cx - 300f, top + 20f, cx + 300f, top + 40f, fill(alpha(Color.BLACK, 0.18f)))
            c.restore()
            c.drawPath(aw, stroke(alpha(OUTLINE, 0.6f), 4f))
        }
        for (lx in listOf(cx - w / 2 - 70f, cx + w / 2 + 70f)) {
            c.drawCircle(lx, 260f, 44f, paint().apply {
                shader = RadialGradient(lx, 260f, 44f, alpha(0xFFFFE9A0.toInt(), 0.7f), alpha(0xFFFFE9A0.toInt(), 0f), Shader.TileMode.CLAMP)
            })
            c.drawRoundRect(RectF(lx - 16f, 236f, lx + 16f, 286f), 8f, 8f, fill(0xFFFFF1B8.toInt()))
            c.drawRoundRect(RectF(lx - 16f, 236f, lx + 16f, 286f), 8f, 8f, stroke(0xFF2C2C35.toInt(), 6f))
            c.drawRect(lx - 20f, 228f, lx + 20f, 238f, fill(0xFF2C2C35.toInt()))
        }
    }

    private fun roofDeck(c: Canvas, p: Palette, rnd: Random) {
        val base = mix(p.trim, 0xFFB8B6B0.toInt(), 0.35f)
        c.drawRect(0f, 0f, 512f, 512f, fill(base))
        repeat(900) {
            c.drawCircle(rnd.nextFloat() * 512f, rnd.nextFloat() * 512f, 1f + rnd.nextFloat() * 3f, fill(alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 0.08f)))
        }
        // Rand und Platten
        c.drawRect(0f, 0f, 512f, 512f, stroke(dark(p.trimDark, 0.2f), 40f))
        c.drawRect(20f, 20f, 492f, 492f, stroke(alpha(Color.WHITE, 0.35f), 4f))
        var x = 128f
        while (x < 512f) {
            c.drawLine(x, 24f, x, 488f, stroke(alpha(Color.BLACK, 0.1f), 3f))
            c.drawLine(24f, x, 488f, x, stroke(alpha(Color.BLACK, 0.1f), 3f))
            x += 128f
        }
        // Dachluke in der Mitte
        val hatch = RectF(180f, 180f, 332f, 332f)
        c.drawRoundRect(RectF(hatch.left + 8f, hatch.top + 10f, hatch.right + 8f, hatch.bottom + 10f), 10f, 10f, fill(alpha(Color.BLACK, 0.25f)))
        c.drawRoundRect(hatch, 10f, 10f, vGrad(hatch.top, hatch.bottom, light(p.wall, 0.15f), p.wallDark))
        c.drawRoundRect(hatch, 10f, 10f, stroke(alpha(OUTLINE, 0.6f), 5f))
        c.drawRect(hatch.left + 24f, 250f, hatch.right - 24f, 262f, fill(alpha(Color.BLACK, 0.3f)))
        c.drawRoundRect(RectF(236f, 210f, 276f, 226f), 6f, 6f, fill(0xFF4A4A55.toInt()))
        // Lueftungsrohre
        for ((vx, vy) in listOf(90f to 90f, 420f to 420f, 420f to 96f)) {
            c.drawCircle(vx + 4f, vy + 6f, 22f, fill(alpha(Color.BLACK, 0.25f)))
            c.drawCircle(vx, vy, 22f, fill(0xFFC9CDD3.toInt()))
            c.drawCircle(vx, vy, 13f, fill(0xFF5A5F68.toInt()))
            c.drawCircle(vx, vy, 22f, stroke(alpha(OUTLINE, 0.5f), 3f))
        }
    }

    private fun cornice(c: Canvas, p: Palette) {
        c.drawRect(0f, 0f, 512f, 512f, vGrad(0f, 512f, light(p.trim, 0.3f), p.trimDark))
        c.drawRect(0f, 0f, 512f, 40f, fill(alpha(Color.WHITE, 0.45f)))
        var x = 0f
        var i = 0
        while (x < 512f) {
            c.drawRect(x, 180f, x + 14f, 400f, fill(if (i % 2 == 0) dark(p.trimDark, 0.12f) else light(p.trim, 0.1f)))
            x += 22f
            i++
        }
        c.drawRect(0f, 400f, 512f, 512f, vGrad(400f, 512f, p.trimDark, dark(p.trimDark, 0.35f)))
        c.drawRect(0f, 470f, 512f, 512f, fill(alpha(OUTLINE, 0.5f)))
    }

    // ------------------------------------------------------------ Sonstiges

    /** 1024 x 1024: Dachziegel, Beton, Metall, Pflaster. */
    val UV_ROOF = Uv.px(0, 0, 512, 512, 1024, 1024)
    val UV_CONCRETE = Uv.px(512, 0, 1024, 512, 1024, 1024)
    val UV_METAL = Uv.px(0, 512, 512, 1024, 1024, 1024)
    val UV_PAVING = Uv.px(512, 512, 1024, 1024, 1024, 1024)

    fun miscAtlas(): Bitmap {
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(99)
        // Dachziegel
        c.drawRect(0f, 0f, 512f, 512f, fill(0xFF8E3020.toInt()))
        var row = 0
        var y = -20f
        while (y < 512f) {
            var x = if (row % 2 == 0) -32f else 0f
            while (x < 512f) {
                val t = RectF(x + 2f, y, x + 62f, y + 60f)
                val col = mix(0xFFD4553A.toInt(), 0xFFA83A26.toInt(), rnd.nextFloat() * 0.6f)
                c.drawRoundRect(t, 28f, 28f, vGrad(t.top, t.bottom, light(col, 0.15f), dark(col, 0.2f)))
                c.drawRoundRect(t, 28f, 28f, stroke(alpha(0xFF4A160C.toInt(), 0.7f), 3f))
                x += 64f
            }
            y += 40f
            row++
        }
        // Beton
        c.drawRect(512f, 0f, 1024f, 512f, fill(0xFFBFC3C8.toInt()))
        repeat(1800) {
            c.drawCircle(512f + rnd.nextFloat() * 512f, rnd.nextFloat() * 512f, 1f + rnd.nextFloat() * 2.5f, fill(alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 0.1f)))
        }
        for (k in 0..4) {
            c.drawLine(512f + k * 128f, 0f, 512f + k * 128f, 512f, stroke(alpha(Color.BLACK, 0.18f), 4f))
            c.drawLine(512f, k * 128f, 1024f, k * 128f, stroke(alpha(Color.BLACK, 0.18f), 4f))
        }
        // Metall (wird eingefaerbt)
        c.drawRect(0f, 512f, 512f, 1024f, fill(0xFFE2E5EA.toInt()))
        repeat(160) {
            val x = rnd.nextFloat() * 512f
            c.drawLine(x, 512f, x, 1024f, stroke(alpha(if (rnd.nextBoolean()) Color.BLACK else Color.WHITE, 0.06f), 2f))
        }
        // Pflaster
        c.drawRect(512f, 512f, 1024f, 1024f, fill(0xFF9C968C.toInt()))
        for (r in 0 until 8) for (k in 0 until 8) {
            val off = if (r % 2 == 0) 0f else 32f
            val t = RectF(512f + k * 64f + off + 3f, 512f + r * 64f + 3f, 512f + k * 64f + off + 61f, 512f + r * 64f + 61f)
            c.drawRoundRect(t, 8f, 8f, fill(mix(0xFFE6DCC8.toInt(), 0xFFCFC3AD.toInt(), rnd.nextFloat())))
        }
        return bmp
    }

    /** Wiederholbarer Rasen. */
    fun grass(): Bitmap {
        val bmp = Bitmap.createBitmap(512, 512, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(5)
        c.drawRect(0f, 0f, 512f, 512f, fill(0xFF6CC24A.toInt()))
        repeat(2400) {
            val x = rnd.nextFloat() * 512f
            val y = rnd.nextFloat() * 512f
            val col = if (rnd.nextBoolean()) alpha(0xFF3E8F2E.toInt(), 0.45f) else alpha(0xFFA6E36B.toInt(), 0.45f)
            for (ox in listOf(-512f, 0f, 512f)) for (oy in listOf(-512f, 0f, 512f)) {
                c.drawLine(x + ox, y + oy, x + ox + rnd.nextFloat() * 4f - 2f, y + oy - 6f - rnd.nextFloat() * 6f, stroke(col, 2.5f))
            }
        }
        return bmp
    }

    /** Wiederholbare Hochhausfassade, 4 x 8 Fenster. */
    fun skyscraper(night: Boolean): Bitmap {
        val bmp = Bitmap.createBitmap(512, 1024, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val rnd = Random(if (night) 17 else 3)
        c.drawRect(0f, 0f, 512f, 1024f, fill(if (night) 0xFF1B2440.toInt() else 0xFFF3F5F9.toInt()))
        for (r in 0 until 8) for (k in 0 until 4) {
            val x = k * 128f
            val y = r * 128f
            val win = RectF(x + 16f, y + 20f, x + 112f, y + 112f)
            if (night) {
                val lit = rnd.nextFloat() < 0.42f
                if (lit) {
                    val warm = if (rnd.nextFloat() < 0.7f) 0xFFFFD27A.toInt() else 0xFFBFE6FF.toInt()
                    c.drawRect(win, vGrad(win.top, win.bottom, light(warm, 0.3f), warm))
                } else {
                    c.drawRect(win, vGrad(win.top, win.bottom, 0xFF2E3F6B.toInt(), 0xFF223055.toInt()))
                }
            } else {
                val top = mix(0xFFCDEBFF.toInt(), 0xFFFFFFFF.toInt(), rnd.nextFloat() * 0.4f)
                c.drawRect(win, vGrad(win.top, win.bottom, top, 0xFF5E9FD8.toInt()))
                c.drawRect(win.left, win.top, win.left + 30f, win.bottom, fill(alpha(Color.WHITE, 0.25f)))
            }
            c.drawRect(x + 62f, win.top, x + 66f, win.bottom, fill(alpha(if (night) Color.BLACK else 0xFF6B7C93.toInt(), 0.5f)))
            c.drawRect(x, y + 112f, x + 128f, y + 128f, fill(alpha(Color.BLACK, if (night) 0.3f else 0.1f)))
        }
        return bmp
    }

    // ------------------------------------------------------------ Sprites

    /** 4 x 4 Zellen zu 256 Pixel. */
    const val SPRITE_PARA = 0 // 0..3
    const val SPRITE_CLOUD = 4 // 4..6
    const val SPRITE_STAR = 7
    const val SPRITE_PUFF = 8
    const val SPRITE_TREE = 9
    const val SPRITE_PINE = 10
    const val SPRITE_BALLOON = 11
    const val SPRITE_SPARKLE = 12
    const val SPRITE_BIRD = 13
    const val SPRITE_SHADOW = 14

    fun spriteUv(cell: Int): FloatArray {
        val cx = cell % 4
        val cy = cell / 4
        val e = 2f / 1024f
        return floatArrayOf(cx * 0.25f + e, cy * 0.25f + e, (cx + 1) * 0.25f - e, (cy + 1) * 0.25f - e)
    }

    fun sprites(): Bitmap {
        val bmp = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val canopies = listOf(
            0xFFE84A4A.toInt() to 0xFFFFFFFF.toInt(),
            0xFF3D8BFF.toInt() to 0xFFFFE14D.toInt(),
            0xFF4CC46A.toInt() to 0xFFFFFFFF.toInt(),
            0xFFFF8BC2.toInt() to 0xFF8A5CFF.toInt(),
        )
        val shirts = listOf(0xFF2F6FDB.toInt(), 0xFFE8643C.toInt(), 0xFFFFC531.toInt(), 0xFF2EA65A.toInt())
        for (i in 0..3) cell(c, SPRITE_PARA + i) { parachuter(it, canopies[i].first, canopies[i].second, shirts[i], i) }
        for (i in 0..2) cell(c, SPRITE_CLOUD + i) { cloud(it, Random(40 + i)) }
        cell(c, SPRITE_STAR) { star(it) }
        cell(c, SPRITE_PUFF) { puff(it) }
        cell(c, SPRITE_TREE) { tree(it) }
        cell(c, SPRITE_PINE) { pine(it) }
        cell(c, SPRITE_BALLOON) { balloon(it) }
        cell(c, SPRITE_SPARKLE) { sparkle(it) }
        cell(c, SPRITE_BIRD) { bird(it) }
        cell(c, SPRITE_SHADOW) { shadow(it) }
        return bmp
    }

    private fun cell(c: Canvas, i: Int, draw: (Canvas) -> Unit) {
        c.save()
        c.translate((i % 4) * 256f, (i / 4) * 256f)
        c.clipRect(0, 0, 256, 256)
        draw(c)
        c.restore()
    }

    private fun parachuter(c: Canvas, a: Int, b: Int, shirt: Int, seed: Int) {
        val dome = RectF(24f, 18f, 232f, 150f)
        val domePath = Path().apply {
            arcTo(dome, 180f, 180f)
            // Wellenkante unten
            val n = 6
            for (k in n downTo 1) {
                val x0 = 24f + (k) * 208f / n
                val x1 = 24f + (k - 1) * 208f / n
                quadTo((x0 + x1) / 2, 100f, x1, 84f)
            }
            close()
        }
        c.drawPath(domePath, fill(a))
        c.save()
        c.clipPath(domePath)
        for (k in 0 until 6 step 2) {
            val x0 = 24f + k * 208f / 6
            val stripe = Path().apply {
                moveTo(128f, 0f); lineTo(x0, 110f); lineTo(x0 + 208f / 6, 110f); close()
            }
            c.drawPath(stripe, fill(b))
        }
        c.drawRect(0f, 0f, 256f, 160f, vGrad(10f, 100f, alpha(Color.WHITE, 0.35f), alpha(Color.BLACK, 0.12f)))
        c.restore()
        c.drawPath(domePath, stroke(OUTLINE, 5f))
        // Leinen
        val cords = stroke(alpha(0xFF3A3A44.toInt(), 0.85f), 2.5f)
        for (x in listOf(34f, 90f, 166f, 222f)) c.drawLine(x, 86f, 128f, 178f, cords)
        // Figur
        val skin = 0xFFF3C29B.toInt()
        c.drawLine(116f, 186f, 104f, 168f, stroke(skin, 9f))
        c.drawLine(140f, 186f, 152f, 168f, stroke(skin, 9f))
        c.drawLine(120f, 218f, 112f, 246f, stroke(0xFF2C3E66.toInt(), 12f))
        c.drawLine(136f, 218f, 146f, 244f, stroke(0xFF2C3E66.toInt(), 12f))
        c.drawRoundRect(RectF(110f, 182f, 146f, 224f), 12f, 12f, fill(shirt))
        c.drawRoundRect(RectF(110f, 182f, 146f, 224f), 12f, 12f, stroke(OUTLINE, 4f))
        c.drawCircle(128f, 164f, 18f, fill(skin))
        val hair = listOf(0xFF4A2B17.toInt(), 0xFFF2C14E.toInt(), 0xFF1E1E24.toInt(), 0xFFB5482C.toInt())[seed % 4]
        c.drawArc(RectF(110f, 145f, 146f, 175f), 180f, 180f, true, fill(hair))
        c.drawCircle(128f, 164f, 18f, stroke(OUTLINE, 4f))
        c.drawCircle(122f, 166f, 2.5f, fill(OUTLINE))
        c.drawCircle(134f, 166f, 2.5f, fill(OUTLINE))
        c.drawArc(RectF(121f, 168f, 135f, 176f), 0f, 180f, false, stroke(OUTLINE, 2.5f))
    }

    private fun cloud(c: Canvas, rnd: Random) {
        val blobs = List(7) { i ->
            val t = i / 6f
            Triple(40f + t * 176f + rnd.nextFloat() * 10f, 150f - sin(t * PI).toFloat() * 46f - rnd.nextFloat() * 14f, 34f + sin(t * PI).toFloat() * 34f + rnd.nextFloat() * 10f)
        }
        // Weicher Schatten unten, dann helle Ballen
        for ((x, y, r) in blobs) c.drawCircle(x, y + 10f, r, fill(alpha(0xFFB9CCE6.toInt(), 0.9f)))
        for ((x, y, r) in blobs) {
            c.drawCircle(x, y, r, paint().apply {
                shader = RadialGradient(x - r * 0.3f, y - r * 0.4f, r * 1.3f, 0xFFFFFFFF.toInt(), 0xFFE3ECF8.toInt(), Shader.TileMode.CLAMP)
            })
        }
        c.drawRect(0f, 176f, 256f, 256f, paint().apply { xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.CLEAR) })
    }

    private fun star(c: Canvas) {
        c.drawCircle(128f, 128f, 120f, paint().apply {
            shader = RadialGradient(128f, 128f, 120f, alpha(0xFFFFF3A0.toInt(), 0.55f), alpha(0xFFFFF3A0.toInt(), 0f), Shader.TileMode.CLAMP)
        })
        val path = Path()
        for (k in 0 until 10) {
            val r = if (k % 2 == 0) 92f else 40f
            val a = -PI / 2 + k * PI / 5
            val x = 128f + (r * cos(a)).toFloat()
            val y = 132f + (r * sin(a)).toFloat()
            if (k == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        c.drawPath(path, vGrad(40f, 220f, 0xFFFFF27A.toInt(), 0xFFFFB000.toInt()))
        c.drawPath(path, stroke(0xFFD97A00.toInt(), 7f))
        c.drawCircle(104f, 104f, 12f, fill(alpha(Color.WHITE, 0.85f)))
    }

    private fun puff(c: Canvas) {
        for ((x, y, r) in listOf(Triple(100f, 140f, 70f), Triple(160f, 130f, 76f), Triple(128f, 100f, 70f))) {
            c.drawCircle(x, y, r, paint().apply {
                shader = RadialGradient(x, y, r, intArrayOf(alpha(Color.WHITE, 0.9f), alpha(0xFFE6E2DA.toInt(), 0.6f), alpha(0xFFE6E2DA.toInt(), 0f)), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
            })
        }
    }

    private fun tree(c: Canvas) {
        c.drawRoundRect(RectF(112f, 150f, 144f, 250f), 10f, 10f, hGrad(112f, 144f, 0xFF9A6236.toInt(), 0xFF5E3519.toInt()))
        c.drawRoundRect(RectF(112f, 150f, 144f, 250f), 10f, 10f, stroke(OUTLINE, 4f))
        val balls = listOf(Triple(128f, 70f, 62f), Triple(78f, 112f, 54f), Triple(178f, 112f, 54f), Triple(128f, 130f, 58f), Triple(96f, 70f, 40f), Triple(166f, 74f, 42f))
        for ((x, y, r) in balls) c.drawCircle(x, y, r + 5f, fill(OUTLINE))
        for ((x, y, r) in balls) c.drawCircle(x, y, r, paint().apply {
            shader = RadialGradient(x - r * 0.35f, y - r * 0.45f, r * 1.4f, 0xFF9BE36A.toInt(), 0xFF2F8A36.toInt(), Shader.TileMode.CLAMP)
        })
    }

    private fun pine(c: Canvas) {
        c.drawRect(116f, 200f, 140f, 252f, fill(0xFF6B3E1F.toInt()))
        for (k in 0..2) {
            val top = 20f + k * 52f
            val w = 70f + k * 30f
            val p = Path().apply { moveTo(128f, top); lineTo(128f + w, top + 110f); lineTo(128f - w, top + 110f); close() }
            c.drawPath(p, vGrad(top, top + 110f, 0xFF5CC46A.toInt(), 0xFF1F6E3A.toInt()))
            c.drawPath(p, stroke(OUTLINE, 5f))
        }
    }

    private fun balloon(c: Canvas) {
        val env = RectF(48f, 10f, 208f, 190f)
        c.drawOval(env, fill(0xFFFF5D5D.toInt()))
        c.save()
        c.clipPath(Path().apply { addOval(env, Path.Direction.CW) })
        val cols = listOf(0xFFFFE14D.toInt(), 0xFF3D8BFF.toInt(), 0xFFFFFFFF.toInt())
        for (k in 0 until 6) {
            val x = 48f + k * 160f / 6
            c.drawOval(RectF(x - 6f, 10f, x + 26f, 190f), fill(cols[k % 3]))
        }
        c.drawOval(env, paint().apply {
            shader = RadialGradient(100f, 60f, 160f, alpha(Color.WHITE, 0.35f), alpha(Color.BLACK, 0.25f), Shader.TileMode.CLAMP)
        })
        c.restore()
        c.drawOval(env, stroke(OUTLINE, 5f))
        c.drawLine(98f, 176f, 112f, 218f, stroke(OUTLINE, 3f))
        c.drawLine(158f, 176f, 144f, 218f, stroke(OUTLINE, 3f))
        c.drawRoundRect(RectF(106f, 214f, 150f, 248f), 6f, 6f, fill(0xFFA0642F.toInt()))
        c.drawRoundRect(RectF(106f, 214f, 150f, 248f), 6f, 6f, stroke(OUTLINE, 4f))
    }

    private fun sparkle(c: Canvas) {
        c.drawCircle(128f, 128f, 110f, paint().apply {
            shader = RadialGradient(128f, 128f, 110f, alpha(Color.WHITE, 0.8f), alpha(0xFFFFF6C0.toInt(), 0f), Shader.TileMode.CLAMP)
        })
        val p = Path().apply {
            moveTo(128f, 10f); quadTo(140f, 116f, 246f, 128f); quadTo(140f, 140f, 128f, 246f)
            quadTo(116f, 140f, 10f, 128f); quadTo(116f, 116f, 128f, 10f); close()
        }
        c.drawPath(p, fill(Color.WHITE))
    }

    private fun bird(c: Canvas) {
        val p = Path().apply {
            moveTo(40f, 120f); quadTo(90f, 80f, 128f, 130f); quadTo(166f, 80f, 216f, 120f)
            quadTo(166f, 100f, 128f, 146f); quadTo(90f, 100f, 40f, 120f); close()
        }
        c.drawPath(p, fill(0xFF3A3F55.toInt()))
    }

    private fun shadow(c: Canvas) {
        c.drawOval(RectF(8f, 70f, 248f, 186f), paint().apply {
            shader = RadialGradient(128f, 128f, 120f, alpha(Color.BLACK, 0.45f), alpha(Color.BLACK, 0f), Shader.TileMode.CLAMP)
        })
    }
}
