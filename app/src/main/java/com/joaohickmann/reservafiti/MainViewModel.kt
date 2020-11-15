package com.joaohickmann.reservafiti

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import androidx.lifecycle.AndroidViewModel
import org.conscrypt.Conscrypt
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.security.Security
import java.util.*

fun <T : Any?> Response<T>.unwrap(): T = if (isSuccessful)
    body() ?: throw Exception("Error: null body")
else
    throw Exception(errorBody()?.string() ?: "Error: null errorBody")

val AndroidViewModel.context: Context get() = getApplication()

class MainViewModel(application: Application) : AndroidViewModel(application) {
    init {
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }

    val retrofit = Retrofit.Builder()
        .baseUrl("https://evomobile.azurewebsites.net")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    val fitiApi: FitiApi = retrofit.create()

    suspend fun ativar(email: String, senha: String, dias: Set<Int>) {
        val buscarUsuario = fitiApi.buscarUsuario(email)
            .unwrap()
        fitiApi.autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
            .unwrap()
            .first()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 18)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        if (calendar.before(Calendar.getInstance()))
            calendar.add(Calendar.DAY_OF_MONTH, 1)

        val intent = Intent(context, AlarmReceiver::class.java)
        intent.putExtra("email", email)
        intent.putExtra("senha", senha)
        intent.putExtra("dias", dias.toIntArray())

        val alarmManager: AlarmManager = context.getSystemService()
            ?: throw Exception("AlarmManager não encontrado")

        alarmManager.setRepeating(
            AlarmManager.RTC_WAKEUP,
            calendar.timeInMillis,
            24 * 60 * 60 * 1000, // 1 Dia
            PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
        )
    }
}