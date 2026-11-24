package com.mohammed.istighfar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import java.util.Random
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * برنامج الاستغفار
 * تطوير المبرمج: محمد عبدالله العمري
 */
class IstighfarView(context: Context) : View(context) {

    private class Drop(var y: Float, var vy: Float, val start: Long)
    private class Ripple(val start: Long, val small: Boolean)
    private class Jet(val start: Long)
    private class Wave(val start: Long)
    private class Touch(val x: Float, val y: Float, val start: Long)
    private class Particle(
        var x: Float, var y: Float, var vx: Float, var vy: Float,
        var life: Float, val maxLife: Float, val color: Int, val size: Float, val grav: Float
    )

    companion object {
        val MAX_OPTIONS = intArrayOf(33, 100, 500, 1000)
        const val DEVELOPER = "تطوير المبرمج: محمد عبدالله العمري"
    }

    private val prefs = context.getSharedPreferences("istighfar", Context.MODE_PRIVATE)
    private var maxCount = prefs.getInt("max", 100).let { if (MAX_OPTIONS.contains(it)) it else 100 }
    private var count = prefs.getInt("count", 0).coerceIn(0, maxCount)
    private var muted = prefs.getBoolean("muted", false)
    private var shown = count * 100f / maxCount
    private var last = SystemClock.uptimeMillis()
    private var pulse = 0f

    // adaptive quality (lowers effect density on slow phones)
    private var q = 1f
    private var avgDt = 0.0167f
    private var frameCnt = 0

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val maxPill = RectF()
    private val mutePill = RectF()
    private var vignette: RadialGradient? = null

    // ---------------- tilt parallax
    private val sensors = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accel: Sensor? = sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private var tiltT = 0f
    private var tilt = 0f
    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(e: SensorEvent) {
            tiltT = (-e.values[0] / 6f).coerceIn(-1f, 1f)
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
    }

    // ---------------- scenery (fixed seed)
    private val rnd = Random(7)
    private val grassN = 220
    private val grassX = FloatArray(grassN) { rnd.nextFloat() }
    private val grassY = FloatArray(grassN) { rnd.nextFloat() }
    private val grassT = FloatArray(grassN) { if (it < grassN * 0.12f) 0f else 1f + rnd.nextFloat() * 37f }
    private val crackN = 36
    private val crackX = FloatArray(crackN) { rnd.nextFloat() }
    private val crackY = FloatArray(crackN) { rnd.nextFloat() }
    private val crackL = FloatArray(crackN) { 0.05f + rnd.nextFloat() * 0.12f }
    private val patchN = 16
    private val patchX = FloatArray(patchN) { rnd.nextFloat() }
    private val patchY = FloatArray(patchN) { rnd.nextFloat() }
    private val patchS = FloatArray(patchN) { 0.08f + rnd.nextFloat() * 0.16f }
    private val flowerN = 90
    private val flowerX = FloatArray(flowerN) { rnd.nextFloat() }
    private val flowerY = FloatArray(flowerN) { rnd.nextFloat() }
    private val flowerC = IntArray(flowerN) { rnd.nextInt(5) }
    private val flowerT = FloatArray(flowerN) { 48f + rnd.nextFloat() * 45f }
    private val rainN = 120
    private val rainX = FloatArray(rainN) { rnd.nextFloat() }
    private val rainP = FloatArray(rainN) { rnd.nextFloat() }
    private val rainS = FloatArray(rainN) { 0.9f + rnd.nextFloat() * 0.8f }
    private val dustN = 26
    private val dustY = FloatArray(dustN) { rnd.nextFloat() }
    private val dustP = FloatArray(dustN) { rnd.nextFloat() }
    private val dustS = FloatArray(dustN) { 0.6f + rnd.nextFloat() * 1.2f }
    private val moteN = 54
    private val moteX = FloatArray(moteN) { rnd.nextFloat() }
    private val moteY = FloatArray(moteN) { rnd.nextFloat() }
    private val moteP = FloatArray(moteN) { rnd.nextFloat() }
    private val leafPh = FloatArray(8) { rnd.nextFloat() }
    private val flowerColors = intArrayOf(
        0xFFFFFFFF.toInt(), 0xFFF4C430.toInt(), 0xFFF08A3C.toInt(),
        0xFFEE7B96.toInt(), 0xFFB197E0.toInt()
    )
    private val leafColors = intArrayOf(0xFF7BC558.toInt(), 0xFFA6D45A.toInt(), 0xFFE2C34A.toInt())

    // trees: x, baseY, height
    private val trees = arrayOf(
        floatArrayOf(0.08f, 0.50f, 0.17f),
        floatArrayOf(0.25f, 0.45f, 0.10f),
        floatArrayOf(0.40f, 0.43f, 0.06f),
        floatArrayOf(0.65f, 0.43f, 0.06f),
        floatArrayOf(0.80f, 0.455f, 0.11f),
        floatArrayOf(0.95f, 0.50f, 0.17f)
    )

    private val river = Path()
    private val riverSeg = Path()
    private val pm = PathMeasure()
    private val riverPos = FloatArray(2)

    private val drops = ArrayList<Drop>()
    private val ripples = ArrayList<Ripple>()
    private val jets = ArrayList<Jet>()
    private val waves = ArrayList<Wave>()
    private val touches = ArrayList<Touch>()
    private val particles = ArrayList<Particle>() // screen-space sparkles
    private val splash = ArrayList<Particle>()    // pond-space droplets
    private val prnd = Random()
    private val nodeAt = LongArray(4)

    private val verses = arrayOf(
        arrayOf("يُرْسِلِ السَّمَاءَ عَلَيْكُم مِّدْرَارًا", "[نوح: 11]"),
        arrayOf("وَيَجْعَل لَّكُمْ جَنَّاتٍ", "[نوح: 12]"),
        arrayOf("وَيَجْعَل لَّكُمْ أَنْهَارًا", "[نوح: 12]"),
        arrayOf("اسْتَغْفِرُوا رَبَّكُمْ إِنَّهُ كَانَ غَفَّارًا", "[نوح: 10]  •  اضغط للبدء من جديد")
    )
    private var cardV: Array<String>? = null
    private var cardStart = 0L

    // ---------------- sound
    private val sounds: SoundPool = SoundPool.Builder()
        .setMaxStreams(14)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val dropIds = intArrayOf(
        sounds.load(context, R.raw.drop1, 1), sounds.load(context, R.raw.drop2, 1),
        sounds.load(context, R.raw.drop3, 1), sounds.load(context, R.raw.drop4, 1),
        sounds.load(context, R.raw.drop5, 1), sounds.load(context, R.raw.drop6, 1)
    )
    private val birdIds = intArrayOf(
        sounds.load(context, R.raw.bird1, 1), sounds.load(context, R.raw.bird2, 1)
    )
    private val chimeId = sounds.load(context, R.raw.chime, 1)
    private val bellId = sounds.load(context, R.raw.bell, 1)
    private val windLoop = sounds.load(context, R.raw.wind, 1)
    private val rainLoop = sounds.load(context, R.raw.rain, 1)
    private val streamLoop = sounds.load(context, R.raw.stream, 1)
    private var windId = 0
    private var rainId = 0
    private var streamId = 0
    private var ambCheck = 0L
    private var nextBird = SystemClock.uptimeMillis() + 4000
    private var lastDrop = -1

    private val resetRun = Runnable {
        count = 0
        cardV = null
        drops.clear()
        save()
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
    }

    fun save() {
        prefs.edit().putInt("count", count).putInt("max", maxCount).putBoolean("muted", muted).apply()
    }

    fun start() {
        accel?.let { sensors.registerListener(sensorListener, it, SensorManager.SENSOR_DELAY_GAME) }
        sounds.autoResume()
    }

    fun stop() {
        sensors.unregisterListener(sensorListener)
        sounds.autoPause()
    }

    fun release() {
        sounds.release()
    }

    private fun play(id: Int, rate: Float = 1f, vol: Float = 1f) {
        if (!muted) sounds.play(id, vol, vol, 1, 0, rate)
    }

