package com.joaohickmann.reservafiti

import android.os.Bundle
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.joaohickmann.reservafiti.databinding.ActivityMainBinding
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.launch
import org.conscrypt.Conscrypt
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.create
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import java.security.Security

interface FitiApi {
    @JsonClass(generateAdapter = true)
    data class BuscarUsuario(
            val idAspeNetUser: String,
            val idUsuarioToken: String,
    )

    @GET("/api/v1/auth/buscar-usuario?idW12Personalizado=220")
    suspend fun buscarUsuario(@Query("usuario") email: String): Response<BuscarUsuario>

    @JsonClass(generateAdapter = true)
    data class AutenticarUsuario(
            val idClienteW12Token: String
    )

    @GET("/api/v1/auth/autenticar-usuario?idW12Personalizado=220")
    suspend fun autenticarUsuario(
            @Query("idAspeNetUser") idAspeNetUser: String,
            @Query("senha") senha: String
    ): Response<List<AutenticarUsuario>>

    @JsonClass(generateAdapter = true)
    data class ObterDadosLogin(
            val token: String
    )

    @GET("/api/v2/auth/obter-dados-login")
    suspend fun obterDadosLogin(
            @Query("idUsuarioToken") idUsuarioToken: String,
            @Query("idClienteW12Token") idClienteW12Token: String,
            @Query("idAspeNetUser") idAspeNetUser: String
    ): Response<ObterDadosLogin>

    @JsonClass(generateAdapter = true)
    data class ObterAgendaDia(
            val idConfiguracao: Int
    )

    @GET("/api/v1/agenda/obter-agenda-dia?idFilial=24&apenasDisponiveis=false&mobile=true&wod=true&manha=true&tarde=true&noite=true&flAgendadas=false")
    suspend fun obterAgendaDia(
            @Header("Authorization") authorization: String,
            @Query("data") data: String,
            @Query("filtroInicio") filtroInicio: String = "0.18:00",
            @Query("filtroFim") filtroFim: String = "0.18:45",
            @Query("idsAtividades") idsAtividades: String = "916"
    ): Response<List<ObterAgendaDia>>

    @GET("/api/v1/agenda/participar-atividade?idFilial=24&numeroDaVaga=null")
    suspend fun participarAtividade(
            @Header("Authorization") authorization: String,
            @Query("data") data: String,
            @Query("idConfiguracao") idConfiguracao: Int,
    ): Response<Unit>
}

fun <T : Any?> Response<T>.unwrap(): T = if (isSuccessful)
    body() ?: throw Exception("Error: null body")
else
    throw Exception(errorBody()?.string() ?: "Error: null errorBody")

class MainViewModel : ViewModel() {
    init {
        Security.insertProviderAt(Conscrypt.newProvider(), 1);
    }

    val retrofit = Retrofit.Builder()
            .baseUrl("https://evomobile.azurewebsites.net")
            .addConverterFactory(MoshiConverterFactory.create())
            .build()

    val fitiApi: FitiApi = retrofit.create()

    suspend fun ativar(email: String, senha: String) {
        val buscarUsuario = fitiApi.buscarUsuario(email).unwrap()
        val autenticarUsuario = fitiApi.autenticarUsuario(buscarUsuario.idAspeNetUser, senha).unwrap().first()
        val obterDadosLogin = fitiApi.obterDadosLogin(
                idUsuarioToken = buscarUsuario.idUsuarioToken,
                idClienteW12Token = autenticarUsuario.idClienteW12Token,
                idAspeNetUser = buscarUsuario.idAspeNetUser
        ).unwrap()
        val obterAgendaDia = fitiApi.obterAgendaDia(
                authorization = "Bearer ${obterDadosLogin.token}",
                data = "2020-11-16"
        ).unwrap().first()
        fitiApi.participarAtividade(
                authorization = "Bearer ${obterDadosLogin.token}",
                data = "2020-11-16",
                idConfiguracao = obterAgendaDia.idConfiguracao
        ).unwrap()
    }
}

class MainActivity : AppCompatActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnAtivar.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val usuario = viewModel.ativar(
                            email = binding.edtEmail.text?.toString().orEmpty(),
                            senha = binding.edtSenha.text?.toString().orEmpty()
                    )

                    throw Exception(usuario.toString())
                } catch (e: Exception) {
                    Toast.makeText(applicationContext, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}