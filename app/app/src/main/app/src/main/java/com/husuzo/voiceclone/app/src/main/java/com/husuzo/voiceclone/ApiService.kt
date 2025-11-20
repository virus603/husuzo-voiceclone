package com.husuzo.voiceclone

import okhttp3.MultipartBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ApiService {
    @Multipart
    @POST("upload_sample")
    suspend fun uploadSample(@Part file: MultipartBody.Part): Response<Void>

    @POST("synthesize")
    suspend fun synthesize(@Body payload: Map<String, String>): Response<ResponseBody>
}
