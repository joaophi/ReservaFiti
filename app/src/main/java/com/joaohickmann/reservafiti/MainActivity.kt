package com.joaohickmann.reservafiti

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.LifecycleCoroutineScope
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DividerItemDecoration
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.joaohickmann.reservafiti.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

fun <T> Flow<T>.launchWhenStartedIn(scope: LifecycleCoroutineScope) = scope.launchWhenStarted {
    collect()
}

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ERROS

        viewModel.erros
            .onEach { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
            .launchWhenStartedIn(lifecycleScope)

        // LOGIN

        viewModel.loginStatus
            .onEach { status ->
                val loggedIn = status is LoggedIn
                binding.crdLogin.isVisible = !loggedIn
                binding.btnEntrar.isEnabled = status !is LogginIn
                binding.crdAtividade.isVisible = loggedIn
                binding.crdItens.isVisible = loggedIn
            }
            .launchWhenStartedIn(lifecycleScope)

        binding.btnEntrar.setOnClickListener {
            viewModel.conectar(
                binding.edtEmail.text?.toString().orEmpty(),
                binding.edtSenha.text?.toString().orEmpty(),
            )
        }

        // ATIVIDADES

        val atividadeAdapter = ArrayAdapter<FitiApi.AtividadeAcademia>(
            this,
            android.R.layout.simple_dropdown_item_1line
        )
        atividadeAdapter.setNotifyOnChange(false)
        binding.edtAtividade.setAdapter(atividadeAdapter)

        viewModel.atividades
            .onEach { atividades ->
                binding.tilAtividade.isEnabled = atividades.isNotEmpty()
                atividadeAdapter.clear()
                atividadeAdapter.addAll(atividades)
                atividadeAdapter.notifyDataSetChanged()
            }
            .launchWhenStartedIn(lifecycleScope)
        viewModel.atividade
            .onEach { binding.edtAtividade.setText(it?.toString().orEmpty(), false) }
            .launchWhenStartedIn(lifecycleScope)
        binding.edtAtividade.setOnItemClickListener { _, _, position, _ ->
            viewModel.atividade.value = atividadeAdapter.getItem(position)
        }

        // DIA

        val dias = DayOfWeek.values()

        val diasAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_dropdown_item_1line,
            dias.map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }
        )
        binding.edtDia.setAdapter(diasAdapter)

        viewModel.dia
            .onEach { dia ->
                binding.edtDia.setText(
                    dia.getDisplayName(TextStyle.FULL, Locale.getDefault()).orEmpty(),
                    false
                )
            }
            .launchWhenStartedIn(lifecycleScope)
        binding.edtDia.setOnItemClickListener { _, _, position, _ ->
            viewModel.dia.value = dias[position]
        }

        // HORARIO

        val horarioAdapter = ArrayAdapter<String>(
            this,
            android.R.layout.simple_dropdown_item_1line
        )
        horarioAdapter.setNotifyOnChange(false)
        binding.edtHorario.setAdapter(horarioAdapter)

        val horarioFormatter = DateTimeFormatter.ofPattern("HH:mm")
        viewModel.horarios
            .onEach { horarios ->
                binding.tilHorario.isEnabled = horarios.isNotEmpty()
                horarioAdapter.clear()
                horarioAdapter.addAll(horarios
                    .map { (inicio, fim) ->
                        "${horarioFormatter.format(inicio)} - ${horarioFormatter.format(fim)}"
                    })
                horarioAdapter.notifyDataSetChanged()
                binding.edtHorario.setOnItemClickListener { _, _, position, _ ->
                    viewModel.horario.value = horarios[position]
                }
            }
            .launchWhenStartedIn(lifecycleScope)
        viewModel.horario
            .onEach { horario ->
                if (horario == null) {
                    binding.edtHorario.setText("", false)
                    return@onEach
                }

                val (inicio, fim) = horario
                val text = "${horarioFormatter.format(inicio)} - ${horarioFormatter.format(fim)}"
                binding.edtHorario.setText(text, false)
            }
            .launchWhenStartedIn(lifecycleScope)

        // ADICIONAR

        combine(viewModel.atividade, viewModel.horario) { a -> a.all { it != null } }
            .onEach(binding.btnAdicionar::setEnabled)
            .launchWhenStartedIn(lifecycleScope)

        binding.btnAdicionar.setOnClickListener { viewModel.adicionar() }

        // WORKS

        val workAdapter = WorkAdapter { work ->
            MaterialAlertDialogBuilder(this)
                .setTitle("Excluir")
                .setNegativeButton("Cancelar", null)
                .setPositiveButton("Excluir") { _, _ -> viewModel.remover(work) }
                .show()
        }
        binding.rvItens.adapter = workAdapter
        binding.rvItens.addItemDecoration(
            DividerItemDecoration(this, DividerItemDecoration.VERTICAL)
        )

        viewModel.works
            .onEach(workAdapter::submitList)
            .launchWhenStartedIn(lifecycleScope)
    }
}