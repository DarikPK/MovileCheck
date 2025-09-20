package com.example.consultadni

import android.annotation.SuppressLint
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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.example.consultadni.databinding.ActivityMainBinding
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var isSearchPageReady = false
    private var isProcessingSearch = false

    // Handlers for timeouts and polling
    private val loginTimeoutHandler = Handler(Looper.getMainLooper())
    private var loginTimeoutRunnable: Runnable? = null
    private val searchPollHandler = Handler(Looper.getMainLooper())
    private var searchPollRunnable: Runnable? = null
    private var searchStartTime = 0L

    companion object {
        private const val BASE_URL = "http://161.132.216.88/"
        private const val USER = "Prueba4"
        private const val PASS = "Prueba4"
        private const val TAG = "MainActivity"
        private const val LOGIN_TIMEOUT_MS = 20000L
        private const val SEARCH_TIMEOUT_MS = 20000L
        private const val POLLING_INTERVAL_MS = 500L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setupWebView()
        binding.webView.loadUrl(BASE_URL)
        binding.searchButton.setOnClickListener { handleSearchClick() }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Prevent memory leaks by removing callbacks
        loginTimeoutHandler.removeCallbacksAndMessages(null)
        searchPollHandler.removeCallbacksAndMessages(null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        binding.webView.settings.javaScriptEnabled = true
        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG, "Page finished loading: $url")
                // This is now only for detecting the initial login/search page states
                determineInitialPageState(view)
            }

            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Log.e(TAG, "Error de red: ${error?.description}")
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Error de red: ${error?.description}", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
        }
    }

    private fun determineInitialPageState(view: WebView?) {
        // We only care about detecting the search page to enable the UI
        view?.evaluateJavascript("(function() { return document.getElementsByName('Documento').length > 0; })();") { isSearchPage ->
            if (isSearchPage == "true" && !isSearchPageReady) {
                Log.d(TAG, "Search page is ready.")
                loginTimeoutRunnable?.let { loginTimeoutHandler.removeCallbacks(it) }
                isSearchPageReady = true
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Listo para buscar", Toast.LENGTH_SHORT).show()
                }
            } else if (!isSearchPageReady) {
                // If it's not the search page, assume it's the login page
                injectLoginScript(view)
            }
        }
    }

    private fun injectLoginScript(view: WebView?) {
        Log.d(TAG, "Attempting to inject login script.")
        // Set a timeout only once
        if (loginTimeoutRunnable == null) {
            loginTimeoutRunnable = Runnable {
                if (!isSearchPageReady) {
                    Log.e(TAG, "Login timeout!")
                    runOnUiThread {
                        Toast.makeText(this, "Error al iniciar sesión. Verifique credenciales o conexión.", Toast.LENGTH_LONG).show()
                        binding.progressBar.visibility = View.GONE
                    }
                }
            }
            loginTimeoutHandler.postDelayed(loginTimeoutRunnable!!, LOGIN_TIMEOUT_MS)
        }

        val jsLogin = "document.querySelector('input[type=\"email\"]').value = '$USER'; document.querySelector('input[type=\"password\"]').value = '$PASS'; (function() { var btns = document.getElementsByTagName('button'); for (var i = 0; i < btns.length; i++) { if (btns[i].textContent.includes('Ingresar')) { btns[i].click(); return; } } })();"
        view?.evaluateJavascript(jsLogin, null)
    }

    private fun isWithinAllowedHours(): Boolean {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY) // 24-hour format
        val minute = calendar.get(Calendar.MINUTE)

        // Allowed from 7:00 (7) to 23:30 (11:30 PM)
        val isAfterStartTime = hour >= 7
        val isBeforeEndTime = hour < 23 || (hour == 23 && minute <= 30)

        return isAfterStartTime && isBeforeEndTime
    }

    private fun handleSearchClick() {
        if (!isWithinAllowedHours()) {
            AlertDialog.Builder(this)
                .setTitle("Horario de Consulta")
                .setMessage("Las consultas no están disponibles más que en el horario de 7am a 11:30pm.")
                .setPositiveButton("Entendido", null)
                .show()
            return
        }

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
        // Stop any previous polling task before starting a new one. This is critical.
        searchPollHandler.removeCallbacksAndMessages(null)

        Log.d(TAG, "Performing search for DNI: $dni")
        isProcessingSearch = true
        runOnUiThread {
            binding.progressBar.visibility = View.VISIBLE
            binding.textName.text = ""
            binding.textBirthdate.text = ""
            binding.textAge.text = ""
            binding.textAddress.text = ""
            binding.textPhones.text = ""
        }

        // Click the search button on the web page
        val jsSearchScript = "(function() { document.getElementsByName('Documento')[0].value = '$dni'; var btns = document.getElementsByTagName('button'); for (var i = 0; i < btns.length; i++) { if (btns[i].textContent.includes('Buscar')) { btns[i].click(); return; } } })();"
        binding.webView.evaluateJavascript(jsSearchScript, null)

        // Immediately clear previous results from the DOM to ensure the poller waits for new data
        val jsClearResultsScript = "(function() { try { document.evaluate('//div[@class=\"card\" and .//h3[contains(., \"Personas\")]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.innerHTML = ''; document.evaluate('//div[@class=\"card\" and .//h3[contains(., \"Teléfonos\")]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue.innerHTML = ''; } catch(e){} })();"
        binding.webView.postDelayed({
            binding.webView.evaluateJavascript(jsClearResultsScript, null)
        }, 200) // A small delay to ensure the search click has been processed before clearing

        // Start polling for results
        searchStartTime = System.currentTimeMillis()
        searchPollRunnable = object : Runnable {
            override fun run() {
                Log.d(TAG, "Polling for search results...")
                if (System.currentTimeMillis() - searchStartTime > SEARCH_TIMEOUT_MS) {
                    Log.e(TAG, "Search timed out.")
                    isProcessingSearch = false
                    runOnUiThread {
                        binding.progressBar.visibility = View.GONE
                        Toast.makeText(this@MainActivity, "La búsqueda tardó demasiado.", Toast.LENGTH_LONG).show()
                    }
                    return
                }
                checkSearchResults(binding.webView)
            }
        }
        searchPollHandler.postDelayed(searchPollRunnable!!, POLLING_INTERVAL_MS)
    }

    private fun checkSearchResults(view: WebView) {
        val jsExtractionScript = "(function() { const loadingIndicator = document.evaluate(\"//*[contains(., 'Cargando') or contains(., 'espere')]\", document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if (loadingIndicator && loadingIndicator.offsetParent !== null) { return JSON.stringify({ status: 'loading', message: 'Indicador de carga visible.' }); } const swal = document.querySelector('.swal2-container'); if (swal && (swal.innerText.includes('No se encontraron resultados') || swal.innerText.includes('sin resultados'))) { return JSON.stringify({ status: 'error', message: 'DNI no encontrado o sin resultados.' }); } const pTable = document.evaluate('//div[@class=\"card\" and .//h3[contains(., \"Personas\")]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if (pTable && pTable.querySelector('tr') && pTable.innerText.trim().length > 0) { let personaData = {}; let telefonosData = []; try { const cols = pTable.querySelectorAll('tr:first-child td'); if (cols.length >= 9) { personaData.nombreCompleto = `${'$'}{cols[0].innerText.trim()} ${'$'}{cols[1].innerText.trim()} ${'$'}{cols[2].innerText.trim()}`; personaData.fechaNacimiento = cols[3].innerText.trim(); personaData.edad = cols[4].innerText.trim(); personaData.direccion = cols[5].innerText.trim(); personaData.ubigeo = `${'$'}{cols[6].innerText.trim()} / ${'$'}{cols[7].innerText.trim()} / ${'$'}{cols[8].innerText.trim()}`; } } catch (e) {} try { const tTable = document.evaluate('//div[@class=\"card\" and .//h3[contains(., \"Teléfonos\")]]//tbody', document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue; if (tTable) { tTable.querySelectorAll('tr').forEach(row => { const cols = row.querySelectorAll('td'); if (cols.length >= 3) { telefonosData.push(`${'$'}{cols[0].innerText.trim()} (${'$'}{cols[2].innerText.trim()})`); } }); } } catch(e) {} return JSON.stringify({ status: 'success', data: { persona: personaData, telefonos: telefonosData } }); } return JSON.stringify({ status: 'loading', message: 'Esperando tabla de resultados...' }); })();"

        view.evaluateJavascript(jsExtractionScript) { result ->
            try {
                if (result == null || result == "null") {
                    // Script might fail to execute, poll again
                    searchPollHandler.postDelayed(searchPollRunnable!!, POLLING_INTERVAL_MS)
                    return@evaluateJavascript
                }
                val unescaped = result.substring(1, result.length - 1).replace("\\\"", "\"")
                val json = JSONObject(unescaped)

                when (json.optString("status")) {
                    "success" -> {
                        isProcessingSearch = false
                        val data = json.getJSONObject("data")
                        val persona = data.getJSONObject("persona")
                        val telefonos = data.getJSONArray("telefonos")
                        updateUiWithResults(persona, telefonos)
                    }
                    "error" -> {
                        isProcessingSearch = false
                        throw Exception(json.getString("message"))
                    }
                    "loading" -> {
                        // It's still loading, post the runnable again
                        searchPollHandler.postDelayed(searchPollRunnable!!, POLLING_INTERVAL_MS)
                    }
                }
            } catch (e: Exception) {
                isProcessingSearch = false
                Log.e(TAG, "Error processing result: ${e.message}")
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    Toast.makeText(this, e.message ?: "No se pudieron extraer los datos.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateUiWithResults(persona: JSONObject, telefonos: JSONArray) {
        runOnUiThread {
            binding.progressBar.visibility = View.GONE
            val nombre = persona.optString("nombreCompleto", "")
            val fechaNacimiento = persona.optString("fechaNacimiento", "")
            val edad = persona.optString("edad", "")
            val direccion = persona.optString("direccion", "")
            val ubigeo = persona.optString("ubigeo", "")

            binding.textName.text = if (nombre.isBlank()) "" else nombre
            binding.textBirthdate.text = if (fechaNacimiento.isBlank()) "" else fechaNacimiento
            binding.textAge.text = if (edad.isBlank()) "" else edad
            binding.textAddress.text = if (direccion.isBlank()) "" else "$direccion - $ubigeo"

            val telefonosList = (0 until telefonos.length()).map { telefonos.getString(it) }
            binding.textPhones.text = if (telefonosList.isEmpty()) "" else telefonosList.joinToString("\n")

            if (nombre.isBlank() && telefonosList.isEmpty()) {
                binding.textName.text = "No se encontraron datos para el DNI consultado."
                binding.textPhones.text = "No se encontraron teléfonos."
            }
        }
    }
}
