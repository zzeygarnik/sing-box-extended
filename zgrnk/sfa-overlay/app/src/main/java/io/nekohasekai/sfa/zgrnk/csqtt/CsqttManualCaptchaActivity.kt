// SPDX-FileCopyrightText: 2026 amurcanov (original manual-captcha design), 2026 zgrnk fork
// SPDX-License-Identifier: PolyForm-Noncommercial-1.0.0
//
// Ported from amurcanov/csqtt v2.1.9's ManlCaptchaActivity.kt: the visible captcha WebView
// activity, opened by CsqttManualCaptchaWebViewManager when the auto solver can't proceed.

package io.nekohasekai.sfa.zgrnk.csqtt

import android.annotation.SuppressLint
import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

class CsqttManualCaptchaActivity : ComponentActivity() {
    private var captchaWebView: WebView? = null

    private val interceptorJSCode = """
        (function() {
            if (window.__csqtt_interceptor_installed) return;
            window.__csqtt_interceptor_installed = true;
            const origFetch = window.fetch;
            window.fetch = async function() {
                const args = arguments;
                const url = args[0] || '';
                if (typeof url === 'string' && url.includes('captchaNotRobot.check')) {
                    const response = await origFetch.apply(this, args);
                    const clone = response.clone();
                    try {
                        const data = await clone.json();
                        if (data.response && data.response.success_token) {
                            window.CsqttCaptcha.onSuccess(data.response.success_token);
                        } else if (data.error) {
                            window.CsqttCaptcha.onError(JSON.stringify(data.error));
                        }
                    } catch(e) {}
                    return response;
                }
                return origFetch.apply(this, args);
            };
            const origXHROpen = XMLHttpRequest.prototype.open;
            const origXHRSend = XMLHttpRequest.prototype.send;
            XMLHttpRequest.prototype.open = function(method, url) {
                this._csqtt_url = url;
                return origXHROpen.apply(this, arguments);
            };
            XMLHttpRequest.prototype.send = function() {
                const xhr = this;
                if (xhr._csqtt_url && xhr._csqtt_url.includes('captchaNotRobot.check')) {
                    xhr.addEventListener('load', function() {
                        try {
                            const data = JSON.parse(xhr.responseText);
                            if (data.response && data.response.success_token) {
                                window.CsqttCaptcha.onSuccess(data.response.success_token);
                            } else if (data.error) {
                                window.CsqttCaptcha.onError(JSON.stringify(data.error));
                            }
                        } catch(e) {}
                    });
                }
                return origXHRSend.apply(this, arguments);
            };
        })();
    """.trimIndent()

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CsqttManualCaptchaWebViewManager.activeActivity = this
        val redirectUri = intent.getStringExtra("redirectUri") ?: return finish()
        if (!CsqttCaptchaUriPolicy.isAllowed(redirectUri)) {
            CsqttManualCaptchaWebViewManager.notifyResult(Result.failure(IllegalArgumentException("Недопустимый адрес captcha")))
            return finish()
        }

        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Column {
                        var isLoading by rememberSaveable { mutableStateOf(true) }
                        Box {
                            Surface(
                                modifier = Modifier.fillMaxSize(),
                                color = Color.Transparent,
                                tonalElevation = 0.dp,
                                shadowElevation = 0.dp,
                            ) {
                                AndroidView(
                                    modifier = Modifier.fillMaxSize(),
                                    factory = { ctx ->
                                        WebView(ctx).apply {
                                            captchaWebView = this
                                            layoutParams = ViewGroup.LayoutParams(
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                                ViewGroup.LayoutParams.MATCH_PARENT,
                                            )
                                            settings.apply {
                                                javaScriptEnabled = true
                                                domStorageEnabled = true
                                                mediaPlaybackRequiresUserGesture = false
                                                loadWithOverviewMode = true
                                                useWideViewPort = true
                                                blockNetworkLoads = false
                                                cacheMode = WebSettings.LOAD_DEFAULT
                                            }
                                            addJavascriptInterface(object {
                                                @JavascriptInterface
                                                fun onSuccess(token: String) {
                                                    CsqttManualCaptchaWebViewManager.notifyResult(Result.success(token))
                                                    finish()
                                                }

                                                @JavascriptInterface
                                                fun onError(err: String) {
                                                    Log.e("CsqttManualCaptchaWV", "error: $err")
                                                    CsqttManualCaptchaWebViewManager.notifyResult(
                                                        Result.failure(Exception("VK captcha error: $err")),
                                                    )
                                                    finish()
                                                }
                                            }, "CsqttCaptcha")

                                            webViewClient = object : WebViewClient() {
                                                override fun shouldOverrideUrlLoading(
                                                    view: WebView,
                                                    request: WebResourceRequest,
                                                ): Boolean = !CsqttCaptchaUriPolicy.isAllowed(request.url.toString())

                                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                                    super.onPageStarted(view, url, favicon)
                                                    view?.evaluateJavascript(interceptorJSCode, null)
                                                }

                                                override fun onPageFinished(view: WebView?, url: String?) {
                                                    super.onPageFinished(view, url)
                                                    view?.evaluateJavascript(interceptorJSCode, null)
                                                    isLoading = false
                                                }
                                            }
                                            webChromeClient = WebChromeClient()
                                            loadUrl(redirectUri)
                                        }
                                    },
                                )
                            }
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.align(Alignment.Center).size(48.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        captchaWebView?.let { webView ->
            runCatching {
                webView.stopLoading()
                webView.removeJavascriptInterface("CsqttCaptcha")
                webView.webViewClient = WebViewClient()
                webView.webChromeClient = null
                webView.loadUrl("about:blank")
                webView.removeAllViews()
                webView.destroy()
            }
        }
        captchaWebView = null
        super.onDestroy()
        if (CsqttManualCaptchaWebViewManager.activeActivity === this) {
            CsqttManualCaptchaWebViewManager.activeActivity = null
        }
    }
}
