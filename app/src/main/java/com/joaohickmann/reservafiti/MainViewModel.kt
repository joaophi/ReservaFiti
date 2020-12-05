package com.joaohickmann.reservafiti

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://evomobile.azurewebsites.net")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val fitiApi: FitiApi = retrofit.create()

    private val _erros = MutableSharedFlow<Throwable>()
    val erros: SharedFlow<Throwable> get() = _erros

    private val _loginStatus = MutableStateFlow<LoginStatus>(LoggedOut)
    val loginStatus: StateFlow<LoginStatus> get() = _loginStatus

    fun conectar(email: String, senha: String) {
        viewModelScope.launch {
            _loginStatus.emit(LogginIn)
            try {
                val buscarUsuario = fitiApi.buscarUsuario(email)
                    .unwrap()

                val autenticarUsuario =
                    fitiApi.autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
                        .unwrap()
                        .first()

                val obterDadosLogin = fitiApi
                    .obterDadosLogin(
                        buscarUsuario.idUsuarioToken,
                        autenticarUsuario.idClienteW12Token,
                        buscarUsuario.idAspeNetUser
                    )
                    .unwrap()

                _loginStatus.emit(
                    LoggedIn(
                        email,
                        senha,
                        obterDadosLogin.token,
                        obterDadosLogin.idFilial
                    )
                )
            } catch (ex: Exception) {
                _loginStatus.emit(LoggedOut)
                _erros.emit(ex)
            }
        }
    }

    val atividade = MutableStateFlow<FitiApi.AtividadeAcademia?>(null)
    val atividades = _loginStatus
        .filterIsInstance<LoggedIn>()
        .transformLatest { loggedIn ->
            try {
                val atividades = fitiApi
                    .atividadesAcademia("Bearer ${loggedIn.token}", loggedIn.idFilial)
                    .unwrap()
                emit(atividades)
                if (atividade.value !in atividades)
                    atividade.value = null
            } catch (ex: Exception) {
                emit(emptyList())
                atividade.value = null
                _erros.emit(ex)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val dia = MutableStateFlow(LocalDate.now().dayOfWeek)

    val horario = MutableStateFlow<Pair<LocalTime, LocalTime>?>(null)
    val horarios = _loginStatus
        .filterIsInstance<LoggedIn>()
        .flatMapLatest { loggedIn ->
            combineTransform(atividade.filterNotNull(), dia) { atividade, dia ->
                try {
                    val data = generateSequence(LocalDate.now()) { it.plusDays(1) }
                        .first { it.dayOfWeek == dia }
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    val horarios = fitiApi
                        .obterAgendaDia(
                            authorization = "Bearer ${loggedIn.token}",
                            loggedIn.idFilial,
                            data,
                            filtroInicio = null,
                            filtroFim = null,
                            atividade.idAtividade
                        )
                        .unwrap()
                        .map { LocalTime.parse(it.horaInicio) to LocalTime.parse(it.horaFim) }
                    emit(horarios)
                    if (horario.value !in horarios)
                        horario.value = null
                } catch (ex: Exception) {
                    emit(emptyList())
                    horario.value = null
                    _erros.emit(ex)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun adicionar() {
        viewModelScope.launch {
            try {
                val loginStatus = loginStatus.value
                if (loginStatus !is LoggedIn)
                    throw Exception("Não conectado")

                val atividade = atividade.value ?: throw Exception("Informe a atividade")

                val horario = horario.value?.first ?: throw Exception("Informe o horário")

                workManager.enqueue(
                    OneTimeWorkRequestBuilder<ReservaWorker>()
                        .addTag("email=${loginStatus.email}")
                        .addTag("atividade=${atividade.idAtividade}")
                        .addTag("dia=${dia.value.value}")
                        .addTag("hora=${horario.toSecondOfDay()}")
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setInputData(
                            Data.Builder()
                                .putString("email", loginStatus.email)
                                .putString("senha", loginStatus.senha)
                                .putInt("atividade", atividade.idAtividade)
                                .putInt("dia", dia.value.value)
                                .putInt("hora", horario.toSecondOfDay())
                                .build()
                        )
                        .build()
                ).await()
            } catch (ex: Exception) {
                _erros.emit(ex)
            }
        }
    }

    val works = loginStatus
        .filterIsInstance<LoggedIn>()
        .flatMapLatest {
            val workInfosLD = workManager
                .getWorkInfosByTagLiveData("email=${it.email}")
                .asFlow()
                .map { list -> list.filterNot { it.state.isFinished } }

            combine(workInfosLD, atividades) { workInfos, atividades ->
                workInfos.map { workInfo ->
                    val atividade = workInfo.tags
                        .first { it.startsWith("atividade=") }
                        .substringAfter('=')
                        .toInt()

                    val dia = workInfo.tags
                        .first { it.startsWith("dia=") }
                        .substringAfter('=')
                        .toInt()

                    val hora = workInfo.tags
                        .first { it.startsWith("hora=") }
                        .substringAfter('=')
                        .toInt()

                    Work(
                        workInfo.id,
                        atividadeAcademia = atividades
                            .find { it.idAtividade == atividade }
                            ?: FitiApi.AtividadeAcademia(atividade, ""),
                        DayOfWeek.of(dia),
                        LocalTime.ofSecondOfDay(hora.toLong())
                    )
                }
            }
        }
        .map {
            it.sortedWith { o1, o2 ->
                var cmp = o1.dia.compareTo(o2.dia)
                if (cmp != 0)
                    return@sortedWith cmp

                cmp = o1.hora.compareTo(o2.hora)
                if (cmp != 0)
                    return@sortedWith cmp

                o1.atividadeAcademia.idAtividade.compareTo(o2.atividadeAcademia.idAtividade)
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun remover(work: Work) {
        viewModelScope.launch {
            try {
                workManager.cancelWorkById(work.id).await()
            } catch (ex: Exception) {
                _erros.emit(ex)
            }
        }
    }
}