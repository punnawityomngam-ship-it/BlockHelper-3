package com.beam.blockhelper

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.*

class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        requestPermissions(arrayOf("android.permission.POST_NOTIFICATIONS"), 0)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 120, 48, 48) }
        l.addView(TextView(this).apply {
            text = "Block Helper\n1) อนุญาตแสดงทับแอปอื่น\n2) กดเริ่ม แล้วอนุญาตจับภาพหน้าจอ\n3) เปิดเกม ช่องที่ควรวางจะมีกรอบสี + เลขลำดับ"
            textSize = 16f
        })
        fun btn(t: String, f: () -> Unit) = l.addView(Button(this).apply { text = t; setOnClickListener { f() } })
        btn("1) อนุญาตแสดงทับ") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btn("2) เริ่มตัวช่วย") {
            if (!Settings.canDrawOverlays(this)) Toast.makeText(this, "ต้องอนุญาตแสดงทับก่อน", Toast.LENGTH_SHORT).show()
            else startActivityForResult((getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), 1)
        }
        btn("หยุด") { stopService(Intent(this, OverlayService::class.java)) }
        setContentView(l)
    }

    override fun onActivityResult(rc: Int, res: Int, d: Intent?) {
        if (rc == 1 && res == RESULT_OK && d != null) {
            startForegroundService(Intent(this, OverlayService::class.java).putExtra("code", res).putExtra("data", d))
            moveTaskToBack(true)
        }
    }
}
