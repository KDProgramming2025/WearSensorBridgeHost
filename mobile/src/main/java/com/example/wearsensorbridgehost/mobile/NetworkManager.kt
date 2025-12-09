package com.example.wearsensorbridgehost.mobile

import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST

data class SensorData(val encryptedValue: String)

interface ApiService {
    @POST("sensor/data")
    fun sendData(@Body data: SensorData): Call<Void>
}

object NetworkManager {
    private const val BASE_URL = "https://example.com/api/"

    private val retrofit = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val api: ApiService = retrofit.create(ApiService::class.java)
}
