package cl.minimal.launcher

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.widget.*
import android.text.TextWatcher
import android.text.Editable
import org.json.JSONObject
import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private val locale = Locale("es", "CL")
    private val bg = Color.rgb(12, 16, 23)
    private val panel = Color.rgb(27, 34, 45)
    private val muted = Color.rgb(151, 166, 185)
    private val accent = Color.rgb(153, 199, 255)
    private lateinit var greeting: TextView
    private lateinit var clock: TextView
    private lateinit var date: TextView
    private lateinit var weather: TextView
    private var startY = 0f
    private val prefs by lazy { getSharedPreferences("launcher_settings", MODE_PRIVATE) }
    private fun dp(n: Int) = (n * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 22) = GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat() }
    private fun txt(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(color)
        if (bold) setTypeface(null, Typeface.BOLD)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg; window.navigationBarColor = bg
        home()
        if (prefs.contains("weather_lat") || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) refreshWeather()
        else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 100)
        handler.post(object : Runnable {
            override fun run() { updateTime(); handler.postDelayed(this, 30_000) }
        })
    }
    private fun apps(): List<Pair<String, String>> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION") val results = packageManager.queryIntentActivities(query, 0)
        return results.map { it.loadLabel(packageManager).toString() to it.activityInfo.packageName }
            .distinctBy { it.second }.filter { it.second != packageName }.sortedBy { it.first.lowercase(locale) }
    }
    private fun selected(index: Int, list: List<Pair<String, String>>): Pair<String, String>? {
        val pkg = prefs.getString("shortcut_$index", null)
        return if (pkg == null) list.getOrNull(index) else list.firstOrNull { it.second == pkg }
    }
    private fun home() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
            setPadding(dp(24), dp(42), dp(24), dp(24))
        }
        setContentView(root)
        greeting = txt("", 23f, Color.WHITE, true)
        root.addView(greeting)
        clock = txt("", 67f, Color.WHITE, true).apply { setPadding(0, dp(8), 0, 0) }
        root.addView(clock)
        date = txt("", 15f, muted)
        root.addView(date)
        weather = txt("Clima: consultando ubicación", 15f, accent).apply {
            background = rounded(panel, 18)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            setOnClickListener {
                if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) refreshWeather()
                else requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), 100)
            }
        }
        root.addView(weather, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(24) })
        root.addView(Space(this), LinearLayout.LayoutParams(1, 0, 1f))
        root.addView(txt("TUS ACCESOS", 11f, muted, true).apply {
            letterSpacing = 0.16f
            setPadding(dp(2), 0, 0, dp(12))
        })
        val available = apps()
        for (rowIndex in 0..1) {
            val row = LinearLayout(this)
            for (column in 0..1) {
                val index = rowIndex * 2 + column
                val item = selected(index, available)
                val tile = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                    background = rounded(panel)
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                }
                val symbol = txt(item?.first?.take(1)?.uppercase(locale) ?: "+", 23f, accent, true).apply { gravity = Gravity.CENTER }
                val name = txt(item?.first ?: "Elegir aplicación", 13f, Color.WHITE, true).apply {
                    gravity = Gravity.CENTER
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    setPadding(0, dp(7), 0, 0)
                }
                tile.addView(symbol)
                tile.addView(name)
                tile.setOnClickListener { if (item == null) choose(index) else launch(item.second) }
                tile.setOnLongClickListener { choose(index); true }
                row.addView(tile, LinearLayout.LayoutParams(0, dp(90), 1f).apply {
                    marginStart = if (column == 0) 0 else dp(7)
                    marginEnd = if (column == 0) dp(7) else 0
                })
            }
            root.addView(row, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        }
        val help = txt("Mantén pulsado un acceso para cambiarlo", 12f, muted).apply { gravity = Gravity.CENTER }
        root.addView(help, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(16) })
        val browse = txt("⌕     Buscar aplicaciones                         ↑", 15f).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = rounded(panel, 18)
            setPadding(dp(18), 0, dp(12), 0)
            setOnClickListener { drawer() }
        }
        root.addView(browse, LinearLayout.LayoutParams(-1, dp(56)))
        root.setOnTouchListener { _, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { startY = e.y; true }
                MotionEvent.ACTION_UP -> { if (startY - e.y > dp(85)) drawer(); true }
                else -> true
            }
        }
        updateTime()
    }
    private fun choose(index: Int) {
        val list = apps()
        val names = list.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Aplicación para acceso ${index + 1}")
            .setItems(names) { _, position ->
                prefs.edit().putString("shortcut_$index", list[position].second).apply()
                home()
            }
            .setNeutralButton("Restablecer") { _, _ ->
                prefs.edit().remove("shortcut_$index").apply()
                home()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
    private fun updateTime() {
        if (!::clock.isInitialized) return
        val now = LocalDateTime.now()
        greeting.text = when(now.hour) {
            in 5..11 -> "Buen día, Cristian"
            in 12..19 -> "Buenas tardes, Cristian"
            else -> "Buenas noches, Cristian"
        }
        clock.text = now.format(DateTimeFormatter.ofPattern("HH:mm"))
        date.text = now.format(DateTimeFormatter.ofPattern("EEEE d 'de' MMMM", locale)).replaceFirstChar { it.uppercase(locale) }
    }
    private fun launch(pkg: String) {
        packageManager.getLaunchIntentForPackage(pkg)?.let { startActivity(it) }
            ?: Toast.makeText(this, "No se pudo abrir la aplicación", Toast.LENGTH_SHORT).show()
    }
    private fun drawer() {
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(32), dp(24), dp(20))
            setBackgroundColor(bg)
        }
        setContentView(page)
        val heading = txt("Aplicaciones", 27f, Color.WHITE, true)
        page.addView(heading)
        val input = EditText(this).apply {
            hint = "Buscar por nombre"
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
        }
        page.addView(input)
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(list)
        page.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val all = apps()
        fun render(query: String) {
            list.removeAllViews()
            all.filter { it.first.contains(query, ignoreCase = true) }.forEach { app ->
                val item = txt(app.first, 17f).apply {
                    setPadding(dp(12), dp(18), dp(12), dp(18))
                    setOnClickListener { launch(app.second) }
                }
                list.addView(item)
            }
        }
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { render(s.toString()) }
            override fun afterTextChanged(s: Editable?) {}
        })
        render("")
        page.addView(txt("←  Volver al inicio", 16f, accent).apply {
            setPadding(0, dp(16), 0, dp(14))
            setOnClickListener { home() }
        })
    }
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() { home() }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 100) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) refreshWeather()
            else if (::weather.isInitialized) weather.text = "Activa la ubicación para ver el clima"
        }
    }
    private fun refreshWeather() {
        var latitude: Double? = if (prefs.contains("weather_lat")) java.lang.Double.longBitsToDouble(prefs.getLong("weather_lat", 0L)) else null
        var longitude: Double? = if (prefs.contains("weather_lon")) java.lang.Double.longBitsToDouble(prefs.getLong("weather_lon", 0L)) else null
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            try {
                val manager = getSystemService(LOCATION_SERVICE) as LocationManager
                @Suppress("MissingPermission") val location =
                    manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                        ?: manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                if (location != null) {
                    latitude = location.latitude
                    longitude = location.longitude
                    prefs.edit()
                        .putLong("weather_lat", java.lang.Double.doubleToRawLongBits(location.latitude))
                        .putLong("weather_lon", java.lang.Double.doubleToRawLongBits(location.longitude))
                        .apply()
                }
            } catch (_: SecurityException) { }
        }
        if (latitude == null || longitude == null) {
            weather.text = "⌖  Ubicación no disponible"
            return
        }
        val lat = latitude
        val lon = longitude
        thread {
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&timezone=auto"
                val conn = URL(url).openConnection().apply { connectTimeout = 8000; readTimeout = 8000 }
                val current = JSONObject(conn.getInputStream().bufferedReader().use { it.readText() }).getJSONObject("current")
                val degrees = current.getDouble("temperature_2m").toInt()
                val code = current.getInt("weather_code")
                val (symbol, condition) = when(code) {
                    0 -> "☀️" to "Despejado"
                    1,2 -> "🌤️" to "Parcialmente nublado"
                    3 -> "☁️" to "Nublado"
                    45,48 -> "🌫️" to "Niebla"
                    in 51..67 -> "🌧️" to "Lluvia"
                    in 71..77 -> "❄️" to "Nieve"
                    in 80..82 -> "🌦️" to "Chubascos"
                    in 95..99 -> "⛈️" to "Tormenta"
                    else -> "🌡️" to "Variable"
                }
                runOnUiThread { if (::weather.isInitialized) weather.text = "$symbol  $degrees°C · $condition" }
            } catch (_: Exception) {
                runOnUiThread { if (::weather.isInitialized) weather.text = "🌡️  Clima no disponible" }
            }
        }
    }
}
