package com.example.testcamerax

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface ApiService {
    @GET("/maps/api/distancematrix/json")
     fun getDistanceMatrix(
        @Query("origins") origins: String,
        @Query("destinations") destinations: String,
        @Query("units") units: String,
        @Query("mode") mode: String,
        @Query("key") apiKey: String
    ): Call<DistanceMatrixResponse>


}