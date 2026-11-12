package de.tonyapps.towerbloxx.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

class Program(vs: String, fs: String) {
    val id: Int
    private val uniforms = HashMap<String, Int>()

    init {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        id = GLES30.glCreateProgram()
        GLES30.glAttachShader(id, v)
        GLES30.glAttachShader(id, f)
        GLES30.glLinkProgram(id)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(id, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("TowerGl", "Link: " + GLES30.glGetProgramInfoLog(id))
    }

    fun use() = GLES30.glUseProgram(id)

    fun u(name: String): Int = uniforms.getOrPut(name) { GLES30.glGetUniformLocation(id, name) }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) Log.e("TowerGl", "Shader: " + GLES30.glGetShaderInfoLog(s))
        return s
    }
}

fun floatBuffer(data: FloatArray): FloatBuffer =
    ByteBuffer.allocateDirect(data.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(data)
        position(0)
    }

/** Dreiecke mit Position (3), Normale (3) und Texturkoordinate (2). */
class Mesh(data: FloatArray) {
    private val vbo: Int
    val count = data.size / 8

    init {
        val ids = IntArray(1)
        GLES30.glGenBuffers(1, ids, 0)
        vbo = ids[0]
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, data.size * 4, floatBuffer(data), GLES30.GL_STATIC_DRAW)
    }

    fun draw() {
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo)
        GLES30.glEnableVertexAttribArray(0)
        GLES30.glEnableVertexAttribArray(1)
        GLES30.glEnableVertexAttribArray(2)
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 32, 0)
        GLES30.glVertexAttribPointer(1, 3, GLES30.GL_FLOAT, false, 32, 12)
        GLES30.glVertexAttribPointer(2, 2, GLES30.GL_FLOAT, false, 32, 24)
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, count)
    }
}

/** Rechteck in der Textur: links oben (u0, v0), rechts unten (u1, v1). v = 0 ist die oberste Bildzeile. */
data class Uv(val u0: Float, val v0: Float, val u1: Float, val v1: Float) {
    companion object {
        val FULL = Uv(0f, 0f, 1f, 1f)

        /** Pixelbereich mit kleinem Rand gegen Ausbluten beim Mipmapping. */
        fun px(x0: Int, y0: Int, x1: Int, y1: Int, w: Int, h: Int, inset: Int = 4) = Uv(
            (x0 + inset).toFloat() / w, (y0 + inset).toFloat() / h,
            (x1 - inset).toFloat() / w, (y1 - inset).toFloat() / h,
        )
    }
}

class MeshBuilder {
    private val f = ArrayList<Float>(4096)

    private fun v(p: FloatArray, n: FloatArray, u: Float, w: Float) {
        f += p[0]; f += p[1]; f += p[2]
        f += n[0]; f += n[1]; f += n[2]
        f += u; f += w
    }

    /** Viereck gegen den Uhrzeigersinn von aussen gesehen: unten links, unten rechts, oben rechts, oben links. */
    fun quad(bl: FloatArray, br: FloatArray, tr: FloatArray, tl: FloatArray, n: FloatArray, uv: Uv) {
        v(bl, n, uv.u0, uv.v1); v(br, n, uv.u1, uv.v1); v(tr, n, uv.u1, uv.v0)
        v(bl, n, uv.u0, uv.v1); v(tr, n, uv.u1, uv.v0); v(tl, n, uv.u0, uv.v0)
    }

    fun tri(a: FloatArray, b: FloatArray, c: FloatArray, ua: FloatArray, ub: FloatArray, uc: FloatArray) {
        val e1 = floatArrayOf(b[0] - a[0], b[1] - a[1], b[2] - a[2])
        val e2 = floatArrayOf(c[0] - a[0], c[1] - a[1], c[2] - a[2])
        val n = floatArrayOf(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
        val l = kotlin.math.sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2])
        n[0] /= l; n[1] /= l; n[2] /= l
        v(a, n, ua[0], ua[1]); v(b, n, ub[0], ub[1]); v(c, n, uc[0], uc[1])
    }

    fun box(
        x0: Float, y0: Float, z0: Float, x1: Float, y1: Float, z1: Float,
        side: Uv, top: Uv = side, bottom: Uv = side,
    ) {
        fun p(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
        // vorne +z
        quad(p(x0, y0, z1), p(x1, y0, z1), p(x1, y1, z1), p(x0, y1, z1), floatArrayOf(0f, 0f, 1f), side)
        // hinten -z
        quad(p(x1, y0, z0), p(x0, y0, z0), p(x0, y1, z0), p(x1, y1, z0), floatArrayOf(0f, 0f, -1f), side)
        // rechts +x
        quad(p(x1, y0, z1), p(x1, y0, z0), p(x1, y1, z0), p(x1, y1, z1), floatArrayOf(1f, 0f, 0f), side)
        // links -x
        quad(p(x0, y0, z0), p(x0, y0, z1), p(x0, y1, z1), p(x0, y1, z0), floatArrayOf(-1f, 0f, 0f), side)
        // oben
        quad(p(x0, y1, z1), p(x1, y1, z1), p(x1, y1, z0), p(x0, y1, z0), floatArrayOf(0f, 1f, 0f), top)
        // unten
        quad(p(x0, y0, z0), p(x1, y0, z0), p(x1, y0, z1), p(x0, y0, z1), floatArrayOf(0f, -1f, 0f), bottom)
    }

    fun build() = Mesh(f.toFloatArray())
}

