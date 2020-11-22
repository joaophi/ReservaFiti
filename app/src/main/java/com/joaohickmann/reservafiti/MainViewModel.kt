package com.joaohickmann.reservafiti

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.work.*
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import java.time.DayOfWeek

fun <T : Any?> Response<T>.unwrap(): T = if (isSuccessful)
    body() ?: throw Exception("Error: null body")
else
    throw Exception(errorBody()?.string() ?: "Error: null errorBody")

val AndroidViewModel.context: Context get() = getApplication()

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

        cancelar()
        for (dia in dias) {
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
                            .build()
                    )
                    .build()
            )
        }
    }

    fun cancelar() {
        workManager.cancelAllWork()
    }
}