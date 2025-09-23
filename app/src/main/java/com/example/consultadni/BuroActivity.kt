package com.example.consultadni

import android.os.Bundle
import android.text.InputFilter
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.consultadni.data.BuroRepository
import com.example.consultadni.databinding.ActivityBuroBinding
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.recaptcha.Recaptcha
import com.google.android.gms.recaptcha.RecaptchaClient
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class BuroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuroBinding
    private val repo = BuroRepository()
    private val recaptchaClient: RecaptchaClient by lazy {
        Recaptcha.getClient(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        binding.numberInput.filters = arrayOf(InputFilter.LengthFilter(8))
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

            launchRecaptchaAndProceed(number, isDni)
        }
    }

    private fun launchRecaptchaAndProceed(number: String, isDni: Boolean) {
        recaptchaClient.verify()
            .addOnSuccessListener { response ->
                val token = response.tokenResult
                if (!token.isNullOrEmpty()) {
                    Log.d("BuroActivity", "reCAPTCHA token received.")
                    proceedWithLogin(token, number, isDni)
                } else {
                    runOnUiThread { showError("Error de reCAPTCHA: Token vacío") }
                }
            }
            .addOnFailureListener { e ->
                Log.e("BuroActivity", "reCAPTCHA verification failed", e)
                val msg = if (e is ApiException) { "API error ${e.statusCode}" } else { e.message ?: "Error desconocido" }
                runOnUiThread { showError("Error en reCAPTCHA: $msg") }
            }
    }

    private fun proceedWithLogin(token: String, number: String, isDni: Boolean) {
        lifecycleScope.launch {
            val loginResult = repo.loginWithRecaptcha(token)
            loginResult.onSuccess {
                Log.d("BuroActivity", "Login successful.")
                proceedWithSearch(number, isDni)
            }.onFailure { e ->
                runOnUiThread { showError(e.message ?: "Error de login desconocido") }
            }
        }
    }

    private fun proceedWithSearch(number: String, isDni: Boolean) {
        lifecycleScope.launch {
            val consultaResult = repo.consultaNumero(number, isDni)
            consultaResult.onSuccess { json: JsonObject ->
                runOnUiThread {
                    showSuccess("Consulta OK: $json")
                }
            }.onFailure { e: Throwable ->
                runOnUiThread {
                    showError(e.message ?: "Error de consulta desconocido")
                }
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
