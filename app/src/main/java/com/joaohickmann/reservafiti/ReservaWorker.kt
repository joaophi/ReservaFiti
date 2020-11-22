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

            val podeReserver = agora.isAfter(horario.minusDays(2))
            if (podeReserver) {
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
                val idsAtividades = 916

                val obterAgendaDia = fitiApi.obterAgendaDia(
                    authorization,
                    obterDadosLogin.idFilial,
                    data,
                    filtroInicio,
                    filtroFim = null,
                    idsAtividades,
                ).unwrap().first { LocalTime.parse(it.horaInicio) == hora }

                if (obterAgendaDia.status != 5) { // JÁ RESERVADO
                    fitiApi.participarAtividade(
                        authorization,
                        obterDadosLogin.idFilial,
                        data,
                        obterAgendaDia.idConfiguracao,
                    ).unwrap()

                    showNotification("Reservado", "Para: $horario")
                }
            }

            val prox = if (podeReserver) horario.plusWeeks(1) else horario

            WorkManager.getInstance(applicationContext)
                .enqueue(
                    OneTimeWorkRequestBuilder<ReservaWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setInitialDelay(Duration.between(LocalDateTime.now(), prox.minusDays(2)))
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