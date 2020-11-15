package com.joaohickmann.reservafiti

import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.joaohickmann.reservafiti.databinding.ActivityMainBinding
import com.joaohickmann.reservafiti.databinding.LayoutDiaBinding

private val DIAS = arrayOf("Domingo", "Segunda", "Terça", "Quarta", "Quinta", "Sexta", "Sábado")

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)

        val dias = List(6) {
            val dia = 7 - it
            val diaBinding = LayoutDiaBinding.inflate(layoutInflater, binding.llMain, false)
            viewModel.conectado.observe(this) { diaBinding.crdDia.isVisible = it }
            diaBinding.cbDia.text = DIAS[dia - 1]
            diaBinding.cbDia.setOnCheckedChangeListener { _, isChecked ->
                diaBinding.tilAtividade.isEnabled = isChecked
                diaBinding.tilHorario.isEnabled = isChecked
                if (!isChecked) {
                    diaBinding.edtAtividade.setText("")
                    diaBinding.edtHorario.setText("")
                }
            }
            diaBinding.tilAtividade.isEnabled = false
            diaBinding.tilHorario.isEnabled = false

            viewModel.atividades.observe(this) { list ->
                val adapter = ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, list)
                diaBinding.edtAtividade.setAdapter(adapter)
            }

            binding.llMain.addView(diaBinding.root, 1)
            diaBinding
        }.reversed()
        setContentView(binding.root)

        viewModel.conectado.observe(this) {
            binding.crdUsuario.isVisible = !it
            binding.btnSalvar.isVisible = it
        }

        binding.btnConectar.setOnClickListener {
            viewModel.login(
                email = binding.edtEmail.text.toString(),
                senha = binding.edtSenha.text.toString()
            )
        }
    }
}