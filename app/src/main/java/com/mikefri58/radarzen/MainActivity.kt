package com.mikefri58.radarzen

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.WindowManager
import android.webkit.GeolocationPermissions
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.JavascriptInterface
import android.speech.tts.TextToSpeech
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var webView: WebView
    private val LOCATION_PERMISSION_REQUEST_CODE = 1000
    private var sensorManager: SensorManager? = null
    private var lightSensor: Sensor? = null
    private var lastTheme: String = "unknown"
    private var lastLux: Float = -1f
    private var tts: TextToSpeech? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        webView = findViewById(R.id.webview)
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.setGeolocationEnabled(true)
        webView.settings.allowFileAccess = true
        webView.settings.mediaPlaybackRequiresUserGesture = false

        tts = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = java.util.Locale.FRENCH
            }
        }
        webView.addJavascriptInterface(object {
            @JavascriptInterface
            fun speak(text: String) {
                runOnUiThread {
                    tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
                }
            }
        }, "AndroidTTS")

        webView.webChromeClient = object : WebChromeClient() {
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) {
                callback?.invoke(origin, true, false)
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val csvData = loadCsvFromAssets()
                val escapedCsv = csvData.replace("\\", "\\\\").replace("`", "\\`").replace("\$", "\\$")
                view?.evaluateJavascript("window.csvData = `$escapedCsv`; window.dispatchEvent(new Event('csvLoaded'));", null)
                // Re-injecte la derniere valeur lux connue si dispo
                if (lastLux >= 0f) {
                    view?.evaluateJavascript("window.__onLightChange && window.__onLightChange($lastLux);", null)
                }
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
        }

        webView.loadUrl("file:///android_asset/index.html")

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    }

    override fun onResume() {
        super.onResume()
        lightSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type != Sensor.TYPE_LIGHT) return
        val lux = event.values[0]
        // Evite les envois redondants pour economiser le JS
        if (Math.abs(lux - lastLux) < 2f) return
        lastLux = lux
        webView.evaluateJavascript("window.__onLightChange && window.__onLightChange($lux);", null)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onDestroy() {
        tts?.shutdown()
        super.onDestroy()
    }

    private fun loadCsvFromAssets(): String {
        return try {
            assets.open("radars.csv").bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            ""
        }
    }
}
