package app.orbit.browser

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    private val pages = mutableListOf<Page>()
    private var active = 0
    private lateinit var address: EditText
    private lateinit var tabsLabel: TextView
    private lateinit var host: FrameLayout
    private lateinit var progress: ProgressBar
    private lateinit var prefs: android.content.SharedPreferences
    private var engine = "DuckDuckGo"
    private var desktop = false
    private val bg = Color.rgb(17,18,24)
    private val panel = Color.rgb(30,31,42)
    private val ink = Color.rgb(245,245,250)
    private val muted = Color.rgb(165,166,183)
    private val accent = Color.rgb(139,124,255)
    private data class Page(val web: WebView, var title: String = "Новая вкладка", var url: String = "")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = getSharedPreferences("orbit", MODE_PRIVATE)
        engine = prefs.getString("engine", "DuckDuckGo") ?: "DuckDuckGo"
        desktop = prefs.getBoolean("desktop", false)
        buildUi()
        newTab(prefs.getString("last_url", "") ?: "")
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun button(label: String, action: () -> Unit) = TextView(this).apply {
        text = label; textSize = 19f; gravity = Gravity.CENTER; setTextColor(ink)
        setPadding(dp(10), dp(8), dp(10), dp(8)); setOnClickListener { action() }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val header = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12),dp(8),dp(12),dp(8)); setBackgroundColor(bg) }
        val brand = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        brand.addView(TextView(this).apply { text = "◈  Orbit Browser"; textSize = 22f; typeface = android.graphics.Typeface.DEFAULT_BOLD; setTextColor(ink) }, LinearLayout.LayoutParams(0,dp(44),1f))
        brand.addView(button("＋") { newTab("") })
        brand.addView(button("▣") { showTabs() })
        header.addView(brand)
        val row = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        address = EditText(this).apply {
            singleLine = true; textSize = 15f; setTextColor(ink); setHintTextColor(muted); hint = "Адрес или поисковый запрос"
            setPadding(dp(12),0,dp(8),0); setBackgroundColor(panel); imeOptions = EditorInfo.IME_ACTION_GO
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_GO || id == EditorInfo.IME_ACTION_DONE) { go(text.toString()); true } else false }
        }
        row.addView(address, LinearLayout.LayoutParams(0,dp(48),1f).apply { marginEnd = dp(6) })
        row.addView(button("Перейти ↗") { go(address.text.toString()) })
        header.addView(row)
        val nav = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL }
        nav.addView(button("‹") { if (current().web.canGoBack()) current().web.goBack() })
        nav.addView(button("›") { if (current().web.canGoForward()) current().web.goForward() })
        nav.addView(button("↻") { current().web.reload() })
        nav.addView(button("⌂") { home() })
        nav.addView(button("☆") { bookmarks() })
        nav.addView(button("⋮") { menu() })
        header.addView(nav)
        tabsLabel = TextView(this).apply { textSize = 12f; setTextColor(muted); setPadding(0,dp(3),0,0) }
        header.addView(tabsLabel)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { progressTintList = android.content.res.ColorStateList.valueOf(accent) }
        header.addView(progress, LinearLayout.LayoutParams(-1,dp(3)))
        host = FrameLayout(this).apply { setBackgroundColor(Color.WHITE) }
        root.addView(header)
        root.addView(host, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun newTab(initial: String) {
        val web = WebView(this)
        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        web.settings.loadsImagesAutomatically = true
        web.settings.javaScriptCanOpenWindowsAutomatically = true
        web.settings.setSupportMultipleWindows(true)
        web.settings.mediaPlaybackRequiresUserGesture = true
        web.settings.userAgentString = WebSettings.getDefaultUserAgent(this)
        applyDesktop(web)
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url
                if (u.scheme == "http" || u.scheme == "https") return false
                return try { startActivity(Intent(Intent.ACTION_VIEW,u)); true } catch (_: Exception) { true }
            }
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                progress.visibility = android.view.View.VISIBLE
                pages.getOrNull(active)?.url = url
                address.setText(url)
            }
            override fun onPageFinished(view: WebView, url: String) {
                progress.visibility = android.view.View.GONE
                pages.getOrNull(active)?.let { it.url = url; it.title = view.title ?: url }
                address.setText(url); updateTabs()
                prefs.edit().putString("last_url",url).apply()
                remember(url, view.title ?: url)
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) Toast.makeText(this@MainActivity,"Не удалось загрузить сайт. Проверь подключение.",Toast.LENGTH_LONG).show()
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, p: Int) { progress.progress = p; progress.visibility = if (p >= 100) android.view.View.GONE else android.view.View.VISIBLE }
            override fun onReceivedTitle(view: WebView, title: String?) { pages.getOrNull(active)?.title = title ?: "Вкладка"; updateTabs() }
            override fun onCreateWindow(view: WebView, dialog: Boolean, gesture: Boolean, msg: android.os.Message): Boolean {
                newTab("")
                (msg.obj as WebView.WebViewTransport).webView = current().web
                msg.sendToTarget()
                return true
            }
        }
        pages.add(Page(web)); active = pages.lastIndex
        host.addView(web,FrameLayout.LayoutParams(-1,-1)); updateTabs()
        if (initial.isBlank()) home() else go(initial)
    }

    private fun applyDesktop(web: WebView) {
        web.settings.useWideViewPort = desktop
        web.settings.loadWithOverviewMode = desktop
        web.settings.userAgentString = if (desktop) "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36" else WebSettings.getDefaultUserAgent(this)
    }

    private fun go(raw: String) {
        val q = raw.trim()
        if (q.isEmpty()) return
        val url = when {
            q.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) -> q
            q.matches(Regex("^[^\\s.]+\\.[^\\s]+(:[0-9]+)?(/.*)?$")) && !q.contains(" ") -> "https://" + q
            else -> {
                val base = when (engine) {
                    "Google" -> "https://www.google.com/search?q="
                    "Bing" -> "https://www.bing.com/search?q="
                    "Brave" -> "https://search.brave.com/search?q="
                    else -> "https://duckduckgo.com/?q="
                }
                base + Uri.encode(q)
            }
        }
        current().url = url
        current().web.loadUrl(url)
        address.setText(url)
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken,0)
        address.clearFocus()
    }

    private fun home() {
        address.setText("")
        current().url = ""
        current().web.loadDataWithBaseURL("https://duckduckgo.com", """<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><style>*{box-sizing:border-box}body{margin:0;background:#111218;color:#f5f5fa;font:16px system-ui;padding:28px 20px}.tag{color:#a99cff;letter-spacing:2px;font-size:12px;font-weight:700}h1{font-size:42px;line-height:1.05;margin:18px 0}span{color:#8b7cff}p{color:#a5a6b7;line-height:1.6}a{display:inline-block;background:#20212c;color:#f5f5fa;padding:14px;border-radius:14px;margin:5px;text-decoration:none}</style></head><body><div class="tag">ТВОЙ ИНТЕРНЕТ</div><h1>Интернет.<br><span>По-твоему.</span></h1><p>Введи адрес или поисковый запрос в строку выше.</p><a href="https://duckduckgo.com">DuckDuckGo</a><a href="https://youtube.com">YouTube</a><a href="https://wikipedia.org">Wikipedia</a><a href="https://github.com">GitHub</a></body></html>""","text/html","UTF-8",null)
        updateTabs()
    }

    private fun updateTabs() { if (::tabsLabel.isInitialized && pages.isNotEmpty()) tabsLabel.text = "Вкладка " + (active+1) + " из " + pages.size + " · " + current().title }
    private fun current() = pages[active]

    private fun showTabs() {
        val items = pages.mapIndexed { i,p -> (if(i==active) "● " else "") + (i+1) + ". " + p.title }.toTypedArray()
        AlertDialog.Builder(this).setTitle("Вкладки").setItems(items) { _,i ->
            active=i; host.removeAllViews(); host.addView(current().web,FrameLayout.LayoutParams(-1,-1)); address.setText(current().url); updateTabs()
        }.setPositiveButton("＋ Новая") { _,_ -> newTab("") }.setNeutralButton("Закрыть текущую") { _,_ -> closeTab() }.show()
    }

    private fun closeTab() {
        if (pages.size == 1) { current().web.stopLoading(); home(); return }
        host.removeView(current().web); current().web.destroy(); pages.removeAt(active); active=(active-1).coerceAtLeast(0)
        host.addView(current().web,FrameLayout.LayoutParams(-1,-1)); address.setText(current().url); updateTabs()
    }

    private fun menu() {
        val opts = arrayOf("Поисковая система","Закладки","История","Режим для компьютера","Поделиться страницей","Обновить")
        AlertDialog.Builder(this).setTitle("Orbit Browser").setItems(opts) { _,i -> when(i) {
            0 -> chooseEngine()
            1 -> bookmarks()
            2 -> history()
            3 -> { desktop=!desktop; prefs.edit().putBoolean("desktop",desktop).apply(); pages.forEach { applyDesktop(it.web); it.web.reload() } }
            4 -> { val intent=Intent(Intent.ACTION_SEND).apply { type="text/plain"; putExtra(Intent.EXTRA_TEXT,current().url) }; startActivity(Intent.createChooser(intent,"Поделиться")) }
            5 -> current().web.reload()
        } }.show()
    }

    private fun chooseEngine() {
        val list=arrayOf("DuckDuckGo","Google","Bing","Brave")
        AlertDialog.Builder(this).setTitle("Поисковая система").setSingleChoiceItems(list,list.indexOf(engine).coerceAtLeast(0)) { d,i ->
            engine=list[i]; prefs.edit().putString("engine",engine).apply(); d.dismiss()
        }.setNegativeButton("Отмена",null).show()
    }

    private fun remember(url:String,title:String) {
        if (!url.startsWith("http")) return
        val old=JSONArray(prefs.getString("history","[]")); val next=JSONArray().put(JSONObject().put("url",url).put("title",title).put("time",System.currentTimeMillis()))
        for(i in 0 until minOf(old.length(),99)) { val item=old.optJSONObject(i) ?: continue; if(item.optString("url")!=url) next.put(item) }
        prefs.edit().putString("history",next.toString()).apply()
    }

    private fun bookmarks() {
        val arr=JSONArray(prefs.getString("bookmarks","[]"))
        val names=Array(arr.length()) { i -> arr.optJSONObject(i)?.optString("title","Закладка") ?: "Закладка" }
        AlertDialog.Builder(this).setTitle("Закладки").setItems(names) { _,i -> arr.optJSONObject(i)?.optString("url")?.let { go(it) } }
            .setPositiveButton("＋ Добавить текущую") { _,_ -> addBookmark() }.setNeutralButton("Готово",null).show()
    }

    private fun addBookmark() {
        val url=current().url
        if(!url.startsWith("http")) { Toast.makeText(this,"Сначала открой сайт",Toast.LENGTH_SHORT).show(); return }
        val arr=JSONArray(prefs.getString("bookmarks","[]"))
        for(i in 0 until arr.length()) if(arr.optJSONObject(i)?.optString("url")==url) { Toast.makeText(this,"Уже сохранено",Toast.LENGTH_SHORT).show(); return }
        arr.put(JSONObject().put("url",url).put("title",current().title)); prefs.edit().putString("bookmarks",arr.toString()).apply()
        Toast.makeText(this,"Добавлено в закладки",Toast.LENGTH_SHORT).show()
    }

    private fun history() {
        val arr=JSONArray(prefs.getString("history","[]"))
        val names=Array(arr.length()) { i -> arr.optJSONObject(i)?.optString("title","История") ?: "История" }
        AlertDialog.Builder(this).setTitle("История").setItems(names) { _,i -> arr.optJSONObject(i)?.optString("url")?.let { go(it) } }
            .setPositiveButton("Очистить") { _,_ -> prefs.edit().remove("history").apply() }.setNegativeButton("Закрыть",null).show()
    }

    override fun onBackPressed() { if(current().web.canGoBack()) current().web.goBack() else if(pages.size>1) closeTab() else super.onBackPressed() }
    override fun onDestroy() { pages.forEach { it.web.stopLoading(); it.web.destroy() }; super.onDestroy() }
}
