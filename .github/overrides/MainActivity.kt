package cl.minimal.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.*
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val locale = Locale("es", "CL")
    private val bg = Color.rgb(13, 17, 24)
    private val surface = Color.rgb(31, 38, 50)
    private val muted = Color.rgb(167, 180, 195)
    private val accent = Color.rgb(119, 188, 249)
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private var startY = 0f
    private fun dp(n: Int) = (resources.displayMetrics.density * n).toInt()
    private fun shape(color: Int, radius: Int = 20) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        home()
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) refreshWeather()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 100)
        handler.post(object : Runnable {
            override fun run() { updateTime(); handler.postDelayed(this, 30000) }
        })
    }
    private fun label(value: String, size: Float, color: Int = Color.WHITE): TextView = TextView(this).apply {
        text = value; textSize = size; setTextColor(color); includeFontPadding = true
    }
    private fun home() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(42), dp(24), dp(28)); setBackgroundColor(bg)
        }
        setContentView(root)
        greeting = label("", 25f); root.addView(greeting)
        clock = label("", 64f).apply { setPadding(0, dp(8), 0, 0) }; root.addView(clock)
        date = label("", 15f, muted); root.addView(date)
        weather = label("Clima: esperando ubicación", 15f, accent).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = shape(surface)
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) refreshWeather()
                else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            }
        }
        root.addView(weather, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        apps().take(6).chunked(3).forEach { entries ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            entries.forEach { info ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; background = shape(surface)
                    setOnClickListener { launch(info.second) }
                }
                val symbol = info.first.take(1).uppercase(locale)
                item.addView(label(symbol, 26f, accent).apply { gravity = Gravity.CENTER })
                item.addView(label(info.first, 11f).apply {
                    gravity = Gravity.CENTER; maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(dp(4), dp(6), dp(4), 0)
                })
                row.addView(item, LinearLayout.LayoutParams(0, dp(96), 1f).apply {
                    marginStart = dp(4); marginEnd = dp(4); bottomMargin = dp(8)
                })
            }
            root.addView(row)
        }
        val search = label("⌕  Buscar aplicaciones     ↑", 16f).apply {
            gravity = Gravity.CENTER; background = shape(surface, 18)
            setOnClickListener { drawer() }
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(14) })
        root.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startY = event.y; true }
                MotionEvent.ACTION_UP -> { if (startY - event.y > dp(80)) drawer(); true }
                else -> true
            }
        }
        updateTime()
    }
    private fun updateTime() {
        if (!::clock.isInitialized) return
        val now = LocalDateTime.now()
        greeting.text = when (now.hour) {
            in 5..11 -> "Buen día, Cristian"
            in 12..19 -> "Buenas tardes, Cristian"
            else -> "Buenas noches, Cristian"
        }
        clock.text = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        date.text = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)).replaceFirstChar { it.uppercase(locale) }
    }
    private fun apps(): List<Pair<String, String>> {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION") val results = packageManager.queryIntentActivities(intent, 0)
        return results.map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.filter { it.second != packageName }.sortedBy { it.first.lowercase(locale) }
    }
    private fun launch(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) startActivity(intent) else Toast.makeText(this, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show()
    }
    private fun drawer() {
        val panel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(30), dp(24), dp(20)); setBackgroundColor(bg)
        }
        val input = EditText(this).apply {
            hint = "Buscar aplicación"; setSingleLine(true); setTextColor(Color.WHITE); setHintTextColor(muted)
        }
        panel.addView(input)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list); panel.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val all = apps()
        fun render(q: String) {
            list.removeAllViews()
            all.filter { it.first.contains(q, true) }.forEach { app ->
                list.addView(label(app.first, 17f).apply {
                    setPadding(dp(12), dp(18), dp(12), dp(18))
                    setOnClickListener { launch(app.second) }
                })
            }
        }
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        render("")
        panel.addView(label("←  Volver al inicio", 16f, accent).apply {
            setPadding(0, dp(18), 0, dp(12)); setOnClickListener { home() }
        })
        setContentView(panel)
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { home() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshWeather()
            else if (::weather.isInitialized) weather.text = "Activa ubicación para ver el clima"
        }
    }
    private fun refreshWeather() {
        try {
            val manager = getSystemService(LOCATION_SERVICE) as LocationManager
            @Suppress("MissingPermission") val location = manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            if (location == null) { weather.text = "Clima: esperando ubicación · toca para actualizar"; return }
            thread {
                try {
                    val url = "https://api.open-meteo.com/v1/forecast?latitude=${location.latitude}&longitude=${location.longitude}&current=temperature_2m,weather_code&timezone=auto"
                    val connection = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                    val json = JSONObject(connection.getInputStream().bufferedReader().use { it.readText() }).getJSONObject("current")
                    val degrees = json.getDouble("temperature_2m").toInt()
                    val code = json.getInt("weather_code")
                    val condition = when(code) { 0 -> "Despejado"; 1,2,3 -> "Nubosidad"; 45,48 -> "Niebla"; in 51..67 -> "Lluvia"; in 71..77 -> "Nieve"; in 80..82 -> "Chubascos"; in 95..99 -> "Tormenta"; else -> "Variable" }
                    runOnUiThread { if (::weather.isInitialized) weather.text = "$degrees°C · $condition" }
                } catch (_: Exception) { runOnUiThread { if (::weather.isInitialized) weather.text = "Clima no disponible · toca para actualizar" } }
            }
        } catch (_: SecurityException) { weather.text = "Permite ubicación para mostrar el clima" }
    }
}
