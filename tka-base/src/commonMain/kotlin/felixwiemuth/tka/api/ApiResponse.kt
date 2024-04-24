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

package felixwiemuth.tka.api

import io.ktor.http.*

sealed class ApiResponse<out T, out E> {
    /**
     * Result received with status code 200.
     */
    data class Ok<out T>(val result: T) : ApiResponse<T, Nothing>()

    /**
     * An error indicating a failed request, where the server explicitly responds with
     * an error object (e.g. invalid data, requested object not available etc.).
     */
    data class RequestErr<out E>(val err: E) : ApiResponse<Nothing, E>()

    /**
     * Other errors which can happen for a request.
     */
    data class Err(val err: OtherErr) : ApiResponse<Nothing, Nothing>()

    /**
     * Get the result from the request if it is [Ok]
     * @throws RuntimeException if this rseponse is a [RequestErr] or an [Err]
     */
    fun result() : T {
        when(this) {
            is Ok -> return result
            is RequestErr -> throw RuntimeException("Request was answered with error: $err")
            is Err -> throw RuntimeException("Network error: $err")
        }
    }
}
// TODO How exactly this should be structured has to be investigated, or it should be left to the user
sealed class OtherErr {
    object NetworkError : OtherErr() { // This is a class because we want a proper/custom toString()
        override fun toString(): String {
            return "NetworkError"
        }
    }

    data class HttpError(val status: HttpStatusCode, val msg: String) : OtherErr()
    data class BrowserError(val msg: String) : OtherErr()
}