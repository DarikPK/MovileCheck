package com.example.consultadni

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.consultadni.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isSearchPageReady = false
    private var isProcessingSearch = false

    private val loginTimeoutHandler = Handler(Looper.getMainLooper())
    private var loginTimeoutRunnable: Runnable? = null

    companion object {
        private const val BASE_URL = "http://161.132.216.88/"
        private const val USER = "Prueba4"
        private const val PASS = "Prueba4"
        private const val TAG = "MainActivity"
        private const val LOGIN_TIMEOUT_MS = 15000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        binding.webView.loadUrl(BASE_URL)

        binding.searchButton.setOnClickListener { handleSearchClick() }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")
                determinePageAndAct(view)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Error loading page: ${error?.description}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error de red: ${error?.description}", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun determinePageAndAct(view: WebView?) {
        // Check for login form first, which is more specific than the search page.
        view?.evaluateJavascript("(function() { return document.querySelector('input[type=\"email\"]') !== null; })();") { isLoginPage ->
            if (isLoginPage == "true") {
                Log.d(TAG, "Login page detected.")
                injectLoginScript(view)
                return@evaluateJavascript
            }

            // If not login page, check for search form
            view?.evaluateJavascript("(function() { return document.getElementsByName('Documento').length > 0; })();") { isSearchPage ->
                if (isSearchPage == "true") {
                    Log.d(TAG, "Search page detected.")
                    handleSearchPageLoad(view)
                    return@evaluateJavascript
                }

                Log.w(TAG, "Unknown page state at ${view?.url}")
            }
        }
    }

    private fun injectLoginScript(view: WebView?) {
        Log.d(TAG, "Injecting login script.")
        // Check if we are already logged in or trying to log in, to avoid loops
        if (isSearchPageReady) return

        view?.evaluateJavascript("document.querySelector('input[type=\"email\"]').value = '$USER'; document.querySelector('input[type=\"password\"]').value = '$PASS';", null)
        view?.postDelayed({
            val jsClickLogin = "(function() { var buttons = document.getElementsByTagName('button'); for (var i = 0; i < buttons.length; i++) { if (buttons[i].textContent.includes('Ingresar')) { buttons[i].click(); return; } } })();"
            view.evaluateJavascript(jsClickLogin, null)
        }, 500)

        loginTimeoutRunnable = Runnable {
            if (!isSearchPageReady) {
                Log.e(TAG, "Login timeout!")
                Toast.makeText(this, "Error al iniciar sesión. Verifique las credenciales o la conexión.", Toast.LENGTH_LONG).show()
                binding.progressBar.visibility = View.GONE
            }
        }
        loginTimeoutHandler.postDelayed(loginTimeoutRunnable!!, LOGIN_TIMEOUT_MS)
    }

    private fun handleSearchPageLoad(view: WebView?) {
        loginTimeoutRunnable?.let { loginTimeoutHandler.removeCallbacks(it) }

        if (!isSearchPageReady) {
            isSearchPageReady = true
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Listo para buscar", Toast.LENGTH_SHORT).show()
            }
        }

        if (isProcessingSearch) {
            Log.d(TAG, "Search results available. Extracting data.")
            extractData(view)
        }
    }

    private fun handleSearchClick() {
        val dni = binding.dniInput.text.toString().trim()
        if (dni.length != 8) {
            binding.dniInput.error = "El DNI debe tener 8 dígitos"
            return
        }
        if (!isSearchPageReady) {
            Toast.makeText(this, "La página aún no está lista. Espere.", Toast.LENGTH_SHORT).show()
            return
        }
        if (isProcessingSearch) {
            Toast.makeText(this, "Búsqueda en progreso. Espere.", Toast.LENGTH_SHORT).show()
            return
        }
        performSearch(dni)
    }

    private fun performSearch(dni: String) {
        Log.d(TAG, "Performing search for DNI: $dni")
        isProcessingSearch = true
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            binding.textName.text = ""
            binding.textAge.text = ""
            binding.textAddress.text = ""
            binding.textPhones.text = ""
        }
        val jsSearchScript = "(function() { document.getElementsByName('Documento')[0].value = '$dni'; var buttons = document.getElementsByTagName('button'); for (var i = 0; i < buttons.length; i++) { if (buttons[i].textContent.includes('Buscar')) { buttons[i].click(); return; } } })();"
        binding.webView.evaluateJavascript(jsSearchScript, null)
    }

    private fun extractData(view: WebView?) {
        val jsExtractionScript = "(function() { const swal = document.querySelector('.swal2-container'); if (swal && (swal.innerText.includes('No se encontraron resultados') || swal.innerText.includes('sin resultados'))) { return JSON.stringify({ error: 'DNI no encontrado o sin resultados.' }); } let personaData = {}; let telefonosData = []; try { const pTable = document.evaluate('//div[@class=\"card\" and .//h3[text()=\"Personas\"]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if (pTable) { const cols = pTable.querySelectorAll('tr:first-child td'); if (cols.length >= 9) { personaData.nombreCompleto = `${'$'}{cols[0].innerText.trim()} ${'$'}{cols[1].innerText.trim()} ${'$'}{cols[2].innerText.trim()}`; personaData.edad = cols[4].innerText.trim(); personaData.direccion = cols[5].innerText.trim(); personaData.ubigeo = `${'$'}{cols[6].innerText.trim()} / ${'$'}{cols[7].innerText.trim()} / ${'$'}{cols[8].innerText.trim()}`; } } } catch (e) {} try { const tTable = document.evaluate('//div[@class=\"card\" and .//h3[text()=\"Teléfonos\"]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if (tTable) { tTable.querySelectorAll('tr').forEach(row => { const cols = row.querySelectorAll('td'); if (cols.length >= 3) { telefonosData.push(`${'$'}{cols[0].innerText.trim()} (${'$'}{cols[2].innerText.trim()})`); } }); } } catch(e) {} return JSON.stringify({ persona: personaData, telefonos: telefonosData }); })();"
        view?.evaluateJavascript(jsExtractionScript) { result ->
            isProcessingSearch = false // Search is complete, win or lose
            try {
                if (result == null || result == "null") throw Exception("JavaScript returned null.")
                val unescaped = result.substring(1, result.length - 1).replace("\\\"", "\"")
                val json = JSONObject(unescaped)

                if (json.has("error")) {
                    throw Exception(json.getString("error"))
                }

                val persona = json.getJSONObject("persona")
                val telefonos = json.getJSONArray("telefonos")
                updateUiWithResults(persona, telefonos)
            } catch (e: Exception) {
                Log.e(TAG, "Error processing result: ${e.message}")
                runOnUiThread {
                    Toast.makeText(this, e.message ?: "No se pudieron extraer los datos.", Toast.LENGTH_LONG).show()
                    binding.progressBar.visibility = View.GONE
                    binding.textName.text = "No se encontraron datos."
                }
            }
        }
    }

    private fun updateUiWithResults(persona: JSONObject, telefonos: JSONArray) {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            val nombre = persona.optString("nombreCompleto", "")
            val edad = persona.optString("edad", "")
            val direccion = persona.optString("direccion", "")
            val ubigeo = persona.optString("ubigeo", "")

            binding.textName.text = if (nombre.isBlank()) "" else nombre
            binding.textAge.text = if (edad.isBlank()) "" else edad
            binding.textAddress.text = if (direccion.isBlank()) "" else "$direccion - $ubigeo"

            val telefonosList = (0 until telefonos.length()).map { telefonos.getString(it) }
            binding.textPhones.text = if (telefonosList.isEmpty()) "" else telefonosList.joinToString("\n")

            if(nombre.isBlank() && telefonosList.isEmpty()) {
                 binding.textName.text = "No se encontraron datos para el DNI consultado."
                 binding.textPhones.text = "No se encontraron teléfonos."
            }
        }
    }
}