    private fun ambience(now: Long, s: Float, rainAmt: Float) {
        if (now - ambCheck > 800) {
            ambCheck = now
            if (windId == 0) windId = sounds.play(windLoop, 0f, 0f, 1, -1, 1f)
            if (rainId == 0) rainId = sounds.play(rainLoop, 0f, 0f, 1, -1, 1f)
            if (streamId == 0) streamId = sounds.play(streamLoop, 0f, 0f, 1, -1, 1f)
        }
        val m = if (muted) 0f else 1f
        if (windId != 0) {
            val v = (1f - sm(s, 0f, 35f)) * 0.55f * m
            sounds.setVolume(windId, v, v)
        }
        if (rainId != 0) {
            val v = rainAmt * 0.75f * m
            sounds.setVolume(rainId, v, v)
        }
        if (streamId != 0) {
            val v = sm(s, 24f, 60f) * 0.5f * m
            sounds.setVolume(streamId, v, v)
        }
        if (s > 50f && !muted && now > nextBird) {
            play(birdIds[prnd.nextInt(birdIds.size)], 1f, 0.35f)
            nextBird = now + 6000 + prnd.nextInt(9000)
        }
    }

    // ---------------- helpers
    private fun cl(x: Float) = if (x < 0f) 0f else if (x > 1f) 1f else x

    private fun sm(x: Float, a: Float, b: Float): Float {
        val t = cl((x - a) / (b - a))
        return t * t * (3f - 2f * t)
    }

    /** ease-out-back: grows with a little overshoot, like a plant sprouting */
    private fun back(x: Float): Float {
        val k = 1.70158f
        val u = x - 1f
        return 1f + (k + 1f) * u * u * u + k * u * u
    }

    private fun grow(s: Float, thr: Float, span: Float): Float = back(cl((s - thr) / span))

    private fun lerpC(a: Int, b: Int, t: Float): Int {
        val f = cl(t)
        return Color.rgb(
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    private fun mix3(a: Int, b: Int, c: Int, s: Float): Int =
        lerpC(lerpC(a, b, sm(s, 0f, 25f)), c, sm(s, 40f, 50f))

    private fun fill(color: Int, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        p.alpha = alpha.coerceIn(0, 255)
    }

    private fun stroke(color: Int, width: Float, alpha: Int = 255) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        p.alpha = alpha.coerceIn(0, 255)
    }

    private fun vGrad(c: Canvas, l: Float, t: Float, r: Float, b: Float, c0: Int, c1: Int) {
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(l, t, l, b, c0, c1, Shader.TileMode.CLAMP)
        c.drawRect(l, t, r, b, gp)
        gp.shader = null
    }

    private fun glow(c: Canvas, x: Float, y: Float, r: Float, r8: Int, g8: Int, b8: Int, a: Int) {
        if (a <= 0) return
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            x, y, r, Color.argb(a.coerceIn(0, 255), r8, g8, b8), Color.argb(0, r8, g8, b8), Shader.TileMode.CLAMP
        )
        c.drawCircle(x, y, r, gp)
        gp.shader = null
    }

    private fun arabic(n: Int): String =
        n.toString().map { '٠' + (it - '0') }.joinToString("")

    private fun text(
        c: Canvas, s: String, x: Float, y: Float, size: Float, color: Int,
        align: Paint.Align = Paint.Align.CENTER, alpha: Int = 255
    ) {
        tp.textSize = size
        tp.typeface = Typeface.DEFAULT_BOLD
        tp.color = color
        tp.alpha = alpha.coerceIn(0, 255)
        tp.textAlign = align
        c.drawText(s, x, y, tp)
    }

    private fun fit(s: String, maxW: Float, size: Float): Float {
        tp.textSize = size
        tp.typeface = Typeface.DEFAULT_BOLD
        val m = tp.measureText(s)
        return if (m > maxW) size * maxW / m else size
    }

    private fun milestone(k: Int): Int = (maxCount * k / 4.0).roundToInt()

