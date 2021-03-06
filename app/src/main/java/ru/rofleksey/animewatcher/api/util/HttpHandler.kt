package ru.rofleksey.animewatcher.api.util

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.riversun.okhttp3.OkHttp3CookieHelper
import ru.rofleksey.animewatcher.util.AnimeUtils
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException


class HttpHandler {
    companion object {
        private const val TAG = "HttpHandler"
        val instance: HttpHandler by lazy { HOLDER.INSTANCE }
    }

    private object HOLDER {
        val INSTANCE = HttpHandler()
    }

    private val cookieJar = OkHttp3CookieHelper()
    private val httpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar.cookieJar())
        .addInterceptor(Interceptor {
            val originalRequest: Request = it.request()
            val requestWithUserAgent = originalRequest.newBuilder()
                .header("User-Agent", AnimeUtils.USER_AGENT)
                .build()
            it.proceed(requestWithUserAgent)
        })
        .build()

    fun getCookies(url: String): List<Cookie> {
        return cookieJar.cookieJar().loadForRequest(url.toHttpUrl())
    }

    fun getCookiesString(url: String): String {
        return getCookies(url).joinToString("; ") {
            "${it.name}=${it.value}"
        }
    }

    fun saveCookies(url: HttpUrl, cookies: List<Cookie>) {
        Log.v(TAG, "httpClient::saveCookies for $url - $cookies")
        cookies.forEach {
            cookieJar.setCookie(url.toString(), it.name, it.value)
        }
    }

    suspend fun <T> executeDirect(
        urlProcessor: HttpUrl.Builder.() -> HttpUrl.Builder,
        requestProcessor: Request.Builder.() -> Request.Builder,
        responseProcessor: Response.() -> T
    ): T {
        val url = HttpUrl.Builder()
            .urlProcessor()
            .build()

        val request = Request.Builder()
            .requestProcessor()
            .url(url)
            .build()

        val response = withContext(Dispatchers.IO) {
            httpClient.newCall(request).await()
        }

        return response.responseProcessor()
    }
}

fun Response.actualBody(): String {
    return this.body!!.string()
}

@Throws(IOException::class)
suspend fun Call.await(): Response {
    return suspendCancellableCoroutine { continuation ->
        enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(IOException("(${response.code}) ${response.message}"))
                    return
                }
                continuation.resume(response)
            }

            override fun onFailure(call: Call, e: IOException) {
                // Don't bother with resuming the continuation if it is already cancelled.
                if (continuation.isCancelled) return
                continuation.resumeWithException(e)
            }
        })

        continuation.invokeOnCancellation {
            try {
                cancel()
            } catch (ex: Throwable) {
                //Ignore cancel exception
            }
        }
    }
}