package com.joaohickmann.reservafiti

import com.squareup.moshi.JsonClass
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

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

    @JsonClass(generateAdapter = true)
    data class AtividadesAcademia(
        val idAtividade: Int,
        val nomeAtividade: String
    ) {
        override fun toString(): String = "$idAtividade - $nomeAtividade"
    }

    @GET("/api/v1/agenda/atividades-academia/24")
    suspend fun atividadesAcademia(
        @Header("Authorization") authorization: String
    ): Response<List<AtividadesAcademia>>
}