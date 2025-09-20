package com.example.consultadni

import android.os.Bundle
import android.text.InputFilter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.consultadni.databinding.ActivityBuroBinding

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
        if (isDni) {
            if (number.length != 8) {
                binding.numberInputLayout.error = "El DNI debe tener 8 dígitos."
                isValid = false
            }
        } else { // isRUC
            if (number.length != 11) {
                binding.numberInputLayout.error = "El RUC debe tener 11 dígitos."
                isValid = false
            }
        }

        if (isValid) {
            binding.numberInputLayout.error = null // Clear error
            val searchType = if (isDni) "DNI" else "RUC"
            Toast.makeText(this, "Buscando $searchType: $number", Toast.LENGTH_SHORT).show()
            // TODO: Implement actual search logic here
        }
    }
}
