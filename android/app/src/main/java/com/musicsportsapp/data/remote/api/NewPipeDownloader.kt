package com.musicsportsapp.data.remote.api

import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/**
 * Implementation of NewPipeExtractor's Downloader interface using OkHttp.
 * Handles forwarding required headers and mapping responses.
 */
class NewPipeDownloader(private val client: OkHttpClient) : Downloader() {

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .url(url)
            .method(
                httpMethod,
                if (httpMethod == "POST" || httpMethod == "PUT") {
                    dataToSend?.toRequestBody() ?: ByteArray(0).toRequestBody()
                } else null
            )

        headers?.forEach { (key, values) ->
            values.forEach { value ->
                // Crucial: Forward exact headers from NewPipeExtractor to avoid silent blocking
                requestBuilder.addHeader(key, value)
            }
        }

        val okHttpRequest = requestBuilder.build()
        val okHttpResponse = client.newCall(okHttpRequest).execute()

        val responseHeaders = mutableMapOf<String, List<String>>()
        okHttpResponse.headers.names().forEach { name ->
            responseHeaders[name] = okHttpResponse.headers.values(name)
        }

        val responseBody = okHttpResponse.body?.string() ?: ""

        if (okHttpResponse.code == 429) {
            throw ReCaptchaException("reCaptcha Challenge requested", url)
        }

        return Response(
            okHttpResponse.code,
            okHttpResponse.message,
            responseHeaders,
            responseBody,
            okHttpResponse.request.url.toString()
        )
    }
}