    // ---------------- layout
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val fw = w.toFloat()
        val fh = h.toFloat()
        river.reset()
        river.moveTo(0.50f * fw, 0.56f * fh)
        river.cubicTo(0.36f * fw, 0.63f * fh, 0.50f * fw, 0.71f * fh, 0.40f * fw, 0.79f * fh)
        river.cubicTo(0.33f * fw, 0.85f * fh, 0.26f * fw, 0.90f * fh, 0.18f * fw, 1.00f * fh)
        pm.setPath(river, false)
        maxPill.set(0.04f * fw, 0.020f * fh, 0.31f * fw, 0.058f * fh)
        mutePill.set(0.69f * fw, 0.020f * fh, 0.96f * fw, 0.058f * fh)
        vignette = RadialGradient(
            fw / 2f, fh / 2f, fh * 0.75f,
            Color.argb(0, 0, 0, 0), Color.argb(80, 0, 0, 0), Shader.TileMode.CLAMP
        )
    }

    // ---------------- input
    private fun tap(x: Float, y: Float) {
        val now = SystemClock.uptimeMillis()
        touches.add(Touch(x, y, now))
        if (count >= maxCount) {
            count = 0
            cardV = null
            save()
            return
        }
        count++
        save()
        pulse = 1f
        drops.add(Drop(height * 0.31f, height * 0.12f, now))
        for (k in 1..4) {
            if (count == milestone(k)) {
                cardV = verses[k - 1]
                cardStart = now
                nodeAt[k - 1] = now
                waves.add(Wave(now))
                play(if (k == 4) bellId else chimeId)
                burst(width * 0.5f, height * 0.36f, if (k == 4) 46 else 26)
                break
            }
        }
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    private fun cycleMax() {
        val i = MAX_OPTIONS.indexOf(maxCount)
        maxCount = MAX_OPTIONS[(i + 1) % MAX_OPTIONS.size]
        if (count >= maxCount) count = 0
        cardV = null
        save()
        performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (maxPill.contains(e.x, e.y)) {
                    cycleMax()
                } else if (mutePill.contains(e.x, e.y)) {
                    muted = !muted
                    save()
                } else {
                    tap(e.x, e.y)
                    postDelayed(resetRun, 2500) // hold 2.5s to reset
                }
            }
            MotionEvent.ACTION_POINTER_DOWN -> tap(e.getX(e.actionIndex), e.getY(e.actionIndex))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> removeCallbacks(resetRun)
        }
        return true
    }

    private fun burst(x: Float, y: Float, n: Int) {
        for (i in 0 until n) {
            val a = prnd.nextFloat() * 2f * PI.toFloat()
            val sp = width * (0.1f + prnd.nextFloat() * 0.35f)
            val col = if (prnd.nextBoolean()) 0xFFFFD968.toInt() else 0xFFFFF3C4.toInt()
            particles.add(
                Particle(
                    x, y, cos(a) * sp, sin(a) * sp, 0f, 0.9f + prnd.nextFloat() * 0.8f,
                    col, width * (0.005f + prnd.nextFloat() * 0.007f), height * 0.15f
                )
            )
        }
    }

    private fun stepParticles(c: Canvas, list: ArrayList<Particle>, dt: Float) {
        val it2 = list.iterator()
        while (it2.hasNext()) {
            val q2 = it2.next()
            q2.life += dt
            if (q2.life >= q2.maxLife) {
                it2.remove()
                continue
            }
            q2.vy += q2.grav * dt
            q2.x += q2.vx * dt
            q2.y += q2.vy * dt
            val f = 1f - q2.life / q2.maxLife
            fill(q2.color, (255 * f).toInt())
            c.drawCircle(q2.x, q2.y, q2.size * (0.4f + 0.6f * f), p)
        }
    }

    // ---------------- drawing
    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val now = SystemClock.uptimeMillis()
        val raw = (now - last) / 1000f
        if (raw < 0.25f) avgDt = avgDt * 0.95f + raw * 0.05f
        frameCnt++
        if (frameCnt % 90 == 0) {
            if (avgDt > 0.026f) q = max(0.5f, q - 0.15f) else if (avgDt < 0.02f) q = min(1f, q + 0.1f)
        }
        val dt = raw.coerceIn(0f, 0.05f)
        last = now
        val t = now / 1000f
        val target = count * 100f / maxCount
        shown += (target - shown) * min(1f, dt * 2.5f)
        val s = shown
        pulse = max(0f, pulse - dt * 4f)
        tilt += (tiltT - tilt) * min(1f, dt * 4f)
        val cam = tilt * 0.6f + sin(t * 0.23f) * 0.18f

        val gy = 0.41f * h
        val cx = 0.5f * w
        val cy = 0.525f * h
        val rx = 0.235f * w
        val ry = 0.042f * h
        val fgDx = cam * w * 0.012f

        val rainAmt = sm(s, 20f, 26f) * (1f - sm(s, 42f, 48f))
        val clear = sm(s, 40f, 50f)
        val doneAmt = sm(s, 92f, 100f)
        val desert = 1f - sm(s, 0f, 18f)

        ambience(now, s, rainAmt)

        // ===== sky
        val skyTop = mix3(0xFFC2A98A.toInt(), 0xFF6B7482.toInt(), 0xFF5AA7E2.toInt(), s)
        val skyBot = mix3(0xFFE9D3AB.toInt(), 0xFF9DA6B1.toInt(), 0xFFCBE9F8.toInt(), s)
        vGrad(c, 0f, 0f, w, gy + 0.03f * h, skyTop, skyBot)
        glow(c, 0.4f * w, gy - 0.02f * h, 0.8f * w, 255, 243, 200, (150 * clear + 50).toInt())

        // ===== sun and light shafts
        val sunA = sm(s, 45f, 58f)
        if (sunA > 0.01f) {
            val sx = 0.88f * w + cam * w * 0.004f
            val sy = 0.12f * h
            glow(c, sx, sy, w * 0.30f, 255, 244, 190, (210 * sunA).toInt())
            fill(0xFFFFF6C8.toInt(), (255 * sunA).toInt())
            c.drawCircle(sx, sy, w * 0.045f, p)
            fill(0xFFFFFFFF.toInt(), (200 * sunA).toInt())
            c.drawCircle(sx, sy, w * 0.03f, p)
            if (q > 0.7f) {
                val shaftA = 20f * sunA + 34f * doneAmt
                for (k in 0 until 8) {
                    val a0 = (0.55 + k * 0.17 + sin(t * 0.4 + k) * 0.03).toFloat() * PI.toFloat() * 0.9f
                    val a1 = a0 + 0.055f
                    val len = h * 0.75f
                    path.reset()
                    path.moveTo(sx, sy)
                    path.lineTo(sx + cos(a0) * len, sy + sin(a0) * len)
                    path.lineTo(sx + cos(a1) * len, sy + sin(a1) * len)
                    path.close()
                    fill(0xFFFFF3C0.toInt(), (shaftA * (0.6f + 0.4f * sin(t * 1.5f + k))).toInt())
                    c.drawPath(path, p)
                }
            }
        }

        // ===== rainbow
        if (doneAmt > 0.01f) {
            val rc = intArrayOf(
                0xFFE74C3C.toInt(), 0xFFF39C12.toInt(), 0xFFF1E05A.toInt(),
                0xFF5FBF5F.toInt(), 0xFF4A9BE0.toInt(), 0xFF8E5BD0.toInt()
            )
            for (k in rc.indices) {
                val r = w * (0.36f - k * 0.016f)
                rect.set(cx - r, 0.47f * h - r, cx + r, 0.47f * h + r)
                stroke(rc[k], w * 0.018f, (105 * doneAmt).toInt())
                c.drawArc(rect, 180f, 180f, false, p)
            }
        }

        // ===== clouds (continuously drifting)
        val cloudTop = lerpC(0xFFFFFFFF.toInt(), 0xFF7E8590.toInt(), rainAmt * 0.85f)
        val cloudBot = lerpC(0xFFDCE6F0.toInt(), 0xFF5C626C.toInt(), rainAmt * 0.9f)
        val cbase = floatArrayOf(0.15f, 0.45f, 0.78f, 0.98f)
        val cspd = floatArrayOf(0.006f, 0.009f, 0.004f, 0.007f)
        val cy2 = floatArrayOf(0.335f, 0.322f, 0.355f, 0.34f)
        val crr = floatArrayOf(0.07f, 0.10f, 0.06f, 0.075f)
        for (k in 0..3) {
            val xx = (((cbase[k] + t * cspd[k]) % 1.5f) - 0.25f) * w + cam * w * 0.006f
            cloud(c, xx, cy2[k] * h, crr[k] * w, cloudTop, cloudBot)
        }

        // ===== mountains with parallax
        val farTop = mix3(0xFFC7A68A.toInt(), 0xFF8C93A8.toInt(), 0xFFA6B3D6.toInt(), s)
        val farBot = mix3(0xFFE0C7A2.toInt(), 0xFFA9B0BC.toInt(), 0xFFC9D8EC.toInt(), s)
        c.save()
        c.translate(cam * w * 0.010f, 0f)
        path.reset()
        path.moveTo(-0.1f * w, 0.43f * h)
        path.lineTo(-0.1f * w, 0.375f * h)
        path.lineTo(0.12f * w, 0.335f * h)
        path.lineTo(0.28f * w, 0.372f * h)
        path.lineTo(0.47f * w, 0.325f * h)
        path.lineTo(0.62f * w, 0.368f * h)
        path.lineTo(0.80f * w, 0.33f * h)
        path.lineTo(1.1f * w, 0.372f * h)
        path.lineTo(1.1f * w, 0.43f * h)
        path.close()
        gp.style = Paint.Style.FILL
        gp.shader = LinearGradient(0f, 0.32f * h, 0f, 0.43f * h, farTop, farBot, Shader.TileMode.CLAMP)
        c.drawPath(path, gp)
        gp.shader = null
        c.restore()

        val nearTop = mix3(0xFFAB846A.toInt(), 0xFF747C97.toInt(), 0xFF8190BC.toInt(), s)
        val nearBot = mix3(0xFFCDAA84.toInt(), 0xFF98A0AE.toInt(), 0xFFAFC0DA.toInt(), s)
        c.save()
        c.translate(cam * w * 0.020f, 0f)
        path.reset()
        path.moveTo(-0.1f * w, 0.43f * h)
        path.lineTo(-0.1f * w, 0.40f * h)
        path.lineTo(0.18f * w, 0.375f * h)
        path.lineTo(0.38f * w, 0.405f * h)
        path.lineTo(0.55f * w, 0.382f * h)
        path.lineTo(0.75f * w, 0.408f * h)
        path.lineTo(0.92f * w, 0.38f * h)
        path.lineTo(1.1f * w, 0.40f * h)
        path.lineTo(1.1f * w, 0.43f * h)
        path.close()
        gp.shader = LinearGradient(0f, 0.37f * h, 0f, 0.43f * h, nearTop, nearBot, Shader.TileMode.CLAMP)
        c.drawPath(path, gp)
        gp.shader = null
        c.restore()

        // ===== ground
        val gFar = lerpC(0xFFE3C390.toInt(), 0xFFA6D170.toInt(), sm(s, 0f, 35f))
        val gNear = lerpC(0xFFC79A60.toInt(), 0xFF5CA042.toInt(), sm(s, 0f, 35f))
        vGrad(c, 0f, gy, w, h, gFar, gNear)
        for (i in 0 until patchN) {
            val x = patchX[i] * w
            val y = gy + 0.06f * h + patchY[i] * (h - gy - 0.12f * h)
            val rr = patchS[i] * w
            rect.set(x - rr, y - rr * 0.25f, x + rr, y + rr * 0.25f)
            fill(lerpC(0xFFA97C48.toInt(), 0xFF3F8A33.toInt(), sm(s, 0f, 35f)), 38)
            c.drawOval(rect, p)
        }
        if (rainAmt > 0.02f) {
            fill(0xFF1D3A52.toInt(), (46 * rainAmt).toInt())
            c.drawRect(0f, gy, w, h, p)
        }
        val haloA = (255 * (1f - sm(s, 5f, 35f))).toInt()
        if (haloA > 0) {
            gp.style = Paint.Style.FILL
            gp.shader = RadialGradient(
                cx, cy, rx * 1.6f,
                Color.argb(haloA, 125, 201, 78), Color.argb(0, 125, 201, 78), Shader.TileMode.CLAMP
            )
            rect.set(cx - rx * 1.6f, cy - ry * 3.2f, cx + rx * 1.6f, cy + ry * 3.4f)
            c.drawOval(rect, gp)
            gp.shader = null
        }
        val crackA = (170 * (1f - sm(s, 3f, 30f))).toInt()
        if (crackA > 0) {
            for (i in 0 until crackN) {
                val x = crackX[i] * w
                val y = gy + 0.04f * h + crackY[i] * (h - gy - 0.1f * h)
                val l = crackL[i] * w
                stroke(0xFF8A6838.toInt(), w * 0.0045f, crackA)
                c.drawLine(x, y, x + l, y + l * 0.12f, p)
                c.drawLine(x + l * 0.5f, y + l * 0.06f, x + l * 0.62f, y + l * 0.22f, p)
                stroke(0xFFF0D8A8.toInt(), w * 0.002f, crackA / 2)
                c.drawLine(x, y + w * 0.004f, x + l, y + l * 0.12f + w * 0.004f, p)
            }
        }

        // ===== desert life: blowing dust and a tumbleweed
        if (desert > 0.02f) {
            val dn = (dustN * q).toInt()
            for (i in 0 until dn) {
                val x = (((t * 0.05f * dustS[i] + dustP[i]) % 1.3f) - 0.15f) * w
                val y = gy + 0.05f * h + dustY[i] * (h - gy - 0.1f * h) + sin(t * 1.3f + i) * h * 0.004f
                stroke(0xFFFFF0D0.toInt(), w * 0.003f, (70 * desert).toInt())
                c.drawLine(x, y, x + w * 0.07f * dustS[i], y - h * 0.002f, p)
            }
            val tx = (((t * 0.06f) % 1.4f) - 0.2f) * w
            val rr = w * 0.05f
            val ty = h * 0.66f - abs(sin(t * 3.2f)) * h * 0.012f
            val rot = tx / rr
            rect.set(tx - rr, h * 0.66f + rr * 0.55f, tx + rr, h * 0.66f + rr * 0.75f)
            fill(0xFF000000.toInt(), (50 * desert).toInt())
            c.drawOval(rect, p)
            stroke(0xFF8B6A3E.toInt(), w * 0.003f, (210 * desert).toInt())
            c.drawCircle(tx, ty, rr, p)
            c.drawCircle(tx, ty, rr * 0.55f, p)
            for (k in 0 until 6) {
                val a = rot + k * PI.toFloat() / 6f
                c.drawLine(tx - cos(a) * rr, ty - sin(a) * rr, tx + cos(a) * rr, ty + sin(a) * rr, p)
            }
        }

        // ===== mist after the rain
        val mistA = sm(s, 22f, 36f) * (1f - sm(s, 55f, 78f))
        if (mistA > 0.02f) {
            glow(c, cx, cy + ry * 1.2f, rx * 2.1f, 235, 245, 250, (85 * mistA).toInt())
        }

        // ===== foreground group A (grass, river, pond) with parallax
        c.save()
        c.translate(fgDx, 0f)

        // grass (each blade sprouts at its own moment, then sways)
        val gcol1 = lerpC(0xFFA08752.toInt(), 0xFF3F8B34.toInt(), sm(s, 10f, 40f))
        val gcol2 = lerpC(0xFFB59B62.toInt(), 0xFF68B34B.toInt(), sm(s, 10f, 40f))
        val gl = (grassN * q).toInt()
        for (i in 0 until gl) {
            val gr = if (grassT[i] <= 0f) 1f else grow(s, grassT[i], 8f)
            if (gr < 0.03f) continue
            val y = gy + 0.02f * h + grassY[i] * (h - gy - 0.04f * h)
            val x = grassX[i] * w
            val sc = 0.5f + (y - gy) / (h - gy)
            val g = w * 0.015f * sc * gr
            val sw = sin(t * 1.7f + x * 0.02f) * g * 0.3f
            stroke(if (i % 2 == 0) gcol1 else gcol2, w * 0.0032f * sc)
            c.drawLine(x, y, x - g * 0.55f + sw, y - g, p)
            c.drawLine(x, y, x + sw * 1.2f, y - g * 1.25f, p)
            c.drawLine(x, y, x + g * 0.55f + sw, y - g * 0.95f, p)
        }

        // river
        val riverAmt = sm(s, 20f, 60f)
        if (riverAmt > 0.01f) {
            riverSeg.reset()
            pm.getSegment(0f, pm.length * riverAmt, riverSeg, true)
            riverSeg.rLineTo(0f, 0f)
            stroke(0xFF3F8A33.toInt(), w * 0.105f, 120)
            c.drawPath(riverSeg, p)
            stroke(0xFF3C82C4.toInt(), w * 0.080f)
            c.drawPath(riverSeg, p)
            stroke(0xFF5FAAE6.toInt(), w * 0.062f)
            c.drawPath(riverSeg, p)
            stroke(0xFF8CC8F4.toInt(), w * 0.030f, 150)
            c.drawPath(riverSeg, p)
            stroke(0xFFFFFFFF.toInt(), w * 0.007f, 170)
            p.pathEffect = DashPathEffect(floatArrayOf(w * 0.05f, w * 0.10f), -t * w * 0.22f)
            c.drawPath(riverSeg, p)
            p.pathEffect = null
            stroke(0xFFFFFFFF.toInt(), w * 0.004f, 120)
            p.pathEffect = DashPathEffect(floatArrayOf(w * 0.03f, w * 0.07f), -t * w * 0.34f + w * 0.02f)
            c.drawPath(riverSeg, p)
            p.pathEffect = null
        }

        // pond
        rect.set(cx - rx * 1.04f, cy - ry * 0.80f, cx + rx * 1.04f, cy + ry * 1.45f)
        fill(0xFF000000.toInt(), 55)
        c.drawOval(rect, p)
        rect.set(cx - rx, cy - ry, cx + rx, cy + ry)
        fill(0xFF8C7F73.toInt())
        c.drawOval(rect, p)
        for (k in 0 until 46) {
            val a = (2.0 * PI * k / 46.0).toFloat()
            if (sin(a) <= 0f) stone(c, cx + cos(a) * rx * 0.95f, cy + sin(a) * ry * 0.93f, w, k)
        }
        rect.set(cx - rx * 0.88f, cy - ry * 0.80f, cx + rx * 0.88f, cy + ry * 0.80f)
        fill(0xFF4A3828.toInt())
        c.drawOval(rect, p)
        rect.set(cx - rx * 0.78f, cy - ry * 0.68f, cx + rx * 0.78f, cy + ry * 0.68f)
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            cx - rx * 0.1f, cy - ry * 0.2f, rx * 0.85f,
            0xFF97D8F7.toInt(), 0xFF2E83CB.toInt(), Shader.TileMode.CLAMP
        )
        c.drawOval(rect, gp)
        gp.shader = null
        // tree reflections on the rim of the water
        val refl = sm(s, 35f, 60f)
        if (refl > 0.02f) {
            rect.set(cx - rx * 0.62f, cy - ry * 0.62f, cx - rx * 0.18f, cy - ry * 0.18f)
            fill(0xFF2F7A35.toInt(), (70 * refl).toInt())
            c.drawOval(rect, p)
            rect.set(cx + rx * 0.22f, cy - ry * 0.62f, cx + rx * 0.62f, cy - ry * 0.2f)
            c.drawOval(rect, p)
        }
        rect.set(cx - rx * 0.62f, cy - ry * 0.55f, cx + rx * 0.15f, cy - ry * 0.08f)
        fill(0xFFFFFFFF.toInt(), 60)
        c.drawOval(rect, p)
        for (k in 0..3) {
            val sx = cx + sin(t * 0.7f + k * 1.7f) * rx * 0.45f
            val sy = cy + (k - 1.5f) * ry * 0.28f
            stroke(0xFFFFFFFF.toInt(), w * 0.003f, 90)
            c.drawLine(sx - rx * 0.12f, sy, sx + rx * 0.12f, sy, p)
        }
        for (k in 0..2) {
            val f = ((t * 0.25f + k / 3f) % 1f)
            rect.set(cx - rx * 0.6f * f, cy - ry * 0.55f * f, cx + rx * 0.6f * f, cy + ry * 0.55f * f)
            stroke(0xFFFFFFFF.toInt(), w * 0.0025f, (110 * (1f - f)).toInt())
            c.drawOval(rect, p)
        }
        // rain falling on the pond
        if (rainAmt > 0.05f) {
            for (i in 0 until 10) {
                val ph = (t * 1.7f + rainP[(i * 7) % rainN]) % 1f
                val x = cx + (rainX[(i * 11) % rainN] - 0.5f) * rx * 1.4f
                val y = cy + (rainP[(i * 13) % rainN] - 0.5f) * ry * 1.1f
                rect.set(x - rx * 0.07f * ph, y - ry * 0.12f * ph, x + rx * 0.07f * ph, y + ry * 0.12f * ph)
                stroke(0xFFFFFFFF.toInt(), w * 0.002f, (170 * (1f - ph) * rainAmt).toInt())
                c.drawOval(rect, p)
            }
        }

        // impact ripples (shaded rings) and central jets
        var bob = 0f
        ripples.removeAll { now - it.start > 1400 }
        for (r in ripples) {
            val age = (now - r.start) / 1400f
            val k = if (r.small) 0.6f else 1f
            bob += sin(age * 22f) * (1f - age) * w * 0.0035f * k
            for (j in 0..2) {
                val f = age - j * 0.16f
                if (f <= 0f) continue
                val ff = 0.08f + f * 0.92f
                val a = (1f - age) * (1f - j * 0.28f)
                rect.set(cx - rx * 0.74f * ff * k, cy - ry * 0.66f * ff * k, cx + rx * 0.74f * ff * k, cy + ry * 0.66f * ff * k)
                rect.offset(0f, w * 0.0025f)
                stroke(0xFF1B5E9C.toInt(), w * 0.0055f, (80 * a).toInt())
                c.drawOval(rect, p)
                rect.offset(0f, -w * 0.0025f)
                stroke(0xFFFFFFFF.toInt(), w * 0.0035f, (235 * a).toInt())
                c.drawOval(rect, p)
            }
        }
        val jit = jets.iterator()
        while (jit.hasNext()) {
            val j = jit.next()
            val age = (now - j.start) / 1000f
            if (age >= 0.2f) {
                jit.remove()
                ripples.add(Ripple(now, true))
                continue
            }
            val g = h * 3f
            val v = g * 0.1f
            val dy = v * age - 0.5f * g * age * age
            val topY = cy - dy
            val wd = w * 0.010f * (1f - 0.6f * age / 0.2f)
            if (dy > 2f) {
                stroke(0xFFCDEBFB.toInt(), wd, 230)
                c.drawLine(cx, cy, cx, topY + w * 0.004f, p)
            }
            fill(0xFFE4F5FF.toInt())
            c.drawCircle(cx, topY, w * 0.0075f, p)
        }

        // lily pads bob on the ripples
        val lily = sm(s, 60f, 80f)
        if (lily > 0.02f) {
            val lx = floatArrayOf(-0.45f, 0.42f, 0.05f)
            val ly = floatArrayOf(0.15f, -0.2f, 0.38f)
            for (k in 0..2) {
                val px = cx + lx[k] * rx
                val py = cy + ly[k] * ry + bob * (0.6f + k * 0.2f)
                rect.set(px - rx * 0.10f * lily, py - ry * 0.16f * lily, px + rx * 0.10f * lily, py + ry * 0.16f * lily)
                fill(0xFF3E9A47.toInt())
                c.drawOval(rect, p)
                fill(0xFF67BF5E.toInt(), 200)
                rect.inset(rect.width() * 0.18f, rect.height() * 0.18f)
                c.drawOval(rect, p)
            }
            val fyy = cy + ly[1] * ry - ry * 0.05f + bob * 0.8f
            fill(0xFFF7A8C8.toInt(), (255 * lily).toInt())
            c.drawCircle(cx + lx[1] * rx, fyy, w * 0.014f * lily, p)
            fill(0xFFFFE08A.toInt(), (255 * lily).toInt())
            c.drawCircle(cx + lx[1] * rx, fyy, w * 0.005f * lily, p)
        }
        // fish
        val fish = sm(s, 85f, 100f)
        if (fish > 0.02f) {
            for (k in 0..1) {
                val spd = 0.7f + k * 0.25f
                val a = t * spd + k * 3f
                val fx = cx + cos(a) * rx * 0.5f
                val fy = cy + sin(a) * ry * 0.42f
                val sgn = if (-sin(a) >= 0f) 1f else -1f
                rect.set(fx - w * 0.022f, fy - w * 0.008f, fx + w * 0.022f, fy + w * 0.008f)
                fill(0xFFF28C38.toInt(), (230 * fish).toInt())
                c.drawOval(rect, p)
                path.reset()
                path.moveTo(fx - sgn * w * 0.018f, fy)
                path.lineTo(fx - sgn * w * 0.036f, fy - w * 0.010f + sin(t * 9f) * w * 0.003f)
                path.lineTo(fx - sgn * w * 0.036f, fy + w * 0.010f + sin(t * 9f) * w * 0.003f)
                path.close()
                c.drawPath(path, p)
            }
        }
        // sparkling glints on water
        if (clear > 0.3f && q > 0.7f) {
            val gx = floatArrayOf(-0.5f, -0.2f, 0.1f, 0.35f, 0.55f)
            val gyy = floatArrayOf(0.1f, -0.3f, 0.25f, -0.15f, 0.2f)
            for (k in 0..4) {
                val tw = max(0f, sin(t * 2.1f + k * 1.9f)).pow(3)
                glint(c, cx + gx[k] * rx * 0.7f, cy + gyy[k] * ry * 0.7f, w * 0.014f, (230 * tw * clear).toInt())
            }
            if (riverAmt > 0.3f) {
                for (k in 0..5) {
                    val f = (k * 0.17f + t * 0.04f) % 1f
                    pm.getPosTan(pm.length * riverAmt * f, riverPos, null)
                    val tw = max(0f, sin(t * 2.6f + k * 1.3f)).pow(3)
                    glint(c, riverPos[0], riverPos[1], w * 0.014f, (230 * tw * clear).toInt())
                }
            }
        }
        for (k in 0 until 46) {
            val a = (2.0 * PI * k / 46.0).toFloat()
            if (sin(a) > 0f) stone(c, cx + cos(a) * rx * 0.95f, cy + sin(a) * ry * 0.93f, w, k)
        }
        c.restore()

        // ===== trees (each depth layer has its own parallax)
        val fruit = sm(s, 75f, 95f)
        for (tr in trees) {
            c.save()
            c.translate(cam * w * (0.010f + tr[2] * 0.15f), 0f)
            tree(c, tr[0] * w, tr[1] * h, tr[2] * h, s, fruit, w, t)
            c.restore()
        }

        // ===== flowers bloom one by one
        c.save()
        c.translate(fgDx, 0f)
        val fn = (flowerN * q).toInt()
        for (i in 0 until fn) {
            val gr = grow(s, flowerT[i], 7f)
            if (gr < 0.03f) continue
            val y = gy + 0.05f * h + flowerY[i] * (h - gy - 0.1f * h)
            val x = flowerX[i] * w
            val sc = 0.5f + (y - gy) / (h - gy)
            val r = w * 0.007f * sc * gr
            val sw = sin(t * 1.5f + x * 0.03f) * r * 0.4f
            stroke(0xFF3F8B34.toInt(), w * 0.002f * sc)
            c.drawLine(x, y + r * 2.5f, x + sw, y, p)
            fill(flowerColors[flowerC[i]])
            for (a in 0 until 5) {
                val ang = (a * 2 * PI / 5).toFloat() + t * 0.1f
                c.drawCircle(x + sw + cos(ang) * r, y + sin(ang) * r, r * 0.85f, p)
            }
            fill(0xFFF7D84A.toInt())
            c.drawCircle(x + sw, y, r * 0.7f, p)
        }
        c.restore()

        // ===== birds, butterflies, falling leaves, light motes
        val birds = sm(s, 50f, 60f)
        if (birds > 0.01f) {
            for (k in 0..2) {
                val bx = ((t * 0.03f * (1f + k * 0.2f) + k * 0.31f) % 1.2f - 0.1f) * w
                val by = h * (0.305f + k * 0.012f) + sin(t * 1.3f + k) * h * 0.006f
                val fl = sin(t * 9f + k * 2f) * w * 0.008f
                stroke(0xFF3C4350.toInt(), w * 0.003f, (200 * birds).toInt())
                path.reset()
                path.moveTo(bx - w * 0.016f, by - fl)
                path.quadTo(bx - w * 0.006f, by - w * 0.008f, bx, by)
                path.quadTo(bx + w * 0.006f, by - w * 0.008f, bx + w * 0.016f, by - fl)
                c.drawPath(path, p)
            }
        }
        val butter = sm(s, 70f, 85f)
        if (butter > 0.01f) {
            for (k in 0..4) {
                val bx = (0.15f + k * 0.18f) * w + sin(t * (0.6f + k * 0.13f) + k) * w * 0.07f
                val by = h * (0.60f + k * 0.055f) + cos(t * (0.8f + k * 0.1f) + k * 2f) * h * 0.02f
                val flap = abs(sin(t * 11f + k * 1.7f))
                val ww = w * 0.016f * (0.25f + 0.75f * flap)
                fill(flowerColors[(k + 1) % 5], (240 * butter).toInt())
                rect.set(bx - ww - w * 0.002f, by - w * 0.012f, bx - w * 0.002f, by + w * 0.004f)
                c.drawOval(rect, p)
                rect.set(bx + w * 0.002f, by - w * 0.012f, bx + ww + w * 0.002f, by + w * 0.004f)
                c.drawOval(rect, p)
                rect.set(bx - ww * 0.7f - w * 0.002f, by, bx - w * 0.002f, by + w * 0.011f)
                c.drawOval(rect, p)
                rect.set(bx + w * 0.002f, by, bx + ww * 0.7f + w * 0.002f, by + w * 0.011f)
                c.drawOval(rect, p)
                stroke(0xFF3A2A20.toInt(), w * 0.0025f)
                c.drawLine(bx, by - w * 0.010f, bx, by + w * 0.010f, p)
            }
        }
        val leafAmt = sm(s, 65f, 80f)
        if (leafAmt > 0.02f) {
            for (i in 0 until 8) {
                val tr = trees[i % 6]
                val ph = (t * 0.05f + leafPh[i]) % 1f
                val lx = tr[0] * w + sin(ph * 9f + i) * w * 0.05f + ph * w * 0.06f
                val ly = (tr[1] - tr[2] * 0.9f) * h + ph * h * 0.2f
                val al = (sin(ph * PI.toFloat()) * 230 * leafAmt).toInt()
                c.save()
                c.rotate(ph * 720f + i * 40f, lx, ly)
                rect.set(lx - w * 0.008f, ly - w * 0.0035f, lx + w * 0.008f, ly + w * 0.0035f)
                fill(leafColors[i % 3], al)
                c.drawOval(rect, p)
                c.restore()
            }
        }
        val moteA = sm(s, 52f, 70f)
        if (moteA > 0.02f) {
            val mn = (moteN * q).toInt()
            for (i in 0 until mn) {
                val x = moteX[i] * w + sin(t * 0.4f + moteP[i] * 6f) * w * 0.03f
                val y = 0.30f * h + moteY[i] * 0.55f * h + cos(t * 0.3f + moteP[i] * 5f) * h * 0.01f
                val tw = 0.4f + 0.6f * sin(t * 1.2f + moteP[i] * 10f)
                fill(0xFFFFF4C8.toInt(), (110 * moteA * max(0f, tw)).toInt())
                c.drawCircle(x, y, w * 0.0035f, p)
            }
        }

        // ===== rain (three depth layers)
        if (rainAmt > 0.02f) {
            val rn = (rainN * q).toInt()
            for (i in 0 until rn) {
                val layer = i % 3
                val sp = rainS[i] * (0.8f + layer * 0.5f)
                val x = rainX[i] * w
                val y = ((t * sp + rainP[i]) % 1f) * h
                val len = h * (0.018f + 0.012f * layer)
                stroke(0xFFEAF2FA.toInt(), w * (0.002f + 0.0012f * layer), ((70 + 45 * layer) * rainAmt).toInt())
                c.drawLine(x, y, x - w * 0.006f * (1f + layer * 0.4f), y + len, p)
            }
            for (i in 0 until 26) {
                val ph = (t * 1.3f + rainP[i]) % 1f
                val x = rainX[(i * 3) % rainN] * w
                val y = gy + 0.07f * h + rainP[(i * 5) % rainN] * (h - gy - 0.14f * h)
                val rr = w * 0.014f * ph
                rect.set(x - rr, y - rr * 0.35f, x + rr, y + rr * 0.35f)
                stroke(0xFFFFFFFF.toInt(), w * 0.0022f, (170 * (1f - ph) * rainAmt).toInt())
                c.drawOval(rect, p)
            }
        }

        // ===== falling drops, crown splash
        c.save()
        c.translate(fgDx, 0f)
        val dit = drops.iterator()
        while (dit.hasNext()) {
            val d = dit.next()
            d.vy += h * 3.2f * dt
            d.y += d.vy * dt
            if (d.y >= cy) {
                dit.remove()
                ripples.add(Ripple(now, false))
                jets.add(Jet(now))
                var id = prnd.nextInt(dropIds.size)
                if (id == lastDrop) id = (id + 1) % dropIds.size
                lastDrop = id
                play(dropIds[id], 0.97f + prnd.nextFloat() * 0.06f)
                val cn = (13 * q).toInt() + 4
                for (k in 0 until cn) {
                    val a = (k * 2.0 * PI / cn).toFloat() + prnd.nextFloat() * 0.3f
                    val sp = w * (0.07f + prnd.nextFloat() * 0.06f)
                    splash.add(
                        Particle(
                            cx, cy, cos(a) * sp, -h * (0.05f + prnd.nextFloat() * 0.05f) + sin(a) * sp * 0.2f,
                            0f, 0.55f, 0xFFD4EEFB.toInt(), w * (0.003f + prnd.nextFloat() * 0.004f), h * 0.45f
                        )
                    )
                }
            } else {
                val grown = 0.3f + 0.7f * cl((now - d.start) / 120f)
                drawDrop(c, cx, d.y, w * 0.022f * grown, d.vy, h)
            }
        }
        stepParticles(c, splash, dt)
        c.restore()

        // ===== touch rings, growth waves, sparkles
        val tit = touches.iterator()
        while (tit.hasNext()) {
            val tc = tit.next()
            val age = (now - tc.start) / 500f
            if (age >= 1f) {
                tit.remove()
                continue
            }
            glow(c, tc.x, tc.y, w * 0.10f * (0.4f + age), 255, 255, 255, (70 * (1f - age)).toInt())
            stroke(0xFFFFFFFF.toInt(), w * 0.004f, (190 * (1f - age)).toInt())
            c.drawCircle(tc.x, tc.y, w * (0.02f + 0.07f * age), p)
        }
        val wit = waves.iterator()
        while (wit.hasNext()) {
            val wv = wit.next()
            val age = (now - wv.start) / 1500f
            if (age >= 1f) {
                wit.remove()
                continue
            }
            val r = w * (0.15f + 1.1f * age)
            rect.set(cx + fgDx - r, cy - r * 0.42f, cx + fgDx + r, cy + r * 0.42f)
            stroke(0xFFFFE49A.toInt(), w * 0.014f * (1f - age), (170 * (1f - age)).toInt())
            c.drawOval(rect, p)
            rect.inset(-w * 0.03f, -w * 0.012f)
            stroke(0xFFFFFFFF.toInt(), w * 0.005f, (110 * (1f - age)).toInt())
            c.drawOval(rect, p)
        }
        if (doneAmt > 0.5f && prnd.nextFloat() < dt * 25f * q) {
            particles.add(
                Particle(
                    prnd.nextFloat() * w, gy + prnd.nextFloat() * (h - gy) * 0.9f,
                    (prnd.nextFloat() - 0.5f) * w * 0.03f, -h * 0.05f, 0f, 2.2f,
                    0xFFFFE9A0.toInt(), w * (0.003f + prnd.nextFloat() * 0.004f), 0f
                )
            )
        }
        stepParticles(c, particles, dt)

        // golden glow when complete + vignette
        if (doneAmt > 0.01f) {
            fill(0xFFFFE19A.toInt(), (38 * doneAmt * (0.7f + 0.3f * sin(t * 2f))).toInt())
            c.drawRect(0f, 0f, w, h, p)
        }
        gp.style = Paint.Style.FILL
        gp.shader = vignette
        c.drawRect(0f, 0f, w, h, gp)
        gp.shader = null

        drawHud(c, w, h, now)
        postInvalidateOnAnimation()
    }

    private fun glint(c: Canvas, x: Float, y: Float, sz: Float, a: Int) {
        if (a <= 4) return
        stroke(0xFFFFFFFF.toInt(), sz * 0.16f, a)
        c.drawLine(x - sz, y, x + sz, y, p)
        c.drawLine(x, y - sz, x, y + sz, p)
        stroke(0xFFFFFFFF.toInt(), sz * 0.09f, a / 2)
        c.drawLine(x - sz * 0.55f, y - sz * 0.55f, x + sz * 0.55f, y + sz * 0.55f, p)
        c.drawLine(x - sz * 0.55f, y + sz * 0.55f, x + sz * 0.55f, y - sz * 0.55f, p)
        fill(0xFFFFFFFF.toInt(), a)
        c.drawCircle(x, y, sz * 0.2f, p)
    }

    private fun stone(c: Canvas, x: Float, y: Float, w: Float, k: Int) {
        val rw = w * (0.021f + (k % 3) * 0.002f)
        val rh = w * 0.0135f
        rect.set(x - rw - w * 0.002f, y - rh + w * 0.003f, x + rw + w * 0.002f, y + rh + w * 0.003f)
        fill(0xFF51483F.toInt())
        c.drawOval(rect, p)
        rect.set(x - rw, y - rh, x + rw, y + rh)
        fill(if (k % 2 == 0) 0xFFC4B8AA.toInt() else 0xFFA79A8C.toInt())
        c.drawOval(rect, p)
        rect.set(x - rw * 0.7f, y - rh * 0.8f, x + rw * 0.1f, y - rh * 0.05f)
        fill(0xFFEFE8DC.toInt(), 150)
        c.drawOval(rect, p)
    }

    private fun cloud(c: Canvas, x: Float, y: Float, r: Float, top: Int, bot: Int) {
        fill(bot)
        c.drawCircle(x, y + r * 0.12f, r, p)
        c.drawCircle(x - r * 0.95f, y + r * 0.40f, r * 0.7f, p)
        c.drawCircle(x + r * 0.95f, y + r * 0.35f, r * 0.78f, p)
        rect.set(x - r * 1.5f, y + r * 0.25f, x + r * 1.6f, y + r * 1.05f)
        c.drawRoundRect(rect, r * 0.4f, r * 0.4f, p)
        fill(top)
        c.drawCircle(x, y - r * 0.05f, r * 0.95f, p)
        c.drawCircle(x - r * 0.92f, y + r * 0.25f, r * 0.62f, p)
        c.drawCircle(x + r * 0.92f, y + r * 0.2f, r * 0.7f, p)
        rect.set(x - r * 1.45f, y + r * 0.15f, x + r * 1.55f, y + r * 0.8f)
        c.drawRoundRect(rect, r * 0.35f, r * 0.35f, p)
    }

    private fun leafBlob(c: Canvas, x: Float, y: Float, r: Float) {
        fill(0xFF3B8A38.toInt())
        c.drawCircle(x + r * 0.1f, y + r * 0.15f, r, p)
        fill(0xFF58B047.toInt())
        c.drawCircle(x, y, r * 0.92f, p)
        fill(0xFF82CF60.toInt())
        c.drawCircle(x - r * 0.25f, y - r * 0.28f, r * 0.55f, p)
        fill(0xFFB6E88C.toInt(), 150)
        c.drawCircle(x - r * 0.4f, y - r * 0.45f, r * 0.22f, p)
        fill(0xFF3B8A38.toInt(), 150)
        c.drawCircle(x + r * 0.35f, y + r * 0.25f, r * 0.2f, p)
    }

    private fun tree(
        c: Canvas, x: Float, by: Float, th: Float, s: Float, fruit: Float, w: Float, t: Float
    ) {
        val leafAll = sm(s, 28f, 55f)
        val sway = sin(t * 1.2f + x * 0.01f) * th * 0.02f * (0.4f + leafAll)
        val topX = x + sway
        val topY = by - th
        val tw = th * 0.09f
        rect.set(x - th * 0.38f, by - th * 0.03f, x + th * 0.38f, by + th * 0.05f)
        fill(0xFF000000.toInt(), 45)
        c.drawOval(rect, p)
        path.reset()
        path.moveTo(x - tw, by)
        path.lineTo(topX - tw * 0.3f, topY)
        path.lineTo(topX + tw * 0.3f, topY)
        path.lineTo(x + tw, by)
        path.close()
        fill(0xFF6A4A36.toInt())
        c.drawPath(path, p)
        path.reset()
        path.moveTo(x - tw, by)
        path.lineTo(topX - tw * 0.3f, topY)
        path.lineTo(topX, topY)
        path.lineTo(x - tw * 0.1f, by)
        path.close()
        fill(0xFF8A6446.toInt())
        c.drawPath(path, p)

        val ex = floatArrayOf(-0.30f, 0.30f, -0.17f, 0.17f)
        val ey = floatArrayOf(0.88f, 0.88f, 1.03f, 1.03f)
        val es = floatArrayOf(0.40f, 0.40f, 0.66f, 0.66f)
        stroke(0xFF6A4A36.toInt(), th * 0.045f)
        for (i in 0..3) {
            val sx = x + (topX - x) * es[i]
            val sy = by - th * es[i]
            c.drawLine(sx, sy, x + ex[i] * th + sway, by - ey[i] * th, p)
        }
        if (leafAll > 0.01f) {
            val r = th * 0.19f
            // every blob of foliage sprouts at its own moment with a little overshoot
            for (i in 0..3) {
                val g = grow(s, 28f + i * 4f, 18f)
                if (g > 0.02f) leafBlob(c, x + ex[i] * th + sway, by - ey[i] * th, r * g)
            }
            val g4 = grow(s, 40f, 18f)
            if (g4 > 0.02f) leafBlob(c, topX, topY - r * 0.3f, r * 1.15f * g4)
            val g5 = grow(s, 34f, 18f)
            if (g5 > 0.02f) leafBlob(c, x + sway * 0.7f, by - th * 0.72f, r * 0.8f * g5)
            if (fruit > 0.02f) {
                val fr = th * 0.022f * fruit
                for (i in 0..3) {
                    fill(if (i % 2 == 0) 0xFFE8503A.toInt() else 0xFFF2A33A.toInt())
                    c.drawCircle(x + ex[i] * th + sway + r * 0.35f, by - ey[i] * th + r * 0.3f, fr, p)
                    c.drawCircle(x + ex[i] * th + sway - r * 0.4f, by - ey[i] * th - r * 0.1f, fr, p)
                }
                fill(0xFFF2A33A.toInt())
                c.drawCircle(topX + r * 0.5f, topY, fr, p)
            }
        }
    }

    private fun drawDrop(c: Canvas, x: Float, y: Float, r: Float, vy: Float, h: Float) {
        val st = 1f + min(0.8f, vy / (h * 1.6f))
        val tail = r * 2.6f * st
        glow(c, x, y - r, r * 3.2f, 190, 230, 255, 90)
        stroke(0xFFBFE6FA.toInt(), r * 0.5f, 60)
        c.drawLine(x, y - tail - r * 5f * st, x, y - tail, p)
        path.reset()
        path.moveTo(x, y - tail)
        path.lineTo(x - r * 0.92f, y - r * 0.35f)
        path.lineTo(x + r * 0.92f, y - r * 0.35f)
        path.close()
        gp.style = Paint.Style.FILL
        gp.shader = RadialGradient(
            x - r * 0.3f, y - r * 0.3f, r * 1.6f,
            0xFFBEE8FF.toInt(), 0xFF2F8FDB.toInt(), Shader.TileMode.CLAMP
        )
        c.drawPath(path, gp)
        c.drawCircle(x, y, r, gp)
        gp.shader = null
        stroke(0xFFFFFFFF.toInt(), r * 0.2f, 220)
        rect.set(x - r * 0.6f, y - r * 0.6f, x + r * 0.6f, y + r * 0.6f)
        c.drawArc(rect, 110f, 60f, false, p)
    }

    private fun pill(c: Canvas, r: RectF, label: String, w: Float) {
        fill(0xFFFFFFFF.toInt(), 105)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p)
        stroke(0xFFFFFFFF.toInt(), w * 0.0025f, 190)
        c.drawRoundRect(r, r.height() / 2f, r.height() / 2f, p)
        val sz = fit(label, r.width() * 0.84f, w * 0.038f)
        text(c, label, r.centerX(), r.centerY() + sz * 0.33f, sz, 0xFF2B1D14.toInt())
    }

    private fun drawHud(c: Canvas, w: Float, h: Float, now: Long) {
        val ink = 0xFF2B1D14.toInt()
        val brown = 0xFF3E2D20.toInt()
        val t = now / 1000f

        pill(c, maxPill, "الحد: " + arabic(maxCount), w)
        pill(c, mutePill, if (muted) "الصوت: صامت" else "الصوت: يعمل", w)

        tp.setShadowLayer(w * 0.012f, 0f, 0f, 0xCCFFFFFF.toInt())
        text(c, "قولها مع كل نقطة", w * 0.5f, h * 0.095f, w * 0.045f, brown)
        text(c, "أستغفر الله", w * 0.5f, h * 0.170f, w * 0.17f, ink)

        val num = arabic(count)
        val numX = w * 0.54f
        val numSize = fit(num, w * 0.46f, w * 0.24f)
        val numY = h * 0.235f
        c.save()
        val sc = 1f + 0.10f * pulse
        c.scale(sc, sc, numX, numY - numSize * 0.35f)
        text(c, num, numX, numY, numSize, ink)
        val nw = tp.measureText(num)
        c.restore()
        val maxS = arabic(maxCount)
        val rEdge = numX - nw / 2f - w * 0.03f
        text(c, maxS, rEdge, numY, w * 0.06f, brown, Paint.Align.RIGHT)
        val mw = tp.measureText(maxS)
        text(c, "/", rEdge - mw - w * 0.02f, numY, w * 0.06f, brown, Paint.Align.RIGHT)
        tp.clearShadowLayer()

        // progress bar (fills right -> left)
        val left = 0.10f * w
        val right = 0.90f * w
        val by = h * 0.268f
        stroke(0xFF000000.toInt(), w * 0.022f, 40)
        c.drawLine(left, by + w * 0.004f, right, by + w * 0.004f, p)
        stroke(0xFFF5EFE0.toInt(), w * 0.019f)
        c.drawLine(left, by, right, by, p)
        val fx = right - (shown / 100f) * (right - left)
        if (fx < right - 1f) {
            gp.style = Paint.Style.STROKE
            gp.strokeCap = Paint.Cap.ROUND
            gp.strokeWidth = w * 0.019f
            gp.shader = LinearGradient(fx, by, right, by, 0xFF7CC4F5.toInt(), 0xFF3F86CE.toInt(), Shader.TileMode.CLAMP)
            c.drawLine(fx, by, right, by, gp)
            gp.shader = null
            gp.style = Paint.Style.FILL
            // moving shine
            c.save()
            c.clipRect(fx, by - w * 0.02f, right, by + w * 0.02f)
            val span = right - left
            val sx = left + ((t * 0.45f) % 1.5f) * span * 1.3f - span * 0.2f
            stroke(0xFFFFFFFF.toInt(), w * 0.009f, 110)
            c.drawLine(sx, by - w * 0.002f, sx + w * 0.07f, by - w * 0.002f, p)
            c.restore()
        }
        val names = arrayOf("مطر", "جنات", "أنهار", "مغفرة")
        for (k in 0..3) {
            val mx = right - (k + 1) * 0.25f * (right - left)
            val reached = count >= milestone(k + 1)
            val age = (now - nodeAt[k]) / 650f
            val pop = if (age in 0f..1f) 1f + 0.7f * sin(age * PI.toFloat()) * (1f - age * 0.5f) else 1f
            if (reached) {
                fill(0xFFFFD968.toInt(), 90)
                c.drawCircle(mx, by, w * 0.036f * pop, p)
            }
            fill(if (reached) 0xFFE0A82E.toInt() else 0xFFFFFFFF.toInt())
            c.drawCircle(mx, by, w * 0.021f * pop, p)
            stroke(if (reached) 0xFFB9851A.toInt() else 0xFFBBBBBB.toInt(), w * 0.003f)
            c.drawCircle(mx, by, w * 0.021f * pop, p)
            text(c, names[k], mx, h * 0.293f, w * 0.036f,
                if (reached) 0xFF8A6A1F.toInt() else 0xFF6B5444.toInt())
        }

        // verse card
        val v = cardV
        if (v != null) {
            val age = (now - cardStart).toFloat()
            val done = count >= maxCount
            var a = cl(age / 300f)
            if (!done) a = min(a, cl((4500f - age) / 500f))
            if (!done && age > 4500f) cardV = null
            if (a > 0f) {
                val al = (255 * a).toInt()
                val slide = (1f - a) * h * 0.012f
                rect.set(w * 0.14f, h * 0.318f + slide, w * 0.86f, h * 0.408f + slide)
                rect.offset(0f, w * 0.01f)
                fill(0xFF000000.toInt(), (60 * a).toInt())
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)
                rect.offset(0f, -w * 0.01f)
                gp.style = Paint.Style.FILL
                gp.shader = LinearGradient(
                    0f, rect.top, 0f, rect.bottom,
                    Color.argb(al, 255, 252, 240), Color.argb(al, 255, 240, 205), Shader.TileMode.CLAMP
                )
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, gp)
                gp.shader = null
                stroke(0xFFD4A937.toInt(), w * 0.004f, al)
                c.drawRoundRect(rect, w * 0.04f, w * 0.04f, p)
                rect.inset(w * 0.012f, w * 0.012f)
                stroke(0xFFE8CC7A.toInt(), w * 0.0018f, al)
                c.drawRoundRect(rect, w * 0.03f, w * 0.03f, p)
                val vs = fit(v[0], w * 0.62f, w * 0.075f)
                text(c, v[0], w * 0.5f, h * 0.361f + slide, vs, 0xFF8A6A1F.toInt(), alpha = al)
                val rs = fit(v[1], w * 0.62f, w * 0.038f)
                text(c, v[1], w * 0.5f, h * 0.395f + slide, rs, 0xFF3E2D20.toInt(), alpha = al)
            }
        }

        tp.setShadowLayer(w * 0.01f, 0f, 0f, Color.BLACK)
        text(c, DEVELOPER, w * 0.5f, h * 0.975f, w * 0.036f, Color.WHITE, alpha = 225)
        tp.clearShadowLayer()
    }
}
