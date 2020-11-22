package com.joaohickmann.reservafiti

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.work.*
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.time.DayOfWeek
import java.time.LocalTime

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val workManager = WorkManager.getInstance(application)

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://evomobile.azurewebsites.net")
        .addConverterFactory(MoshiConverterFactory.create())
        .build()

    private val fitiApi: FitiApi = retrofit.create()

    suspend fun ativar(email: String, senha: String, dias: Set<DayOfWeek>) {
        val buscarUsuario = fitiApi.buscarUsuario(email)
            .unwrap()
        fitiApi.autenticarUsuario(buscarUsuario.idAspeNetUser, senha)
            .unwrap()
            .first()

        desativar()
        for (dia in dias)
            workManager.enqueue(
                OneTimeWorkRequestBuilder<ReservaWorker>()
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setInputData(
                        Data.Builder()
                            .putString("email", email)
                            .putString("senha", senha)
                            .putInt("dia", dia.value)
                            .putInt("hora", LocalTime.of(18, 0).toSecondOfDay())
                            .build()
                    )
                    .build()
            )
    }

    fun desativar() {
        workManager.cancelAllWork()
    }
}