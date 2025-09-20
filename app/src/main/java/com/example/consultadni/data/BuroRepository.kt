package com.example.consultadni.data

import com.google.gson.JsonObject
import kotlin.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

/**
 * API de Retrofit
 */
interface BuroApi {
    @POST("/login")
    suspend fun login(@Body body: Map<String, String>): Response<JsonObject>

    @GET("/comercial/riesgo")
    suspend fun getRiesgoPage(): Response<String>

    @POST("/livewire/update")
    suspend fun livewireUpdate(@Body body: Map<String, Any>): Response<JsonObject>
}

/**
 * CookieJar simple para mantener sesión
 */
class InMemoryCookieJar : CookieJar {
    private val cookieStore = HashMap<String, List<Cookie>>()
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookieStore[url.host] = cookies
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        return cookieStore[url.host] ?: emptyList()
    }
}

object NetworkModule {
    private val client = OkHttpClient.Builder()
        .cookieJar(InMemoryCookieJar())
        .build()

    val api: BuroApi by lazy {
        Retrofit.Builder()
            .baseUrl("https://intranet.elcristalperu.com") // ajusta si cambia
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(BuroApi::class.java)
    }
}

/**
 * Repository para manejar la lógica de login + consulta
 */
class BuroRepository {

    suspend fun consultaNumero(numero: String, isDni: Boolean): Result<JsonObject> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. Login (usuario hardcodeado de demo)
                val loginBody = mapOf("id" to "46736604", "password" to "Ale07072022")
                val loginResp = NetworkModule.api.login(loginBody)
                if (!loginResp.isSuccessful) {
                    return@withContext Result.failure(Exception("Login fallido"))
                }

                // 2. Obtener _token desde la página
                val pageResp = NetworkModule.api.getRiesgoPage()
                val html = pageResp.body() ?: ""
                val regex = Regex("<meta name=\"csrf-token\" content=\"([^\"]+)\">")
                val token = regex.find(html)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("No CSRF token"))

                // 3. Construir snapshot dinámico
                val snapshotStr = """{"data":{"tipoPersona":"1","tipoDocumento":"${if (isDni) "1" else "6"}","documento":"$numero"},"memo":{"id":"8OXIHjWHHpci5A1CYPXO","name":"intranet.comercial.riesgo","path":"comercial/riesgo","method":"GET","children":[],"scripts":[],"assets":[],"errors":[],"locale":"es"},"checksum":"b1be354c1fc4421124d47297bec10294d8f15cd5c9ced39c36401b2398e6bd43"}"""

                val component = mapOf(
                    "snapshot" to snapshotStr,
                    "updates" to emptyMap<String, Any>(),
                    "calls" to listOf(
                        mapOf(
                            "path" to "",
                            "method" to "search",
                            "params" to listOf<String>()
                        )
                    )
                )

                val body = mapOf(
                    "_token" to token,
                    "components" to listOf(component)
                )

                // 4. Hacer la consulta
                val resp = NetworkModule.api.livewireUpdate(body)
                if (resp.isSuccessful && resp.body() != null) {
                    Result.success(resp.body()!!)
                } else {
                    Result.failure(Exception("Consulta fallida: ${resp.code()}"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }
}
