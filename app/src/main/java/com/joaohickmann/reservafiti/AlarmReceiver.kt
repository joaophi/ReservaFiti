package com.joaohickmann.reservafiti

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.runBlocking
import org.conscrypt.Conscrypt
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.security.Security
import java.text.SimpleDateFormat
import java.util.*

class AlarmReceiver : BroadcastReceiver() {
    init {
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }

    val retrofit = Retrofit.Builder()
        .baseUrl("https://evomobile.azurewebsites.net")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val fitiApi: FitiApi = retrofit.create()

    override fun onReceive(context: Context, intent: Intent) = runBlocking {
        try {
            val email = intent.getStringExtra("email")!!
            val senha = intent.getStringExtra("senha")!!
            val dias = intent.getIntArrayExtra("dias")!!

            val dia = Calendar.getInstance()
            dia.add(Calendar.DAY_OF_MONTH, 2)

            if (dia.get(Calendar.DAY_OF_WEEK) !in dias)
                return@runBlocking

            val buscarUsuario = fitiApi.buscarUsuario(email).unwrap()
            val autenticarUsuario = fitiApi.autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
                .unwrap()
                .first()
            val obterDadosLogin = fitiApi.obterDadosLogin(
                idUsuarioToken = buscarUsuario.idUsuarioToken,
                idClienteW12Token = autenticarUsuario.idClienteW12Token,
                idAspeNetUser = buscarUsuario.idAspeNetUser
            ).unwrap()

            val authorization = "Bearer ${obterDadosLogin.token}"
            val data = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(dia.timeInMillis))

            val obterAgendaDia = fitiApi.obterAgendaDia(authorization, data).unwrap().first()
            fitiApi.participarAtividade(authorization, data, obterAgendaDia.idConfiguracao).unwrap()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("a", "a", NotificationManager.IMPORTANCE_DEFAULT)
                val notificationManager: NotificationManager = context.getSystemService()
                    ?: return@runBlocking
                notificationManager.createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(context, "a")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Reservado")
                .setContentText("Para o dia: $data")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(0, builder)
        } catch (ex: Exception) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel("a", "a", NotificationManager.IMPORTANCE_DEFAULT)
                val notificationManager: NotificationManager = context.getSystemService()
                    ?: return@runBlocking
                notificationManager.createNotificationChannel(channel)
            }
            val builder = NotificationCompat.Builder(context, "a")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Erro")
                .setContentText(ex.message)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(0, builder)
        }
    }
}