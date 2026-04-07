package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myfin.data.AuthRepository

class ForgotPasswordActivity : AppCompatActivity() {
    private val repo = AuthRepository()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_forgot_password)

        val root = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        val emailInput = findViewById<EditText>(R.id.emailField)
        val btnReset = findViewById<Button>(R.id.btnReset)
        val btnBackToLogin = findViewById<TextView>(R.id.btnBackToLogin)

        btnReset.setOnClickListener {
            val email = emailInput.text.toString().trim()
            if (email.isEmpty()) {
                toast(getString(R.string.enter_email))
                return@setOnClickListener
            }
            
            repo.resetPassword(email) { ok, err ->
                if (ok) {
                    toast(getString(R.string.reset_password_email_sent, email))
                    // Возвращаемся на экран входа
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    toast(getString(R.string.error, err ?: getString(R.string.unknown_error)))
                }
            }
        }

        btnBackToLogin.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
