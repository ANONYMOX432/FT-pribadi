package com.alfakuis.pribadi

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val bankSoalUrl = "https://raw.githubusercontent.com/ANONYMOX432/FT-pribadi/main/bank_soal.json"
    private val prefsName = "alfa_kuis_prefs"
    private val keyBankJson = "bank_json"

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var tvStatus: TextView
    private val executor = Executors.newSingleThreadExecutor()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        etUrl = findViewById(R.id.etUrl)
        tvStatus = findViewById(R.id.tvStatus)
        webView = findViewById(R.id.webView)
        val btnGo: Button = findViewById(R.id.btnGo)
        val btnSync: Button = findViewById(R.id.btnSync)

        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectHelper()
            }
        }

        btnGo.setOnClickListener {
            var target = etUrl.text.toString().trim()
            if (target.isNotEmpty()) {
                if (!target.startsWith("http://") && !target.startsWith("https://")) {
                    target = "https://$target"
                }
                webView.loadUrl(target)
            }
        }

        btnSync.setOnClickListener { syncBankSoal() }

        updateStatusText()
        syncBankSoal()
    }

    private fun syncBankSoal() {
        runOnUiThread { tvStatus.text = "Bank soal: mengambil update..." }
        executor.execute {
            try {
                val conn = (URL(bankSoalUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                }
                if (conn.responseCode == HttpURLConnection.HTTP_OK) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    JSONObject(body)
                    getSharedPreferences(prefsName, MODE_PRIVATE)
                        .edit().putString(keyBankJson, body).apply()
                    runOnUiThread { updateStatusText() }
                } else {
                    runOnUiThread { tvStatus.text = "Gagal sync (HTTP ${conn.responseCode}), pakai cache" }
                }
                conn.disconnect()
            } catch (_: Exception) {
                runOnUiThread { tvStatus.text = "Offline / gagal sync, pakai cache lokal" }
            }
        }
    }

    private fun updateStatusText() {
        val raw = getSharedPreferences(prefsName, MODE_PRIVATE).getString(keyBankJson, null)
        if (raw == null) {
            tvStatus.text = "Bank soal: kosong"
            return
        }
        try {
            val obj = JSONObject(raw)
            val ver = obj.optInt("version", 1)
            val total = obj.optJSONArray("soal")?.length() ?: 0
            tvStatus.text = "Bank soal v$ver siap ($total soal)"
        } catch (_: Exception) {
            tvStatus.text = "Bank soal: format tidak valid"
        }
    }

    private fun injectHelper() {
        val raw = getSharedPreferences(prefsName, MODE_PRIVATE).getString(keyBankJson, """{"soal":[]}""")
            ?: """{"soal":[]}"""
        val escaped = raw.replace("\\", "\\\\").replace("'", "\\'").replace("\n", "")
        val js = """
            (function() {
                if (document.getElementById('alfa-helper-box')) return;
                const bank = JSON.parse('$escaped');
                const list = bank.soal || [];
                const box = document.createElement('div');
                box.id = 'alfa-helper-box';
                box.style.cssText = 'position:fixed;bottom:12px;right:12px;z-index:999999;background:#111;color:#fff;padding:10px;border-radius:8px;max-width:260px;font-size:12px;box-shadow:0 2px 8px rgba(0,0,0,.4)';
                box.innerHTML = '<b>Bank Soal Aktif</b><div id="alfa-ans" style="margin-top:6px;color:#4ade80;">Klik Cari Jawaban</div><button id="alfa-find" style="margin-top:6px;padding:4px 8px;">Cari Jawaban</button>';
                document.body.appendChild(box);
                document.getElementById('alfa-find').onclick = function() {
                    const pageText = (document.body.innerText || '').toLowerCase();
                    const found = list.find(item => item.q && pageText.includes(String(item.q).toLowerCase()));
                    document.getElementById('alfa-ans').innerText = found ? ('Jawaban: ' + found.a) : 'Belum ada di bank soal';
                };
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    override fun onDestroy() {
        executor.shutdown()
        super.onDestroy()
    }
}