object Tex {
    fun upload(bmp: Bitmap, repeat: Boolean = false, mipmap: Boolean = true): Int {
        val ids = IntArray(1)
        GLES30.glGenTextures(1, ids, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0])
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bmp, 0)
        val wrap = if (repeat) GLES30.GL_REPEAT else GLES30.GL_CLAMP_TO_EDGE
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, wrap)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        if (mipmap) {
            GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        } else {
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        }
        bmp.recycle()
        return ids[0]
    }
}

object Shaders {
    const val LIT_VS = """#version 300 es
uniform mat4 uVp;
uniform mat4 uModel;
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;
layout(location = 2) in vec2 aUv;
out vec3 vNormal;
out vec2 vUv;
out vec3 vWorld;
void main() {
    vec4 w = uModel * vec4(aPos, 1.0);
    vWorld = w.xyz;
    vNormal = mat3(uModel) * aNormal;
    vUv = aUv;
    gl_Position = uVp * w;
}
"""

    const val LIT_FS = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbient;
uniform vec3 uFogColor;
uniform vec3 uEye;
uniform vec2 uFog;
uniform vec4 uTint;
uniform vec2 uUvScale;
uniform float uUnlit;
in vec3 vNormal;
in vec2 vUv;
in vec3 vWorld;
out vec4 outColor;
void main() {
    vec4 t = texture(uTex, vUv * uUvScale);
    vec3 n = normalize(vNormal);
    float d = max(dot(n, uLightDir), 0.0);
    float hemi = 0.5 + 0.5 * n.y;
    vec3 base = t.rgb * uTint.rgb;
    vec3 lit = base * (uAmbient * (0.75 + 0.35 * hemi) + uLightColor * d);
    // Weiches Glanzlicht von oben
    vec3 v = normalize(uEye - vWorld);
    vec3 h = normalize(uLightDir + v);
    lit += uLightColor * pow(max(dot(n, h), 0.0), 24.0) * 0.12;
    vec3 c = mix(lit, base, uUnlit);
    float f = clamp((distance(vWorld, uEye) - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
    c = mix(c, uFogColor, f * f * 0.9);
    outColor = vec4(c, t.a * uTint.a);
}
"""

    const val SPRITE_VS = """#version 300 es
uniform mat4 uVp;
uniform vec3 uCenter;
uniform vec2 uSize;
uniform vec3 uRight;
uniform vec3 uUp;
uniform float uRot;
uniform vec4 uRect;
layout(location = 0) in vec2 aCorner;
out vec2 vUv;
out vec3 vWorld;
void main() {
    float c = cos(uRot);
    float s = sin(uRot);
    vec2 p = vec2(aCorner.x * c - aCorner.y * s, aCorner.x * s + aCorner.y * c);
    vec3 w = uCenter + uRight * p.x * uSize.x + uUp * p.y * uSize.y;
    vWorld = w;
    vec2 t = vec2(aCorner.x + 0.5, 0.5 - aCorner.y);
    vUv = mix(uRect.xy, uRect.zw, t);
    gl_Position = uVp * vec4(w, 1.0);
}
"""

    const val SPRITE_FS = """#version 300 es
precision mediump float;
uniform sampler2D uTex;
uniform vec4 uTint;
uniform vec3 uFogColor;
uniform vec3 uEye;
uniform vec2 uFog;
in vec2 vUv;
in vec3 vWorld;
out vec4 outColor;
void main() {
    vec4 t = texture(uTex, vUv);
    t.rgb *= uTint.rgb;
    t *= uTint.a;
    if (t.a < 0.004) discard;
    float f = clamp((distance(vWorld, uEye) - uFog.x) / (uFog.y - uFog.x), 0.0, 1.0);
    t.rgb = mix(t.rgb, uFogColor * t.a, f * f * 0.8);
    outColor = t;
}
"""

    const val SKY_VS = """#version 300 es
layout(location = 0) in vec2 aCorner;
out vec2 vPos;
void main() {
    vPos = aCorner * 2.0;
    gl_Position = vec4(aCorner * 2.0, 0.999, 1.0);
}
"""

    const val SKY_FS = """#version 300 es
precision highp float;
uniform vec3 uTop;
uniform vec3 uBottom;
uniform float uStars;
uniform float uScroll;
uniform vec2 uAspect;
in vec2 vPos;
out vec4 outColor;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
void main() {
    float k = clamp(vPos.y * 0.5 + 0.5, 0.0, 1.0);
    vec3 c = mix(uBottom, uTop, smoothstep(0.0, 1.0, k));
    if (uStars > 0.0) {
        vec2 p = vec2(vPos.x * uAspect.x, vPos.y * uAspect.y + uScroll) * 60.0;
        vec2 cell = floor(p);
        float h = hash(cell);
        vec2 f = fract(p) - vec2(hash(cell + 1.3), hash(cell + 7.1));
        float star = step(0.93, h) * smoothstep(0.08, 0.0, length(f));
        c += vec3(star) * uStars * (0.5 + 0.5 * k);
    }
    outColor = vec4(c, 1.0);
}
"""
}
