package com.example.consultadni

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.consultadni.databinding.ActivityLoginBinding
import com.google.firebase.auth.FirebaseAuth

class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth

    companion object {
        private const val PREFS_NAME = "LoginPrefs"
        private const val PREF_EMAIL = "email"
        private const val PREF_PASSWORD = "password"
        private const val PREF_SAVE_PASSWORD = "save_password"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = FirebaseAuth.getInstance()

        loadCredentials()

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text.toString().trim()
            val password = binding.passwordInput.text.toString().trim()

            if (email.isNotEmpty() && password.isNotEmpty()) {
                loginUser(email, password)
            } else {
                Toast.makeText(this, "Por favor, ingrese correo y contraseña", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun loadCredentials() {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savePassword = sharedPreferences.getBoolean(PREF_SAVE_PASSWORD, false)
        binding.savePasswordCheckbox.isChecked = savePassword
        if (savePassword) {
            binding.emailInput.setText(sharedPreferences.getString(PREF_EMAIL, ""))
            binding.passwordInput.setText(sharedPreferences.getString(PREF_PASSWORD, ""))
        }
    }

    private fun saveOrClearCredentials(email: String, password: String) {
        val sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        if (binding.savePasswordCheckbox.isChecked) {
            editor.putString(PREF_EMAIL, email)
            editor.putString(PREF_PASSWORD, password)
            editor.putBoolean(PREF_SAVE_PASSWORD, true)
        } else {
            editor.remove(PREF_EMAIL)
            editor.remove(PREF_PASSWORD)
            editor.remove(PREF_SAVE_PASSWORD)
        }
        editor.apply()
    }

    private fun loginUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("LoginActivity", "signInWithEmail:success")
                    saveOrClearCredentials(email, password)
                    val intent = Intent(this, DashboardActivity::class.java)
                    startActivity(intent)
                    finish()
                } else {
                    Log.w("LoginActivity", "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        baseContext, "Authentication failed. Verifique sus credenciales.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
    }
}
