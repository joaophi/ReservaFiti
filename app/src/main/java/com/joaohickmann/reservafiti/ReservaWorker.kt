package com.joaohickmann.reservafiti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import androidx.work.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ReservaWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    private val retrofit = Retrofit.Builder()
        .baseUrl("https://evomobile.azurewebsites.net")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val fitiApi: FitiApi = retrofit.create()

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "reserva",
                "Reserva",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager: NotificationManager = applicationContext.getSystemService()
                ?: return
            notificationManager.createNotificationChannel(channel)
        }

        val builder = NotificationCompat.Builder(applicationContext, "reserva")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        val id = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC).toInt()
        NotificationManagerCompat.from(applicationContext).notify(id, builder)
    }

    override suspend fun doWork(): Result {
        try {
            val email = inputData.getString("email")
                ?: return Result.failure()

            val senha = inputData.getString("senha")
                ?: return Result.failure()

            val atividade = inputData.getInt("atividade", -1)
            if (atividade == -1) return Result.failure()

            val diaInt = inputData.getInt("dia", -1)
            if (diaInt == -1) return Result.failure()
            val dia = DayOfWeek.of(diaInt)

            val horaInt = inputData.getInt("hora", -1)
            if (horaInt == -1) return Result.failure()
            val hora = LocalTime.ofSecondOfDay(horaInt.toLong())

            val agora = LocalDateTime.now()
            val horario = generateSequence(LocalDate.now().atTime(hora)) { it.plusDays(1) }
                .filter { it.isAfter(agora) }
                .filter { it.dayOfWeek == dia }
                .first()

            val buscarUsuario = fitiApi.buscarUsuario(email).unwrap()
            val autenticarUsuario = fitiApi
                .autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
                .unwrap()
                .first()
            val obterDadosLogin = fitiApi.obterDadosLogin(
                buscarUsuario.idUsuarioToken,
                autenticarUsuario.idClienteW12Token,
                buscarUsuario.idAspeNetUser,
            ).unwrap()

            val authorization = "Bearer ${obterDadosLogin.token}"
            val data = horario.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            val filtroInicio = hora.format(DateTimeFormatter.ofPattern("0.HH:mm"))

            val obterAgendaDia = fitiApi.obterAgendaDia(
                authorization,
                obterDadosLogin.idFilial,
                data,
                filtroInicio,
                filtroFim = null,
                atividade,
            ).unwrap().first { LocalTime.parse(it.horaInicio) == hora }

            val units = listOf(ChronoUnit.MINUTES, ChronoUnit.HOURS, ChronoUnit.DAYS)
            val horaInicioReserva = obterAgendaDia.horaInicioReserva
                .split(':')
                .asReversed()
                .foldIndexed(Duration.ZERO) { i, acc, d ->
                    acc.plus(Duration.of(d.toLongOrNull() ?: 0, units[i]))
                }

            val prox = if (agora.isAfter(horario - horaInicioReserva)) {
                if (obterAgendaDia.status != 5) { // JÁ RESERVADO
                    fitiApi.participarAtividade(
                        authorization,
                        obterDadosLogin.idFilial,
                        data,
                        obterAgendaDia.idConfiguracao,
                    ).unwrap()

                    val atv = try {
                        fitiApi.atividadesAcademia(authorization, obterDadosLogin.idFilial)
                            .unwrap()
                            .first { it.idAtividade == atividade }
                            .nomeAtividade
                    } catch (ex: Exception) {
                        null
                    }

                    showNotification("Reservado", "Atividade: $atividade - $atv\nPara: $horario")
                }
                horario.plusWeeks(1)
            } else {
                horario
            }

            val proxHoraInicioReserva = try {
                val proxObterAgendaDia = fitiApi.obterAgendaDia(
                    authorization,
                    obterDadosLogin.idFilial,
                    prox.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")),
                    filtroInicio,
                    filtroFim = null,
                    atividade,
                ).unwrap().first { LocalTime.parse(it.horaInicio) == hora }

                proxObterAgendaDia
                    .horaInicioReserva
                    .split(':')
                    .asReversed()
                    .foldIndexed(Duration.ZERO) { i, acc, d ->
                        acc.plus(Duration.of(d.toLongOrNull() ?: 0, units[i]))
                    }
            } catch (ex: Exception) {
                null
            }
                ?: Duration.ofDays(2)

            WorkManager.getInstance(applicationContext)
                .enqueue(
                    OneTimeWorkRequestBuilder<ReservaWorker>()
                        .apply { tags.forEach(::addTag) }
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setInitialDelay(
                            Duration.between(LocalDateTime.now(), prox - proxHoraInicioReserva)
                        )
                        .setInputData(inputData)
                        .build()
                )

            return Result.success()
        } catch (ex: Exception) {
            showNotification("Erro", ex.message ?: ex.toString())
            return Result.retry()
        }
    }
}