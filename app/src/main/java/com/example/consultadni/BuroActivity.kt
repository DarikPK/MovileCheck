package com.example.consultadni

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.consultadni.data.BuroRepository
import com.example.consultadni.databinding.ActivityBuroBinding
import com.google.android.gms.safetynet.SafetyNet
import com.google.android.gms.safetynet.SafetyNetApi
import kotlinx.coroutines.launch

class BuroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuroBinding
    private val repo = BuroRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Botón Buscar -> lanza flow captcha -> login demo -> consulta
        binding.btnBuscar.setOnClickListener {
            val numero = binding.numberInput.text.toString().trim()
            val isDni = binding.radioDni.isChecked

            // validaciones mínimas
            if (isDni && numero.length != 8) {
                binding.numberInputLayout.error = "El DNI debe tener 8 dígitos."
                return@setOnClickListener
            } else if (!isDni && numero.length != 11) {
                binding.numberInputLayout.error = "El RUC debe tener 11 dígitos."
                return@setOnClickListener
            } else {
                binding.numberInputLayout.error = null
            }

            // Deshabilitar UI mientras trabaja
            binding.btnBuscar.isEnabled = false
            binding.progressBar.visibility = android.view.View.VISIBLE

            // Lanzar reCAPTCHA
            launchRecaptcha { token, recaptchaError ->
                if (recaptchaError != null) {
                    runOnUiThread {
                        binding.btnBuscar.isEnabled = true
                        binding.progressBar.visibility = android.view.View.GONE
                        Toast.makeText(this, "Error reCAPTCHA: $recaptchaError", Toast.LENGTH_LONG).show()
                    }
                    return@launchRecaptcha
                }

                // Con token: hacer login con credenciales demo
                lifecycleScope.launch {
                    val loginResult = repo.loginWithRecaptcha(token!!)
                    loginResult.onSuccess {
                        // Login OK -> ahora consulta
                        val consulta = repo.consultaNumero(numero, isDni)
                        consulta.onSuccess { json ->
                            runOnUiThread {
                                binding.btnBuscar.isEnabled = true
                                binding.progressBar.visibility = android.view.View.GONE
                                Toast.makeText(this@BuroActivity, "Consulta OK", Toast.LENGTH_LONG).show()
                                // Aquí podrías navegar a pantalla de resultados y pasar json
                            }
                        }.onFailure { e ->
                            runOnUiThread {
                                binding.btnBuscar.isEnabled = true
                                binding.progressBar.visibility = android.view.View.GONE
                                Toast.makeText(this@BuroActivity, "Error consulta: ${e.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }.onFailure { e ->
                        runOnUiThread {
                            binding.btnBuscar.isEnabled = true
                            binding.progressBar.visibility = android.view.View.GONE
                            Toast.makeText(this@BuroActivity, "Error login: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }

    /**
     * Ejecuta SafetyNet reCAPTCHA y devuelve token o error vía callback.
     * onComplete(token: String?, errorMsg: String?)
     */
    private fun launchRecaptcha(onComplete: (token: String?, errorMsg: String?) -> Unit) {
        SafetyNet.getClient(this).verifyWithRecaptcha(AppConfig.RECAPTCHA_SITE_KEY)
            .addOnSuccessListener(this) { response: SafetyNetApi.RecaptchaTokenResponse ->
                val token = response.tokenResult
                if (!token.isNullOrEmpty()) {
                    onComplete(token, null)
                } else {
                    onComplete(null, "Token vacío")
                }
            }
            .addOnFailureListener(this) { e ->
                val msg = if (e is com.google.android.gms.common.api.ApiException) {
                    "API error ${e.statusCode}"
                } else {
                    e.message ?: "Error desconocido"
                }
                onComplete(null, msg)
            }
    }
}
