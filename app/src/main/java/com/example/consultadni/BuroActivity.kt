package com.example.consultadni

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.consultadni.databinding.ActivityBuroBinding
import com.google.android.play.core.integrity.IntegrityManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.security.MessageDigest

class BuroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuroBinding
    private lateinit var integrityManager: IntegrityManager

    // Credenciales de prueba
    private val defaultUsuario = "46736604"
    private val defaultContrasenia = "Ale07072022"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        integrityManager = IntegrityManagerFactory.create(this)

        setupListeners()
        binding.numberInput.filters = arrayOf(InputFilter.LengthFilter(8))

        setupCaptchaWebView()
    }

    private fun setupListeners() {
        binding.typeSelectorGroup.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.radio_dni -> {
                    binding.numberInputLayout.hint = getString(R.string.buro_radio_dni)
                    binding.numberInput.filters = arrayOf(InputFilter.LengthFilter(8))
                }
                R.id.radio_ruc -> {
                    binding.numberInputLayout.hint = getString(R.string.buro_radio_ruc)
                    binding.numberInput.filters = arrayOf(InputFilter.LengthFilter(11))
                }
            }
            binding.numberInput.text?.clear()
        }

        binding.btnBuscar.setOnClickListener {
            validateAndSearch()
        }
    }

    private fun validateAndSearch() {
        val number = binding.numberInput.text.toString().trim()
        val isDni = binding.radioDni.isChecked

        var isValid = true
        if (isDni && number.length != 8) {
            binding.numberInputLayout.error = "El DNI debe tener 8 dígitos."
            isValid = false
        } else if (!isDni && number.length != 11) {
            binding.numberInputLayout.error = "El RUC debe tener 11 dígitos."
            isValid = false
        }

        if (isValid) {
            binding.numberInputLayout.error = null
            binding.btnBuscar.isEnabled = false
            binding.progressBar.visibility = View.VISIBLE

            launchIntegrityCheckAndProceed(number, isDni)
        }
    }

    private fun launchIntegrityCheckAndProceed(number: String, isDni: Boolean) {
        val nonce = generateNonce(number)
        val request = IntegrityTokenRequest.builder()
            .setCloudProjectNumber(1055185231703)
            .setNonce(nonce)
            .build()

        val task = integrityManager.requestIntegrityToken(request)
        task.addOnSuccessListener { response ->
            val token = response.token()
            if (!token.isNullOrEmpty()) {
                Log.d("BuroActivity", "Integrity token received.")
                // Mostrar captcha WebView
                binding.webviewCaptcha.visibility = View.VISIBLE
                binding.webviewCaptcha.evaluateJavascript("javascript:iniciarCaptcha('$token')", null)
            } else {
                showError("Error: Token de integridad vacío")
            }
        }.addOnFailureListener { e ->
            showError("Error en Google Play Integrity: ${e.message ?: "desconocido"}")
        }
    }

    private fun generateNonce(number: String): String {
        val timestamp = System.currentTimeMillis().toString()
        val input = "$number-$timestamp"
        val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        val base64 = android.util.Base64.encodeToString(
            digest,
            android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP
        )
        return base64.trimEnd('=')
    }

    private fun setupCaptchaWebView() {
        binding.webviewCaptcha.apply {
            settings.javaScriptEnabled = true
            webViewClient = WebViewClient()
            addJavascriptInterface(object {
                @JavascriptInterface
                fun onCaptchaToken(captchaToken: String) {
                    runOnUiThread {
                        Log.d("BuroActivity", "Captcha token recibido: $captchaToken")
                        // Llamar a la consulta con usuario, contraseña, token de integridad y captcha
                        val number = binding.numberInput.text.toString().trim()
                        val isDni = binding.radioDni.isChecked
                        realizarConsulta(number, isDni, defaultUsuario, defaultContrasenia, captchaToken)
                    }
                }
            }, "Android")
            loadUrl("file:///android_asset/captcha.html") // HTML local con reCAPTCHA
            visibility = View.GONE
        }
    }

    private fun realizarConsulta(
        number: String,
        isDni: Boolean,
        usuario: String,
        contrasenia: String,
        captcha: String
    ) {
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val url = URL("https://intranet.elcristalperu.com/consulta") // ajusta endpoint
                    val conn = url.openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")

                    val postData = "usuario=$usuario&contrasenia=$contrasenia&numero=$number&isDni=$isDni&captcha=$captcha"

                    OutputStreamWriter(conn.outputStream).use { it.write(postData) }
                    conn.inputStream.bufferedReader().readText()
                }
                showSuccess("Consulta OK: $result")
            } catch (e: Exception) {
                showError("Error de consulta: ${e.message ?: "desconocido"}")
            }
        }
    }

    private fun showError(message: String) {
        binding.btnBuscar.isEnabled = true
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    private fun showSuccess(message: String) {
        binding.btnBuscar.isEnabled = true
        binding.progressBar.visibility = View.GONE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
}
