package com.lanboardgame.host

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lanboardgame.host.server.GameServerService
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private val startupExecutor = Executors.newSingleThreadExecutor()
    private val startupToken = AtomicInteger()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 40)
        }
        startForegroundService(Intent(this, GameServerService::class.java))
        Log.i(TAG, "foreground service requested")
        webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            addJavascriptInterface(StartupBridge(), "HostStartup")
            addJavascriptInterface(UiBridge(), "HostUi")
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame && request.url.host in setOf("127.0.0.1", "localhost")) {
                        showStartup("本地服务正在恢复，请稍候…")
                        waitForServer()
                    }
                }
            }
            webChromeClient = WebChromeClient()
        }
        setContentView(webView)
        showStartup("正在启动本地服务器…")
        waitForServer()
    }

    override fun onDestroy() {
        startupToken.incrementAndGet()
        startupExecutor.shutdownNow()
        webView.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    private fun waitForServer() {
        val token = startupToken.incrementAndGet()
        startupExecutor.execute {
            repeat(80) {
                if (token != startupToken.get() || isFinishing) return@execute
                val port = GameServerService.configuredPort(this)
                if (isListening(port)) {
                    Log.i(TAG, "local server ready port=$port attempt=${it + 1}")
                    runOnUiThread {
                        if (token == startupToken.get() && !isFinishing) {
                            webView.loadUrl("http://127.0.0.1:$port/")
                        }
                    }
                    return@execute
                }
                runCatching { Thread.sleep(250) }
            }
            runOnUiThread {
                if (token == startupToken.get() && !isFinishing) {
                    Log.e(TAG, "local server startup timed out port=${GameServerService.configuredPort(this)}")
                    showStartup("本地服务器启动超时，请点击重试。", showRetry = true)
                }
            }
        }
    }

    private fun isListening(port: Int): Boolean = runCatching {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", port), 250) }
        true
    }.getOrDefault(false)

    private fun showStartup(message: String, showRetry: Boolean = false) {
        val retry = if (showRetry) "<button onclick=\"HostStartup.retry()\">重新启动</button>" else "<div class=\"spinner\"></div>"
        val html = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#071117;color:#f4efe4;font-family:system-ui}.box{text-align:center;padding:28px}.spinner{width:32px;height:32px;margin:20px auto;border:3px solid #284047;border-top-color:#d7b56d;border-radius:50%;animation:r 1s linear infinite}button{margin-top:20px;padding:12px 24px;border:1px solid #c3a45f;border-radius:10px;background:#245f43;color:white;font-size:16px}@keyframes r{to{transform:rotate(360deg)}}</style></head>
            <body><div class="box"><h2>LAN BOARD GAME</h2><p>$message</p>$retry</div></body></html>
        """.trimIndent()
        webView.loadDataWithBaseURL(null, html, "text/html", "utf-8", null)
    }

    private inner class StartupBridge {
        @JavascriptInterface
        fun retry() {
            runOnUiThread {
                startForegroundService(Intent(this@MainActivity, GameServerService::class.java))
                showStartup("正在重新启动本地服务器…")
                waitForServer()
            }
        }
    }

    private inner class UiBridge {
        @JavascriptInterface
        fun setOrientation(mode: String) {
            runOnUiThread {
                requestedOrientation = when (mode) {
                    "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                    else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                }
            }
        }
    }

    companion object {
        private const val TAG = "LanGame.Activity"
    }
}
