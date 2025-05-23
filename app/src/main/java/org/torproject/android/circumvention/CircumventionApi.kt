package org.torproject.android.circumvention

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient

import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

import kotlinx.serialization.json.Json

import java.net.InetSocketAddress
import java.net.Proxy

interface CircumventionEndpoints {

    @GET("countries")
    fun getCountries(): Call<List<String>>

    @GET("builtin")
    fun getBuiltinTransports(): Call<BuiltInBridgesResponse>

    @POST("settings")
    fun getSettings(@Body request: SettingsRequest): Call<SettingsResponse>

    @POST("defaults")
    fun getDefaults(@Body request: SettingsRequest): Call<SettingsResponse>

    @GET("map")
    fun getMap(): Call<Map<String, SettingsResponse>>

}

object ServiceBuilder {

    fun <T> buildService(service: Class<T>, proxyPort: Long): T {
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("127.0.0.1", proxyPort.toInt()))
        val client = OkHttpClient.Builder().proxy(proxy).build()

        val json = Json {
            ignoreUnknownKeys = true
        }

        val contentType = "application/json".toMediaType()
        val retrofit = Retrofit.Builder()
            .baseUrl("https://bridges.torproject.org/moat/circumvention/")
            .addConverterFactory(json.asConverterFactory(contentType))
            .client(client)
            .build()

        return retrofit.create(service)
    }
}

class  CircumventionApiManager (port: Long) {
    companion object {
        const val BRIDGE_TYPE_OBFS4 = "obfs4"
        const val BRIDGE_TYPE_SNOWFLAKE = "snowflake"
    }
    private val retrofit = ServiceBuilder.buildService(CircumventionEndpoints::class.java, port)

    fun getCountries(onResult: (List<String>?) -> Unit, onError: ((Throwable) -> Unit)? = null) {
        retrofit.getCountries().enqueue(object: Callback<List<String>> {
            override fun onResponse(call: Call<List<String>>, response: Response<List<String>>) {
                onResult(response.body())
            }

            override fun onFailure(call: Call<List<String>>, t: Throwable) {
                onError?.let { it(t) }
            }
        })
    }

    fun getBuiltInTransports(onResult: (BuiltInBridgesResponse?) -> Unit, onError: ((Throwable) -> Unit)? = null) {
        retrofit.getBuiltinTransports().enqueue(object : Callback<BuiltInBridgesResponse> {
            override fun onResponse(call: Call<BuiltInBridgesResponse>, response: Response<BuiltInBridgesResponse>) {
                onResult(response.body())
            }

            override fun onFailure(call: Call<BuiltInBridgesResponse>, t: Throwable) {
                onError?.let { it(t) }
            }
        })
    }

    fun getSettings(request: SettingsRequest, onResult: (SettingsResponse?) -> Unit, onError: ((Throwable) -> Unit)? = null) {
        retrofit.getSettings(request).enqueue(object: Callback<SettingsResponse> {
            override fun onResponse(call: Call<SettingsResponse>, response: Response<SettingsResponse>) {
                onResult(response.body())
            }

            override fun onFailure(call: Call<SettingsResponse>, t: Throwable) {
                onError?.let { it(t) }
            }
        })
    }

    fun getDefaults(request: SettingsRequest, onResult: (SettingsResponse?) -> Unit, onError: ((Throwable) -> Unit)? = null) {
        retrofit.getDefaults(request).enqueue(object: Callback<SettingsResponse> {
            override fun onResponse(call: Call<SettingsResponse>, response: Response<SettingsResponse>) {
                onResult(response.body())
            }

            override fun onFailure(call: Call<SettingsResponse>, t: Throwable) {
                onError?.let { it(t) }
            }
        })
    }

    fun getMap(onResult: (Map<String, SettingsResponse>?) -> Unit, onError: ((Throwable) -> Unit)? = null) {
        retrofit.getMap().enqueue(object: Callback<Map<String, SettingsResponse>> {
            override fun onResponse(call: Call<Map<String, SettingsResponse>>, response: Response<Map<String, SettingsResponse>>) {
                onResult(response.body())
            }

            override fun onFailure(call: Call<Map<String, SettingsResponse>>, t: Throwable) {
                onError?.let { it(t) }
            }
        })
    }


}