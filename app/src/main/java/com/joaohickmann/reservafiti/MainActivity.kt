package com.joaohickmann.reservafiti

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.joaohickmann.reservafiti.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.util.*

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtivar.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val dias = mutableSetOf<Int>()
                    if (binding.cbSeg.isChecked)
                        dias += Calendar.MONDAY
                    if (binding.cbTer.isChecked)
                        dias += Calendar.TUESDAY
                    if (binding.cbQua.isChecked)
                        dias += Calendar.WEDNESDAY
                    if (binding.cbQui.isChecked)
                        dias += Calendar.THURSDAY
                    if (binding.cbSex.isChecked)
                        dias += Calendar.FRIDAY
                    if (binding.cbSab.isChecked)
                        dias += Calendar.SATURDAY

                    viewModel.ativar(
                        email = binding.edtEmail.text.toString(),
                        senha = binding.edtSenha.text.toString(),
                        dias
                    )
                    Toast.makeText(applicationContext, "Ativado", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(applicationContext, ex.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}