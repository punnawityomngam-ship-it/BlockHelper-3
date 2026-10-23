package com.beam.blockhelper

import android.app.*
import android.content.*
import android.content.pm.ServiceInfo
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class OverlayView(c: Context) : View(c) {
    var plan: Solver.Plan? = null; var cells: List<List<IntArray>?> = listOf(); var s = 1f; var msg: String? = null; var info: String? = null
    private val cols = intArrayOf(0xFFE9808A.toInt(), 0xFF6FB4E0.toInt(), 0xFF7FCF8F.toInt())
    private val st = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fl = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tx = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; isFakeBoldText = true; color = Color.BLACK }
    override fun onDraw(cv: Canvas) {
        info?.let { tx.color = Color.YELLOW; tx.textSize = 30 * s; cv.drawText(it, width / 2f, 90 * s, tx); tx.color = Color.BLACK }
        val p = plan
        if (p == null) { msg?.let { tx.color = Color.RED; tx.textSize = 64 * s; cv.drawText(it, width / 2f, 220 * s, tx); tx.color = Color.BLACK }; return }
        val cs = 119.4f * s; val x0 = 62f * s; val y0 = 582f * s
        st.strokeWidth = 10 * s; tx.textSize = 40 * s
        p.steps.forEachIndexed { i, stp ->
            val cl = cells[stp.p] ?: return@forEachIndexed
            st.color = cols[stp.p]; fl.color = cols[stp.p]
            for (c in cl) {
                val x = x0 + (stp.c + c[1]) * cs; val y = y0 + (stp.r + c[0]) * cs
                cv.drawRect(x + 8 * s, y + 8 * s, x + cs - 8 * s, y + cs - 8 * s, st)
            }
            val f = cl[0]; val fx = x0 + (stp.c + f[1]) * cs + 28 * s; val fy = y0 + (stp.r + f[0]) * cs + 28 * s
            cv.drawCircle(fx, fy, 24 * s, fl); cv.drawText("${i + 1}", fx, fy + 14 * s, tx)
            val tx0 = (stp.p * 360 + 180) * s; val ty0 = 1565 * s
            cv.drawCircle(tx0, ty0, 24 * s, fl); cv.drawText("${i + 1}", tx0, ty0 + 14 * s, tx)
        }
        tx.textSize = 38 * s
        cv.drawText("ล้าง ${p.lines} เส้น · คอมโบ ${p.cb}", width / 2f, 540 * s, tx)
    }
}

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var view: OverlayView
    private var mp: MediaProjection? = null
    private var vd: VirtualDisplay? = null
    private lateinit var reader: ImageReader
    private val ex = Executors.newSingleThreadScheduledExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private var last = ""

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val ch = NotificationChannel("bh", "Block Helper", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        val n = Notification.Builder(this, "bh").setContentTitle("Block Helper กำลังทำงาน")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()
        startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        if (::view.isInitialized) return START_NOT_STICKY
        val data = i?.getParcelableExtra<Intent>("data") ?: return START_NOT_STICKY
        val code = i.getIntExtra("code", Activity.RESULT_CANCELED)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val b = wm.maximumWindowMetrics.bounds; val w = b.width(); val h = b.height()
        view = OverlayView(this)
        val lp = WindowManager.LayoutParams(-1, -1, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT)
        lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        wm.addView(view, lp)
        mp = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).getMediaProjection(code, data)
        mp!!.registerCallback(object : MediaProjection.Callback() { override fun onStop() { stopSelf() } }, ui)
        reader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        vd = mp!!.createVirtualDisplay("bh", w, h, resources.displayMetrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, reader.surface, null, null)
        ex.scheduleWithFixedDelay({ tick(w) }, 800, 700, TimeUnit.MILLISECONDS)
        return START_NOT_STICKY
    }

    private fun tick(w: Int) {
        try {
            val img = reader.acquireLatestImage() ?: return
            val bm = try { toBmp(img) } finally { img.close() }
            val s = w / 1080f
            val (board, pcs) = analyze(bm, s)
            bm.recycle()
            val sig = board.joinToString("") + pcs.joinToString("|") { p -> p?.joinToString(",") { "${it[0]}${it[1]}" } ?: "-" }
            if (sig == last) return
            last = sig
            val info = "ช่องเต็ม ${board.sum()}/64 · เจอชิ้น ${pcs.count { it != null }}/3"
            if (pcs.all { it == null }) { ui.post { view.plan = null; view.msg = null; view.info = info; view.invalidate() }; return }
            val t0 = SystemClock.elapsedRealtime()
            val plan = Solver.solve(board, pcs)
            val ms = SystemClock.elapsedRealtime() - t0
            ui.post { view.s = s; view.cells = pcs; view.plan = plan; view.info = "$info · คิด ${ms}ms"; view.msg = if (plan == null) "ไม่มีทางรอด!" else null; view.invalidate() }
        } catch (e: Throwable) {
            ui.post { view.info = "ผิดพลาด: ${e.javaClass.simpleName} ${e.message ?: ""}".take(80); view.invalidate() }
        }
    }

    private fun toBmp(img: Image): Bitmap {
        val pl = img.planes[0]
        val bm = Bitmap.createBitmap(pl.rowStride / pl.pixelStride, img.height, Bitmap.Config.ARGB_8888)
        bm.copyPixelsFromBuffer(pl.buffer)
        return bm
    }

    /** อ่านกระดาน 8x8 และ 3 ชิ้น (พิกัดอิงสกรีนช็อต 1080 กว้าง) */
    private fun px(bm: Bitmap, x: Int, y: Int): Int = bm.getPixel(x.coerceIn(0, bm.width - 1), y.coerceIn(0, bm.height - 1))

    private fun analyze(bm: Bitmap, s: Float): Pair<IntArray, List<List<IntArray>?>> {
        val cs = 119.4f * s; val x0 = 62f * s; val y0 = 582f * s
        val board = IntArray(64) { i ->
            val p = px(bm, (x0 + (i % 8 + .5f) * cs).toInt(), (y0 + (i / 8 + .5f) * cs).toInt())
            if ((Color.red(p) + Color.green(p) + Color.blue(p)) / 3 > 100) 1 else 0
        }
        val bg = px(bm, (540 * s).toInt(), (2100 * s).toInt())
        val W = (360 * s).toInt(); val ty = (1600 * s).toInt(); val H = minOf((450 * s).toInt(), bm.height - ty - 1); val tile = 53.5f * s
        fun df(x: Int, y: Int): Boolean {
            val p = px(bm, x, y)
            return abs(Color.red(p) - Color.red(bg)) + abs(Color.green(p) - Color.green(bg)) + abs(Color.blue(p) - Color.blue(bg)) > 90
        }
        val pcs = (0..2).map { k ->
            var a = Int.MAX_VALUE; var b = -1; var c = Int.MAX_VALUE; var e = -1
            for (y in 0 until H step 2) for (x in 0 until W step 2) if (df(k * W + x, ty + y)) { a = minOf(a, x); b = maxOf(b, x); c = minOf(c, y); e = maxOf(e, y) }
            if (b < 0) return@map null
            val nc = Math.round((b - a + 1) / tile).coerceIn(1, 5); val nr = Math.round((e - c + 1) / tile).coerceIn(1, 5)
            val out = ArrayList<IntArray>()
            for (r in 0 until nr) for (q in 0 until nc) {
                val x = (a + (q + .5f) * tile).toInt(); val y = (c + (r + .5f) * tile).toInt()
                if (x < W && y < H && df(k * W + x, ty + y)) out.add(intArrayOf(r, q))
            }
            if (out.isEmpty()) null else out
        }
        return Pair(board, pcs)
    }

    override fun onDestroy() {
        ex.shutdownNow(); vd?.release(); mp?.stop()
        if (::view.isInitialized) try { wm.removeView(view) } catch (e: Exception) { }
        super.onDestroy()
    }
}
