@file:Suppress("unused")

package org.torproject.android.service.circumvention

import IPtProxy.IPtProxy
import android.content.Context
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
import java.security.KeyStore
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager


interface MoatApi {

    companion object {

        val json = Json {
            ignoreUnknownKeys = true
        }

        fun getInstance(context: Context, tunnel: MoatTunnel): MoatApi {
            val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", tunnel.port))

            val filename = "ISRG Root X1.cer"
            var trustManager: X509TrustManager? = null
            var socketFactory: SSLSocketFactory? = null

            try {
                val cert: Certificate?

                context.assets.open(filename).use {
                    cert = CertificateFactory.getInstance("X.509")?.generateCertificate(it)
                }

                val keystore = KeyStore.getInstance(KeyStore.getDefaultType())
                keystore?.load(null, null)
                keystore?.setCertificateEntry(filename, cert)

                val tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
                tmf?.init(keystore)
                trustManager = tmf?.trustManagers?.firstOrNull() as? X509TrustManager

                val sslContext = SSLContext.getInstance("TLS")
                sslContext?.init(null, arrayOf(trustManager), null)

                socketFactory = sslContext?.socketFactory
            } catch (_: Throwable) {
                // Ignored. If anything goes wrong with reading the certificate,
                // creating the keystore or the trust manager, we just try to use
                // Android's default keystore and hope for the best. (Which is really ok
                // on later Android SDKs)
            }

            val clientBuilder = OkHttpClient.Builder().proxy(proxy)

            // Fix for API 24, 25: Moat uses Let's Encrypt for TLS certs, but old Androids
            // don't have the ISRG Root X1 certificate in their keystore, which Let's Encrypt
            // uses.
            if (socketFactory != null && trustManager != null) {
                clientBuilder.sslSocketFactory(socketFactory, trustManager)
            }

            return Retrofit.Builder()
                .baseUrl(tunnel.baseUrl)
                .addConverterFactory(json.asConverterFactory("application/vnd.api+json".toMediaType()))
                .client(clientBuilder.build())
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
        val transports: List<String> = listOf(
            IPtProxy.Obfs4,
            IPtProxy.Snowflake,
            IPtProxy.Webtunnel,
            IPtProxy.Dnstt
        )
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
    ) {
        companion object {
            const val SOURCE_BUILTIN = "builtin"
        }
    }

    @Serializable
    data class MoatError(
        val id: String? = null,
        val type: String? = null,
        val version: String? = null,
        val code: Int? = null,
        val status: String? = null,
        val detail: String? = null
    ) : Throwable(if (detail.isNullOrEmpty()) "$code $status" else detail)
}