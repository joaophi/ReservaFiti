package com.joaohickmann.reservafiti

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.joaohickmann.reservafiti.databinding.ActivityMainBinding
import kotlinx.coroutines.launch
import java.time.DayOfWeek

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val dias = sequenceOf(
            binding.cbSeg,
            binding.cbTer,
            binding.cbQua,
            binding.cbQui,
            binding.cbSex,
            binding.cbSab,
            binding.cbDom,
        )

        binding.btnAtivar.setOnClickListener {
            lifecycleScope.launch {
                try {
                    viewModel.ativar(
                        email = binding.edtEmail.text.toString(),
                        senha = binding.edtSenha.text.toString(),
                        dias.filter { it.isChecked }
                            .mapIndexed { i, _ -> DayOfWeek.of(i + 1) }
                            .toSet()
                    )
                    Toast.makeText(applicationContext, "Ativado", Toast.LENGTH_LONG).show()
                } catch (ex: Exception) {
                    Toast.makeText(applicationContext, ex.message, Toast.LENGTH_LONG).show()
                }
            }
        }

        binding.btnDesativar.setOnClickListener {
            viewModel.desativar()
            Toast.makeText(applicationContext, "Desativado", Toast.LENGTH_LONG).show()
        }
    }
}