package com.peaceray.codeword.presentation.view.activities

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.peaceray.codeword.R
import com.peaceray.codeword.databinding.ActivityDocViewerBinding
import dagger.hilt.android.AndroidEntryPoint
import java.net.URL

@AndroidEntryPoint
class DocViewerActivity: CodeWordActivity() {

    //region Instantiation and Arguments
    //---------------------------------------------------------------------------------------------
    enum class Documents(val assetPath: String) {
        APP_INFO("documents/app_info/app_info.html"),
        CREDITS("documents/app_info/credits.html");
    }

    companion object {

        const val NAME = "DocViewerActivity"
        const val ARG_ASSET_PATH = "${NAME}_ASSET_PATH"
        const val ARG_URL = "${NAME}_URL"
        const val ARG_DOCUMENT = "${NAME}_DOCUMENT"
        const val ARG_ASSET_PATH_ROOT = "${NAME}_ASSET_PATH_ROOT"

        fun newBundle(
            assetPath: String? = null,
            assetPathRoot: String? = null,
            url: URL? = null,
            urlString: String? = null,
            document: Documents? = null
        ): Bundle {
            if (url != null && urlString != null) {
                throw IllegalArgumentException("Cannot specify both url and urlString; give one (or none)")
            }

            val bundle = Bundle()
            if (assetPath != null) bundle.putString(ARG_ASSET_PATH, assetPath)
            if (assetPathRoot != null) bundle.putString(ARG_ASSET_PATH_ROOT, assetPathRoot)
            if (url != null) bundle.putString(ARG_URL, url.toString())
            if (urlString != null) bundle.putString(ARG_URL, urlString)
            if (document != null) bundle.putString(ARG_DOCUMENT, document.toString())

            return bundle
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Lifecycle
    //---------------------------------------------------------------------------------------------
    private lateinit var binding: ActivityDocViewerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDocViewerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
        supportActionBar?.setTitle(R.string.document_viewer_name)

        binding.webView.webViewClient = webViewClient
        binding.webView.loadUrl(getDocumentUrlFromExtras())
        // val content = loadAssetText(path)
        // binding.textView.text = Html.fromHtml(content, Html.FROM_HTML_MODE_LEGACY)
    }

    override fun onOptionsItemSelected(item: MenuItem) = when(item.itemId) {
        android.R.id.home -> {
            finish()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (!popUrlStack()) super.onBackPressed()
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Web View
    //---------------------------------------------------------------------------------------------
    private val urlStack = mutableListOf<String>()

    private fun popUrlStack(): Boolean {
        if (urlStack.isNotEmpty()) {
            val url = urlStack.removeAt(urlStack.size - 1)
            binding.webView.loadUrl(url)
            return true
        }

        return false
    }

    private val webViewClient = object: WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            if (view != null) {
                supportActionBar?.title = view.title
            }
        }

        override fun shouldOverrideUrlLoading(
            view: WebView?,
            request: WebResourceRequest?
        ): Boolean {
            return overrideUrlLoading(view, request?.url?.toString()) || super.shouldOverrideUrlLoading(view, request)
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return overrideUrlLoading(view, url) || super.shouldOverrideUrlLoading(view, url)
        }

        fun overrideUrlLoading(view: WebView?, path: String?) = when {
            path == null -> false
            path.startsWith("activity://") -> {
                TODO("Implement activity:// paths in DocViewActivity")
            }
            path.startsWith("mailto:") -> {
                val address: String = path.substring(7)
                val emailIntent = Intent(Intent.ACTION_SENDTO)
                emailIntent.setData(Uri.parse("mailto:"))
                emailIntent.putExtra(Intent.EXTRA_EMAIL, arrayOf(address))
                emailIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.document_viewer_send_email_subject))
                startActivity(Intent.createChooser(emailIntent, getString(R.string.document_viewer_send_email_chooser)))
                true
            }
            path.contains("://") -> {
                // send to a browser activity (or other viewer, e.g. NYT Games app)
                val uri = Uri.parse(path)
                val intent = Intent(Intent.ACTION_VIEW, uri)
                startActivity(intent)
                true
            }
            else -> {
                val oldUrl = view?.url
                if (oldUrl != null) urlStack.add(oldUrl)
                view?.loadUrl(path)
                true
            }
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

    //region Helpers
    //---------------------------------------------------------------------------------------------
    private fun getDocumentUrlFromExtras(): String {
        val extras = intent.extras!!

        val assetPathRoot = extras.getString(ARG_ASSET_PATH_ROOT, "file:///android_asset/")
        val assetPath = extras.getString(ARG_ASSET_PATH)
        val url = extras.getString(ARG_URL)
        val documentStr = extras.getString(ARG_DOCUMENT)
        return when {
            url != null -> url
            assetPath != null -> assetPathRoot.join(assetPath)
            documentStr != null -> {
                assetPathRoot.join(Documents.valueOf(documentStr).assetPath)
            }
            else -> throw IllegalStateException("Instantiated with an improperly configured 'extras' Bundle (no document path)")
        }
    }

    private fun String.join(path: String): String {
        // don't use File.resolve; it shortens the "///" pathing of Android Asset files to "/"
        return when {
            isEmpty() -> path
            path.isEmpty() -> this
            endsWith("/") -> this + path.trimStart('/')
            else -> this + "/" + path.trimStart('/')
        }
    }
    //---------------------------------------------------------------------------------------------
    //endregion

}