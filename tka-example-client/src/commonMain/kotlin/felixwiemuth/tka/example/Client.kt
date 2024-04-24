/*
 * Copyright (C) 2024 Felix Wiemuth
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package felixwiemuth.tka.example

import felixwiemuth.tka.api.ApiResponse
import felixwiemuth.tka.api.OtherErr
import felixwiemuth.tka.client.annotation.WithApis
import felixwiemuth.tka.example.api.OrdersApi
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.ClientRequestException
import io.ktor.client.plugins.ServerResponseException
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.resources.Resources
import io.ktor.client.plugins.resources.get
import io.ktor.client.plugins.resources.post
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLProtocol
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.errors.IOException

@WithApis(apis = [OrdersApi::class])
val client = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(Logging)
    install(Resources)
    // Debug configuration with local server
    defaultRequest {
        host = "127.0.0.1"
        port = 3000
        url { protocol = URLProtocol.HTTP }
    }
    followRedirects = false
    expectSuccess = true // make client throw exceptions on 3xx, 4xx, 5xx error codes
}

/**
 * This function (and the corresponding [post] function) glues generated ApiClient code
 * together with the [HttpClient]. For each API resource class X, a class XApiClient is
 * generated which implements the get and post methods from the GET and POST interfaces
 * by delegating to these general get/post functions which again call get/post on the
 * client. The types specified as parameters for the GET and POST interfaces are automatically
 * used all the way, so no casting is necessary.
 * The [handledRequest] function maps the different results and errors to [ApiResponse].
 *
 */
suspend inline fun <reified R : Any, reified T, reified E> get(r: R): ApiResponse<T, E> =
    handledRequest { client.get(r) }

/**
 * @see [get]
 */
suspend inline fun <reified R : Any, reified P, reified T, reified E> post(r: R, param: P): ApiResponse<T, E> =
    handledRequest {
        client.post(r) {
            contentType(ContentType.Application.Json)
            setBody(param)
        }
    }

/**
 * Mapping request results and errors to [ApiResponse].
 * As it is not clear yet what is the best way to map the different errors,
 * and what the possible errors are depending on the client, this is currently
 * not part of the library.
 * This mapping was created in the context of a Kotlin/JS browser client.
 */
suspend inline fun <reified T, reified E> handledRequest(
    crossinline requestBlock: suspend () -> HttpResponse,
): ApiResponse<T, E> =
    try {
        ApiResponse.Ok(requestBlock().body())
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.BadRequest) {
            ApiResponse.RequestErr<E>(e.response.body()) // ignoring serialization exception
        } else {
            // TODO Test this
            ApiResponse.Err(OtherErr.HttpError(e.response.status, e.response.body<String>().toString()))
        }
    } catch (e: ServerResponseException) {
        ApiResponse.Err(OtherErr.HttpError(e.response.status, e.response.body<String>().toString()))
    } catch (e: IOException) { // TODO what can be the content of the IOException?
        ApiResponse.Err(OtherErr.NetworkError)
    } catch (e: Error) {
        // If server is not reachable, get e.name="pf", e.message="Fail to fetch"
        if (e.message.equals("Fail to fetch")) {
            ApiResponse.Err(OtherErr.NetworkError)
        } else {
            ApiResponse.Err(OtherErr.BrowserError(e.toString()))
        }
    } catch (cause: Throwable) {
        ApiResponse.Err(OtherErr.BrowserError(cause.toString()))
    } // catch (e: SerializationException) { // TODO can ignore this case / can it happen?
//    }



