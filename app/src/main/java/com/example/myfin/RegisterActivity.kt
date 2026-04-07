package com.example.myfin

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.myfin.data.AuthRepository
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {
    private val repo = AuthRepository()
    private lateinit var auth: FirebaseAuth

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LanguageHelper.wrapContext(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        // Инициализируем FirebaseAuth
        auth = FirebaseAuth.getInstance()

        // Используем корневой content вместо поиска id main, чтобы избежать ошибки инструмента
        val root = findViewById<android.view.View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val sysBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(sysBars.left, sysBars.top, sysBars.right, sysBars.bottom)
            insets
        }

        val nameInput = findViewById<EditText>(R.id.nameField)
        val emailInput = findViewById<EditText>(R.id.emailField)
        val passInput = findViewById<EditText>(R.id.passwordField)
        val pass2Input = findViewById<EditText>(R.id.confirmPasswordField)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnLoginLink = findViewById<TextView>(R.id.btnLogin)

        btnRegister.setOnClickListener {
            val fio = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val pass = passInput.text.toString()
            val pass2 = pass2Input.text.toString()

            if (fio.isEmpty() || email.isEmpty() || pass.isEmpty() || pass2.isEmpty()) {
                toast(getString(R.string.fill_all_fields))
                return@setOnClickListener
            }
            if (pass != pass2) {
                toast(getString(R.string.passwords_do_not_match))
                return@setOnClickListener
            }

            repo.register(fio, email, pass) { ok, err ->
                if (ok) {
                    toast(getString(R.string.registration_success))

                    // После успешной регистрации переходим к созданию PIN-кода
                    val intent = Intent(this, CreatePinActivity::class.java)
                    intent.putExtra("from_register", true)
                    startActivity(intent)
                    finish()
                } else {
                    toast(getString(R.string.error, err ?: getString(R.string.unknown_error)))
                }
            }
        }

        btnLoginLink.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}