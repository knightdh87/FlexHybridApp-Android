package app.dvkyun.flexhybridand

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Build
import android.util.AttributeSet
import android.webkit.*
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.BufferedReader
import kotlin.random.Random
import kotlin.reflect.KVisibility
import kotlin.reflect.full.createType
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.full.valueParameters

open class FlexWebView: WebView {

    private val mActivity: Activity? = FlexUtil.getActivity(context)
    private val interfaces: HashMap<String, (arguments: JSONArray?) -> Any?> = HashMap()
    private val actions: HashMap<String, (action: FlexAction?, arguments: JSONArray?) -> Unit> = HashMap()
    private val returnFromWeb: HashMap<Int,(Any?) -> Unit> = HashMap()
    private val internalInterface = arrayOf("flexreturn")
    private val scope = CoroutineScope(Dispatchers.Default)

    private var isAfterFirstLoad = false
    private var flexJsString: String
    internal var baseUrl: String? = null

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    init {
        if(mActivity == null) throw FlexException(FlexException.ERROR1)
        flexJsString = try {
            val reader = BufferedReader(context.assets.open("FlexHybridAnd.min.js").reader())
            val sb = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                sb.append(line).append("\n")
            }
            reader.close()
            sb.toString()
        } catch (e: java.lang.Exception) {
            throw FlexException(e)
        }
        webChromeClient = FlexWebChromeClient(mActivity)
        webViewClient = FlexWebViewClient()
        super.addJavascriptInterface(InternalInterface(), "flexdefine")
        initialize()
    }

    /*
    * Initial setting
    * */
    @Suppress("DEPRECATION")
    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        settings.javaScriptEnabled = true
        settings.displayZoomControls = false
        settings.builtInZoomControls = false
        settings.setSupportZoom(false)
        settings.textZoom = 100
        settings.domStorageEnabled = true
        settings.loadWithOverviewMode = true
        settings.loadsImagesAutomatically = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.SINGLE_COLUMN
        settings.enableSmoothTransition()
        settings.javaScriptCanOpenWindowsAutomatically = true
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        if(Build.VERSION.SDK_INT > Build.VERSION_CODES.O) {
            setRendererPriorityPolicy(RENDERER_PRIORITY_IMPORTANT, true)
        }
    }

    fun setBaseUrl(url: String) {
        if(baseUrl != null) {
            throw FlexException(FlexException.ERROR5)
        }
        if(!url.startsWith("http://") && !url.startsWith("http://") && !url.startsWith("file://")) {
            throw FlexException(FlexException.ERROR13)
        }
        baseUrl = url
    }

    fun getBaseUrl(): String? = baseUrl

    fun setInterface(name: String, lambda: (JSONArray?) -> Any?): FlexWebView {
        if(isAfterFirstLoad) {
            throw FlexException(FlexException.ERROR6)
        }
        if(interfaces[name] != null || actions[name] != null) {
            throw FlexException(FlexException.ERROR7)
        }
        if(name.contains("flex")) {
            throw FlexException(FlexException.ERROR8)
        }
        interfaces[name] = lambda
        return this
    }

    fun setAction(name: String, action: (action: FlexAction?, arguments: JSONArray?) -> Unit): FlexWebView {
        if(isAfterFirstLoad) {
            throw FlexException(FlexException.ERROR6)
        }
        if(interfaces[name] != null || actions[name] != null) {
            throw FlexException(FlexException.ERROR7)
        }
        if(name.contains("flex")) {
            throw FlexException(FlexException.ERROR8)
        }
        actions[name] = action
        return this
    }

    fun addFlexInterface(flexInterfaces: Any) {
        flexInterfaces::class.members.forEach {
            if(it.findAnnotation<FlexFuncInterface>() != null) {
                if(it.visibility != KVisibility.PUBLIC) {
                    throw FlexException(FlexException.ERROR12)
                }
                if(it.valueParameters.size != 1 || it.valueParameters[0].type.classifier != JSONArray::class.createType().classifier) {
                    throw FlexException(FlexException.ERROR10)
                }
                setInterface(it.name) { arguments ->
                    it.call(flexInterfaces, arguments)
                }
            } else if(it.findAnnotation<FlexActionInterface>() != null) {
                if(it.visibility != KVisibility.PUBLIC) {
                    throw FlexException(FlexException.ERROR12)
                }
                if(it.valueParameters.size != 2 || it.valueParameters[0].type.classifier != FlexAction::class.createType().classifier || it.valueParameters[1].type.classifier != JSONArray::class.createType().classifier) {
                    throw FlexException(FlexException.ERROR11)
                }
                setAction(it.name) { action, arguments ->
                    it.call(flexInterfaces, action, arguments)
                }
            }
        }
        if(flexInterfaces is FlexInterfaces) {
            flexInterfaces.interfaces.keys.forEach {
                setInterface(it, flexInterfaces.interfaces[it]!!)
            }
            flexInterfaces.actions.keys.forEach {
                setAction(it, flexInterfaces.actions[it]!!)
            }
        }
    }

    fun evalFlexFunc(funcName: String) {
        FlexUtil.evaluateJavaScript(this,"\$flex.flex.\$flex.web.$funcName()")
    }

    fun evalFlexFunc(funcName: String, response: (Any?) -> Unit) {
        val tID = Random.nextInt(10000)
        returnFromWeb[tID] = response
        FlexUtil.evaluateJavaScript(this,"(async function() { const V = await \$flex.web.$funcName(); \$flex.flexreturn({ TID: $tID, Value: V }); })(); void 0;")
    }

    fun evalFlexFunc(funcName: String, sendData: Any?) {
        if(sendData == null || sendData == Unit) {
            FlexUtil.evaluateJavaScript(this,"\$flex.flex.\$flex.web.$funcName()")
        } else {
            FlexUtil.evaluateJavaScript(this,"\$flex.flex.\$flex.web.$funcName(${FlexUtil.convertValue(sendData)})")
        }
    }

    fun evalFlexFunc(funcName: String, sendData: Any?, response: (Any?) -> Unit) {
        val tID = Random.nextInt(10000)
        returnFromWeb[tID] = response
        if(sendData == null || sendData == Unit) {
            FlexUtil.evaluateJavaScript(this,"(async function() { const V = await \$flex.web.${funcName}(); \$flex.flexreturn({ TID: ${tID}, Value: V }); })(); void 0;")
        } else {
            FlexUtil.evaluateJavaScript(this,"(async function() { const V = await \$flex.web.$funcName(${FlexUtil.convertValue(sendData)}); \$flex.flexreturn({ TID: $tID, Value: V }); })(); void 0;")
        }
    }

    override fun getWebChromeClient(): FlexWebChromeClient {
        return super.getWebChromeClient() as FlexWebChromeClient
    }

    override fun setWebChromeClient(client: WebChromeClient) {
        if(client !is FlexWebChromeClient) throw FlexException(FlexException.ERROR2)
        super.setWebChromeClient(client)
    }

    override fun getWebViewClient(): FlexWebViewClient {
        return super.getWebViewClient() as FlexWebViewClient
    }

    override fun setWebViewClient(client: WebViewClient) {
        if(client !is FlexWebViewClient) throw FlexException(FlexException.ERROR2)
        super.setWebViewClient(client)
    }

    @SuppressLint("JavascriptInterface")
    override fun addJavascriptInterface(`object`: Any, name: String) {
        if(name.contains("flex")) {
            throw FlexException(FlexException.ERROR8)
        }
        super.addJavascriptInterface(`object`, name)
    }

    internal fun flexInitInPage() {
        if(!isAfterFirstLoad) {
            val keys = StringBuilder()
            keys.append("[\"")
            keys.append(internalInterface.joinToString(separator = "\",\""))
            if(interfaces.size > 0) {
                keys.append("\",\"")
                keys.append(interfaces.keys.joinToString(separator = "\",\""))
            }
            if(actions.size > 0) {
                keys.append("\",\"")
                keys.append(actions.keys.joinToString(separator = "\",\""))
            }
            keys.append("\"]")
            flexJsString = flexJsString.replaceFirst("keysfromAnd",keys.toString())
        }
        isAfterFirstLoad = true
        FlexUtil.evaluateJavaScript(this, flexJsString)
    }

    inner class InternalInterface {
        @JavascriptInterface
        fun flexInterface(input: String) {
            scope.launch {
                try {
                    val data = JSONObject(input)
                    val intName : String = data.getString("intName")
                    val fName : String = data.getString("funName")
                    val args : JSONArray? = data.getJSONArray("arguments")
                    if(interfaces[intName] != null) {
                        val value = interfaces[intName]?.invoke(args)
                        if(value == null || value == Unit) {
                            FlexUtil.evaluateJavaScript(this@FlexWebView, "\$flex.flex.${fName}()")
                        } else {
                            FlexUtil.evaluateJavaScript(this@FlexWebView, "\$flex.flex.$fName(${FlexUtil.convertValue(value)})")
                        }
                    } else if(actions[intName] != null) {
                        val lambda = actions[intName]!!
                        var action: FlexAction? = FlexAction(fName, this@FlexWebView)
                        action?.afterReturn = { action = null }
                        lambda.invoke(action, args)
                    }  else {
                        when(internalInterface.indexOf(intName)) {
                            0 -> {
                                val iData = args!!.getJSONObject(0)
                                val tID = iData.getInt("TID")
                                val value = iData.get("Value")
                                returnFromWeb[tID]?.invoke(value)
                            }
                        }
                    }
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }
    }

}
