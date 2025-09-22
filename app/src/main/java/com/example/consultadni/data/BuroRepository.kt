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
import com.example.consultadni.AppConfig

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
            .baseUrl("https://intranet.elcristalperu.com")
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

    suspend fun loginWithRecaptcha(recaptchaToken: String): Result<Unit> {
        return withContext(Dispatchers.IO) {
            try {
                // The user's new code uses different keys for login
                val body = mapOf(
                    "usuario" to AppConfig.DEMO_USUARIO,
                    "contrasenia" to AppConfig.DEMO_PASSWORD,
                    "recaptchaToken" to recaptchaToken
                )
                val resp = NetworkModule.api.login(body)
                if (resp.isSuccessful) {
                    Result.success(Unit)
                } else {
                    val err = resp.errorBody()?.string() ?: "Código ${resp.code()}"
                    Result.failure(Exception("Login fallido: $err"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    suspend fun consultaNumero(numero: String, isDni: Boolean): Result<JsonObject> {
        return withContext(Dispatchers.IO) {
            try {
                // **LOGIN STEP REMOVED** - It's now handled separately in the Activity.

                // 1. Obtener _token desde la página (Assumes user is already logged in)
                val pageResp = NetworkModule.api.getRiesgoPage()
                val html = pageResp.body() ?: ""
                val regex = Regex("<meta name=\"csrf-token\" content=\"([^\"]+)\">")
                val token = regex.find(html)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("No CSRF token. Sesión podría haber expirado."))

                // 2. Construir snapshot dinámico
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

                // 3. Hacer la consulta
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
