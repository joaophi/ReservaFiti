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
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
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
        NotificationManagerCompat.from(applicationContext).notify(0, builder)
    }

    override suspend fun doWork(): Result {
        try {
            val email = inputData.getString("email")
                ?: return Result.failure()

            val senha = inputData.getString("senha")
                ?: return Result.failure()

            val dia = inputData.getInt("dia", 0)
            if (dia == 0) return Result.failure()

            val agora = LocalDateTime.now()
            val dateTime = generateSequence(LocalDate.now().atTime(18, 0)) { it.plusDays(1) }
                .filter { it.isAfter(agora) }
                .filter { it.dayOfWeek.value == dia }
                .first()

            val proxima =
                if (agora.isAfter(dateTime.minusDays(2))) {
                    val buscarUsuario = fitiApi.buscarUsuario(email).unwrap()
                    val autenticarUsuario = fitiApi
                        .autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
                        .unwrap()
                        .first()
                    val obterDadosLogin = fitiApi.obterDadosLogin(
                        buscarUsuario.idUsuarioToken,
                        autenticarUsuario.idClienteW12Token,
                        buscarUsuario.idAspeNetUser
                    ).unwrap()

                    val authorization = "Bearer ${obterDadosLogin.token}"
                    val data = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))

                    val obterAgendaDia =
                        fitiApi.obterAgendaDia(authorization, data).unwrap().first()
                    fitiApi.participarAtividade(authorization, data, obterAgendaDia.idConfiguracao)
                        .unwrap()

                    showNotification("Reservado", "Para o dia: $data")

                    dateTime.plusWeeks(1)
                } else {
                    dateTime
                }.minusDays(2)


            WorkManager.getInstance(applicationContext)
                .enqueue(
                    OneTimeWorkRequestBuilder<ReservaWorker>()
                        .setConstraints(
                            Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                        )
                        .setInitialDelay(Duration.between(LocalDateTime.now(), proxima))
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