package com.example.animewatcher.api

import com.example.animewatcher.api.provider.AnimePahe
import com.example.animewatcher.api.provider.PaheTitleResponse
import okhttp3.*
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.CompletableFuture

class HttpHandler(val host: String) {
    val httpClient = OkHttpClient()

    fun <T> execute(requestProcessor: HttpUrl.Builder.() -> HttpUrl.Builder, responseProcessor: String.() -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()

        val url = HttpUrl.Builder()
            .scheme("https")
            .host(AnimePahe.HOST)
            .requestProcessor()
            .build()
        val request = Request.Builder()
            .url(url)
            .build()
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                e.printStackTrace()
                future.completeExceptionally(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use {
                    if (!response.isSuccessful) throw IOException("Unexpected code $response")
                    try {
                        val body = response.body!!.string()
                        val result = body.responseProcessor()
                        future.complete(result)
                    } catch (e : Exception) {
                        future.completeExceptionally(e)
                    }
                }
            }
        })
        return future
    }
}