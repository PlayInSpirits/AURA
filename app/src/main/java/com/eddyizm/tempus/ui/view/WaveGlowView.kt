package com.eddyizm.tempus.ui.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.Choreographer
import android.view.View
import androidx.annotation.RequiresApi
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * AURA_2: the living soft blob behind «Моя волна» on Home, ported from the HomeMusic web app
 * (`web/src/components/WaveGlow.tsx`, `web/src/lib/waveGlow.ts` of justman-1/HomeMusic-public).
 *
 * The same fragment shader, written in AGSL, runs on Android 13+; every input is eased exponentially, so nothing
 * jumps. The blob grows and moves faster while the wave plays, takes its colours from the current cover and flows
 * into a new shape on a skip. Older Android versions get a still soft radial gradient. Outside the blob the view
 * is transparent, so the Home background itself is unchanged.
 */
class WaveGlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Wave playing: the blob brightens, grows and moves faster (eased over a few seconds). */
    var playing: Boolean = false
        set(value) {
            field = value
            kick()
        }

    /** Blob centre in the view (0..1, y up) and zoom (bigger = smaller blob), as in the web hero. */
    var centerX = 0.5f
    var centerY = 0.5f
    var zoom = 0.9f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    // GlowState of the web app
    private var t = 0f
    private var p = 0f
    private var e = 0f
    private var s = 0f
    private var w = 0f
    private var sTarget = 0f
    private var wHold = 0f
    private val colors = arrayOf(DEFAULT[0].copyOf(), DEFAULT[1].copyOf(), DEFAULT[2].copyOf())
    private var targetColors = DEFAULT

    private var lastFrameNanos = 0L
    private var running = false
    private val shader: Any? = if (Build.VERSION.SDK_INT >= 33) makeShader() else null

    private val frame = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!running) return
            val dt = if (lastFrameNanos == 0L) 0f else (frameTimeNanos - lastFrameNanos) / 1e9f
            // ~30 fps is plenty for a shape this slow
            if (lastFrameNanos == 0L || dt >= 0.032f) {
                lastFrameNanos = frameTimeNanos
                step(dt)
                invalidate()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }

    init {
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
    }

    /** A skip of a wave track: the shape slowly flows to a new one. */
    fun skip() {
        sTarget += 2.1f
        kick()
    }

    /** A ♥ of the current wave track: a warm glow that rises and fades. */
    fun like() {
        wHold = 1.5f
        kick()
    }

    /** Colours of the current cover (null: the defaults). Call with a small bitmap, e.g. 24×24. */
    fun setCover(bitmap: Bitmap?) {
        targetColors = bitmap?.let { runCatching { paletteFromBitmap(it) }.getOrNull() } ?: DEFAULT
        kick()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        kick()
    }

    override fun onDetachedFromWindow() {
        stop()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityAggregated(isVisible: Boolean) {
        super.onVisibilityAggregated(isVisible)
        if (isVisible) kick() else stop()
    }

    private fun kick() {
        if (running || shader == null || !isAttachedToWindow || !isShown) return
        running = true
        lastFrameNanos = 0L
        Choreographer.getInstance().postFrameCallback(frame)
    }

    private fun stop() {
        running = false
        Choreographer.getInstance().removeFrameCallback(frame)
    }

    private fun smooth(x: Float, target: Float, rate: Float, dt: Float) = x + (target - x) * (1f - exp(-dt * rate))

    private fun step(rawDt: Float) {
        val dt = min(rawDt, 0.1f) // a hidden screen or a stall must not make a jump
        p = smooth(p, if (playing) 1f else 0f, 0.7f, dt)
        e = smooth(e, 0f, 0.6f, dt)
        t += dt * (0.03f + 0.045f * p)
        s = smooth(s, sTarget, 0.4f, dt)
        if (wHold > 0f) {
            wHold -= dt
            w = smooth(w, 1f, 1.6f, dt)
        } else {
            w = smooth(w, 0f, 0.8f, dt)
        }
        for (i in 0..2) for (k in 0..2) colors[i][k] = smooth(colors[i][k], targetColors[i][k], 0.5f, dt)
    }

    override fun onDraw(canvas: Canvas) {
        val width = width.toFloat()
        val height = height.toFloat()
        if (width <= 0f || height <= 0f) return
        if (Build.VERSION.SDK_INT >= 33 && shader != null) {
            drawShader(canvas, shader, width, height)
        } else {
            drawStill(canvas, width, height)
        }
    }

    @RequiresApi(33)
    private fun drawShader(canvas: Canvas, any: Any, width: Float, height: Float) {
        val sh = any as RuntimeShader
        sh.setFloatUniform("R", width, height)
        sh.setFloatUniform("T", t)
        sh.setFloatUniform("P", p)
        sh.setFloatUniform("E", e)
        sh.setFloatUniform("S", s)
        sh.setFloatUniform("W", w)
        sh.setFloatUniform("Z", zoom)
        sh.setFloatUniform("K", centerX, centerY)
        sh.setFloatUniform("A", colors[0][0], colors[0][1], colors[0][2])
        sh.setFloatUniform("B", colors[1][0], colors[1][1], colors[1][2])
        sh.setFloatUniform("C", colors[2][0], colors[2][1], colors[2][2])
        paint.shader = sh
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun drawStill(canvas: Canvas, width: Float, height: Float) {
        val a = DEFAULT[0]
        val b = DEFAULT[1]
        val radius = max(1f, min(width, height) * 0.58f)
        paint.shader = RadialGradient(
            width * centerX, height * (1f - centerY), radius,
            intArrayOf(argb(0.55f, a), argb(0.35f, b), Color.TRANSPARENT),
            floatArrayOf(0f, 0.48f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, width, height, paint)
    }

    private fun argb(alpha: Float, c: FloatArray) =
        Color.argb((alpha * 255).toInt(), (c[0] * 255).toInt(), (c[1] * 255).toInt(), (c[2] * 255).toInt())

    @RequiresApi(33)
    private fun makeShader(): RuntimeShader? = try {
        RuntimeShader(AGSL)
    } catch (e: RuntimeException) {
        android.util.Log.w("WaveGlowView", "AGSL glow unavailable", e)
        null
    }

    companion object {
        val DEFAULT: Array<FloatArray> = arrayOf(
            floatArrayOf(1f, 0.8f, 0.1f),
            floatArrayOf(0.95f, 0.15f, 0.75f),
            floatArrayOf(0.9f, 0.2f, 0.1f),
        )

        /** The web app's FRAGMENT_SHADER in AGSL (fragCoord has y down, the web's gl_FragCoord y up). */
        private const val AGSL = """
uniform float2 R;
uniform float T;
uniform float P;
uniform float E;
uniform float S;
uniform float W;
uniform float Z;
uniform float2 K;
uniform float3 A;
uniform float3 B;
uniform float3 C;
float h(float2 p){return fract(sin(dot(p,float2(127.1,311.7)))*43758.5453);}
float n(float2 p){float2 i=floor(p);float2 f=fract(p);f=f*f*(3.-2.*f);return mix(mix(h(i),h(i+float2(1,0)),f.x),mix(h(i+float2(0,1)),h(i+float2(1,1)),f.x),f.y);}
float fb(float2 p){float v=0.;float a=.5;for(int i=0;i<5;i++){v+=a*n(p);p=p*2.03+float2(1.7,9.2);a*=.5;}return v;}
half4 main(float2 fc){
  float2 g=float2(fc.x,R.y-fc.y);
  float2 u=(g-K*R)/R.y*Z;
  float t=T;
  float2 q=float2(fb(u*1.4+t+S),fb(u*1.4-t+3.1));
  float2 r=float2(fb(u*2.+q*1.7+float2(1.7,9.2)+t*1.3),fb(u*2.+q*1.7+float2(8.3,2.8)-t));
  float d=length(u);
  float a=atan(u.y,u.x);
  float rad=.19+.12*P+.035*E+.025*W+.10*(fb(float2(cos(a),sin(a))*1.3+t*1.4+S)-.5);
  float e=d-rad-.13*(r.y-.5);
  float b=1.-smoothstep(-.14,.19+.06*(1.-P),e);
  float m=clamp(d/rad+.7*(r.x-.5),0.,1.4);
  float3 c=mix(A,B,smoothstep(.1,.75,m));
  c=mix(c,C,smoothstep(.65,1.15,m));
  c=mix(c,float3(1.,.72,.62),.22*W*(1.-smoothstep(.2,.9,m)));
  float fl=(1.-smoothstep(0.,.03,abs(r.x-.52)))*b*(.03+.30*P);
  c+=fl*.4*mix(float3(1),A,.4);
  c*=.88+.12*P;
  return half4(half3(c*b),half(b));
}
"""

        // --- colours from a cover (paletteFromPixels of the web app) ---

        private fun hsl2rgb(h: Float, s: Float, l: Float): FloatArray {
            val a = s * min(l, 1 - l)
            fun f(n: Float): Float {
                val k = (n + h / 30f) % 12f
                return l - a * max(-1f, min(min(k - 3f, 9f - k), 1f))
            }
            return floatArrayOf(f(0f), f(8f), f(4f))
        }

        /** Three vivid colours (strongest hues by saturation × brightness, ≥ 40° apart), or null for a grey cover. */
        fun paletteFromBitmap(src: Bitmap): Array<FloatArray>? {
            val bmp = if (src.width > 32 || src.height > 32) Bitmap.createScaledBitmap(src, 24, 24, true) else src
            val bins = 12
            val weight = FloatArray(bins)
            var total = 0f
            val px = IntArray(bmp.width * bmp.height)
            bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
            val hsv = FloatArray(3)
            for (c in px) {
                if (Color.alpha(c) < 128) continue
                Color.colorToHSV(c, hsv)
                val w = hsv[1] * hsv[1] * hsv[2]
                weight[(floor(hsv[0] / (360f / bins)).toInt()).coerceIn(0, bins - 1)] += w
                total += w
            }
            if (total < 1f) return null
            val order = (0 until bins).sortedByDescending { weight[it] }
            val hues = mutableListOf((order[0] + 0.5f) * (360f / bins))
            for (i in order.drop(1)) {
                if (hues.size >= 3 || weight[i] < weight[order[0]] * 0.25f) break
                val hue = (i + 0.5f) * (360f / bins)
                if (hues.all { val dd = kotlin.math.abs(it - hue); min(dd, 360f - dd) >= 40f }) hues.add(hue)
            }
            while (hues.size < 3) hues.add((hues[0] + (if (hues.size == 1) 45f else -45f) + 360f) % 360f)
            return arrayOf(
                hsl2rgb(hues[0], 0.92f, 0.58f),
                hsl2rgb(hues[1], 0.85f, 0.52f),
                hsl2rgb(hues[2], 0.85f, 0.46f),
            )
        }
    }
}
