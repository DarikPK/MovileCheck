package com.example.consultadni

import android.os.Bundle
import android.text.InputFilter
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.consultadni.data.BuroRepository
import com.example.consultadni.databinding.ActivityBuroBinding
import com.google.gson.JsonObject
import kotlinx.coroutines.launch

class BuroActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBuroBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBuroBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        // Set initial state for DNI
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

        binding.buroSearchButton.setOnClickListener {
            validateAndSearch()
        }
    }

    private fun validateAndSearch() {
        val number = binding.numberInput.text.toString()
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
            binding.buroSearchButton.isEnabled = false
            // binding.progressBar.visibility = View.VISIBLE

            val repo = BuroRepository()
            lifecycleScope.launch {
                val result = repo.consultaNumero(number, isDni)

                binding.buroSearchButton.isEnabled = true
                // binding.progressBar.visibility = View.GONE

                result.onSuccess { json: JsonObject ->
                    Toast.makeText(this@BuroActivity, "Consulta OK: ${json}", Toast.LENGTH_LONG).show()
                }.onFailure { e: Throwable ->
                    Toast.makeText(this@BuroActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
