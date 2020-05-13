package app.dvkyun.flexhybridand

import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient

class FlexWebViewClient: WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        if(view is FlexWebView && url != null) {
            val baseUrl = view.baseUrl
            if(baseUrl == null || (url.substring(0, baseUrl.length) == baseUrl)) {
                view.flexInitInPage()
            }
        }
    }

    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
        super.doUpdateVisitedHistory(view, url, isReload)
    }

}
