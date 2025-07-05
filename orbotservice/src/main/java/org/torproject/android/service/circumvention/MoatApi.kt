package org.torproject.android.service.circumvention

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.net.InetSocketAddress
import java.net.Proxy

interface MoatApi {

    companion object {
        private const val URL = "https://bridges.torproject.org/moat/circumvention/"

        fun getInstance(proxyPort: Int): MoatApi {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort))
            val client = OkHttpClient.Builder().proxy(proxy).build()

            val json = Json {
                ignoreUnknownKeys = true
            }

            return Retrofit.Builder()
                .baseUrl(URL)
                .addConverterFactory(json.asConverterFactory("application/vnd.api+json".toMediaType()))
                .client(client)
                .build()
                .create(MoatApi::class.java)
        }
    }

    @POST("settings")
    suspend fun settings(@Body request: SettingsRequest = SettingsRequest()): SettingsResponse

    @POST("defaults")
    suspend fun defaults(@Body request: SettingsRequest = SettingsRequest()): SettingsResponse

    @GET("map")
    suspend fun map(): Map<String, SettingsResponse>

    @GET("builtin")
    suspend fun builtin(): BuiltInBridges

    @GET("countries")
    suspend fun countries(): List<String>



    @Serializable
    data class SettingsRequest(
        val country: String? = null,
        val transports: List<String> = listOf("obfs4", "snowflake", "webtunnel")
    )

    @Serializable
    data class SettingsResponse(
        val settings: List<Setting>? = null,
        val country: String? = null,
        val errors: List<MoatError>? = null
    )

    @Serializable
    data class Setting(
        @SerialName("bridges")
        val bridge: Bridge
    )

    @Serializable
    data class Bridge(
        val type: String,
        val source: String,
        @SerialName("bridge_strings")
        val bridges: List<String>? = null
    )

    @Serializable
    data class MoatError(
        val id: String? = null,
        val type: String? = null,
        val version: String? = null,
        val code: Int? = null,
        val status: String? = null,
        val detail: String? = null
    ): Throwable(if (detail.isNullOrEmpty()) "$code $status" else detail)
}