package com.example.tiktokcrossfollow

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    companion object {
        private const val REQUEST_CAPTURE = 501
        private const val PREFS = "crossfollow_full"
        private val KEYWORDS = listOf(
            "follow chéo", "fl chéo", "followcheo", "flcheo", "f4f",
            "follow for follow", "tym và fl", "tim va fl", "trao đổi follow",
            "chéo follow", "cheo follow", "fl qua fl", "follow qua follow"
        )
        private val COMMENT_TEMPLATES = listOf(
            "Đã tym ❤️ và fl nha 🤝",
            "Tym ❤️ + fl chéo nha ✨",
            "Đã ghé ủng hộ bạn ❤️",
            "Ủng hộ nhau nha 🤝❤️",
            "Tym rồi nè ❤️✨"
        )
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences(PREFS, MODE_PRIVATE) }
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private var projection: MediaProjection? = null
    private var imageReader: ImageReader? = null
    private var virtualDisplay: android.hardware.display.VirtualDisplay? = null
    private var scanning = false
    private var processingFrame = false
    private var scanCount = 0
    private var matchCount = 0
    private var lastText = ""
    private var lastMatch = false
    private lateinit var statusView: TextView
    private lateinit var statsView: TextView
    private lateinit var ocrView: TextView
    private lateinit var commentInput: EditText
    private lateinit var keywordInput: EditText
    private lateinit var historyView: TextView
    private lateinit var startStopButton: Button
    private lateinit var commentSpinner: Spinner

    private val frameTask = object : Runnable {
        override fun run() {
            if (!scanning) return
            captureFrame()
            mainHandler.postDelayed(this, 1800)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        refreshHistory()
        updateStats()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(28))
        }
        scroll.addView(page)
        setContentView(scroll)

        fun label(text: String, size: Float = 15f) {
            page.addView(TextView(this).apply {
                this.text = text
                textSize = size
                setPadding(0, dp(8), 0, dp(6))
            })
        }
        fun button(text: String, action: () -> Unit) {
            page.addView(Button(this).apply {
                this.text = text
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(3) })
        }

        label("✨ CrossFollow OCR", 25f)
        label("Quét chữ trực tiếp trên màn hình • nhận diện từ khóa • thống kê cục bộ")
        statusView = TextView(this).apply {
            text = "Trạng thái: Đã dừng"
            textSize = 16f
            setPadding(0, dp(8), 0, dp(4))
        }
        page.addView(statusView)
        statsView = TextView(this)
        page.addView(statsView)
        startStopButton = Button(this).apply {
            text = "▶ BẮT ĐẦU QUÉT MÀN HÌNH"
            setOnClickListener { if (scanning) stopScan() else requestScreenCapture() }
        }
        page.addView(startStopButton, LinearLayout.LayoutParams(-1, -2))
        button("🎵 Mở TikTok") { openTikTok() }

        label("🔎 Từ khóa nhận diện (phân cách bằng dấu phẩy)")
        keywordInput = EditText(this).apply {
            setText(KEYWORDS.joinToString(", "))
            minLines = 2
            gravity = Gravity.TOP
        }
        page.addView(keywordInput, LinearLayout.LayoutParams(-1, -2))
        button("💾 Lưu từ khóa") {
            prefs.edit().putString("keywords", keywordInput.text.toString()).apply()
            toast("Đã lưu từ khóa.")
        }

        label("📺 Kết quả OCR", 18f)
        ocrView = TextView(this).apply {
            text = "Bấm BẮT ĐẦU, chấp thuận chia sẻ màn hình, sau đó chuyển sang TikTok. Văn bản nhận diện sẽ hiện ở đây."
            textSize = 14f
            setTextIsSelectable(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        page.addView(ocrView)

        label("💬 Bình luận ngắn")
        commentSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, COMMENT_TEMPLATES)
            onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                    if (::commentInput.isInitialized) {
                        commentInput.setText(COMMENT_TEMPLATES[position])
                    }
                }
                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
            }
        }
        page.addView(commentSpinner, LinearLayout.LayoutParams(-1, -2))
        commentInput = EditText(this).apply {
            setText(COMMENT_TEMPLATES[0])
            minLines = 2
            maxLines = 3
            gravity = Gravity.TOP
        }
        page.addView(commentInput, LinearLayout.LayoutParams(-1, -2))
        button("📋 Sao chép bình luận") {
            val value = commentInput.text.toString().trim()
            if (value.isBlank()) toast("Hãy nhập bình luận.")
            else if (value.length > 80) toast("Bình luận dài hơn 80 ký tự; hãy rút gọn.")
            else {
                (getSystemService(CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("TikTok comment", value))
                logAction("Sao chép bản nháp bình luận")
                toast("Đã sao chép. Hãy tự kiểm tra và đăng trong TikTok.")
            }
        }

        label("📊 Lịch sử & thống kê", 18f)
        historyView = TextView(this).apply {
            textSize = 13f
            setTextIsSelectable(true)
        }
        page.addView(historyView)
        button("📤 Chia sẻ / xuất lịch sử") { exportHistory() }
        button("🗑 Xóa lịch sử") {
            prefs.edit().remove("history").apply()
            refreshHistory()
            updateStats()
        }
        label("Lưu ý: OCR chỉ đọc màn hình khi bạn cấp quyền chia sẻ màn hình Android. Ứng dụng không tự Like/Follow, đăng bình luận hoặc vuốt video; các hành động đó cần thực hiện thủ công.")
    }

    private fun requestScreenCapture() {
        val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_CAPTURE)
    }

    @Deprecated("Deprecated in Android")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                try {
                    val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    projection = manager.getMediaProjection(resultCode, data)
                    beginScan()
                } catch (e: Exception) {
                    toast("Không thể bắt đầu chia sẻ màn hình: ${e.localizedMessage}")
                }
            } else toast("Bạn chưa cấp quyền chia sẻ màn hình.")
        }
    }

    private fun beginScan() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "CrossFollowOCR", width, height, density,
            android.hardware.display.DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, mainHandler
        )
        scanning = true
        processingFrame = false
        startStopButton.text = "⏸ DỪNG QUÉT"
        statusView.text = "Trạng thái: Đang quét màn hình (OCR mỗi ~1,8 giây)"
        logAction("Bắt đầu quét màn hình")
        mainHandler.post(frameTask)
    }

    private fun captureFrame() {
        if (!scanning || processingFrame) return
        val image = try { imageReader?.acquireLatestImage() } catch (_: Exception) { null } ?: return
        processingFrame = true
        try {
            val bitmap = imageToBitmap(image)
            image.close()
            recognizer.process(InputImage.fromBitmap(bitmap, 0))
                .addOnSuccessListener { result ->
                    val text = result.text
                    scanCount++
                    lastText = text
                    val keywords = (prefs.getString("keywords", null) ?: keywordInput.text.toString())
                        .split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    val normalized = normalize(text)
                    val matched = keywords.any { normalize(it) in normalized }
                    ocrView.text = if (text.isBlank()) "Không nhận diện được chữ trong khung hình."
                        else "Kết quả OCR:\\n$text\\n\\n" + if (matched) "🎯 Có từ khóa follow chéo phù hợp." else "Chưa thấy từ khóa đã cấu hình."
                    if (matched && !lastMatch) {
                        matchCount++
                        logAction("Phát hiện từ khóa follow chéo")
                        statusView.text = "🎯 Đã phát hiện nội dung có từ khóa follow chéo"
                    } else if (!matched) {
                        statusView.text = "Trạng thái: Đang quét màn hình"
                    }
                    lastMatch = matched
                    updateStats()
                }
                .addOnFailureListener { statusView.text = "Lỗi OCR: ${it.localizedMessage}" }
                .addOnCompleteListener {
                    bitmap.recycle()
                    processingFrame = false
                }
        } catch (e: Exception) {
            try { image.close() } catch (_: Exception) {}
            processingFrame = false
            statusView.text = "Lỗi đọc khung hình: ${e.localizedMessage}"
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val paddedWidth = image.width + rowPadding / pixelStride
        val padded = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
        buffer.rewind()
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun stopScan() {
        scanning = false
        mainHandler.removeCallbacks(frameTask)
        try { virtualDisplay?.release() } catch (_: Exception) {}
        try { imageReader?.close() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        virtualDisplay = null
        imageReader = null
        projection = null
        processingFrame = false
        startStopButton.text = "▶ BẮT ĐẦU QUÉT MÀN HÌNH"
        statusView.text = "Trạng thái: Đã dừng"
        logAction("Dừng quét màn hình")
    }

    private fun openTikTok() {
        val packages = listOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill")
        val launch = packages.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
        if (launch != null) startActivity(launch)
        else startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.tiktok.com/")))
        logAction("Mở TikTok")
    }

    private fun exportHistory() {
        val history = JSONArray(prefs.getString("history", "[]"))
        val lines = mutableListOf<String>()
        for (i in 0 until history.length()) {
            val item = history.optJSONObject(i) ?: continue
            lines.add("${item.optString("time")} | ${item.optString("action")}")
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CrossFollow OCR - Lịch sử")
            putExtra(Intent.EXTRA_TEXT, lines.joinToString("\\n").ifBlank { "Chưa có lịch sử." })
        }
        startActivity(Intent.createChooser(send, "Xuất lịch sử"))
    }

    private fun logAction(action: String) {
        val arr = JSONArray(prefs.getString("history", "[]"))
        arr.put(JSONObject().put("time", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date()))
            .put("action", action))
        while (arr.length() > 300) arr.remove(0)
        prefs.edit().putString("history", arr.toString()).apply()
        refreshHistory()
    }

    private fun refreshHistory() {
        if (!::historyView.isInitialized) return
        val arr = JSONArray(prefs.getString("history", "[]"))
        val lines = mutableListOf<String>()
        for (i in arr.length() - 1 downTo 0) {
            val item = arr.optJSONObject(i) ?: continue
            lines.add("${item.optString("time")} • ${item.optString("action")}")
        }
        historyView.text = if (lines.isEmpty()) "Chưa có hoạt động." else lines.take(35).joinToString("\\n")
    }

    private fun updateStats() {
        if (!::statsView.isInitialized) return
        statsView.text = "Phiên hiện tại: 🔍 $scanCount khung hình | 🎯 $matchCount lần phát hiện"
    }

    private fun normalize(value: String): String =
        java.text.Normalizer.normalize(value.lowercase(Locale.ROOT), java.text.Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9]+".toRegex(), " ")
            .trim()

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

    override fun onDestroy() {
        if (scanning) stopScan()
        recognizer.close()
        super.onDestroy()
    }
}
