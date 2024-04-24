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

package felixwiemuth.tka.server

import felixwiemuth.tka.api.ApiResponse
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.util.pipeline.*

fun <T, E> okOrErr(v: T?, e: () -> E): ApiResponse<T, E> =
    if (v == null) {
        ApiResponse.RequestErr(e.invoke())
    } else {
        ApiResponse.Ok(v)
    }

fun <T, E> ok(v: T): ApiResponse<T, E> =
    ApiResponse.Ok(v)

fun <E> ok(): ApiResponse<Unit, E> =
    ApiResponse.Ok(Unit)

fun <T, E> err(e: E): ApiResponse<T, E> =
    ApiResponse.RequestErr(e)

suspend inline fun <reified T : Any, reified E : Any> PipelineContext<*, ApplicationCall>.reply(r: ApiResponse<T, E>) =
    when (r) {
        is ApiResponse.Ok -> call.respond(r.result) // this works also with r: ApiResponse<Any, Any>
        is ApiResponse.RequestErr -> call.respond(HttpStatusCode.BadRequest, r.err)
        is ApiResponse.Err -> throw IllegalStateException("Err should not be constructed by server.")
    }

suspend fun PipelineContext<*, ApplicationCall>.replyOk(v: Any) {
    call.respond(v)
}

suspend fun PipelineContext<*, ApplicationCall>.replyOkOrErr(v: Any?, e: () -> Any) {
    v?.also {
        replyOk(it)
    } ?: replyErr(e.invoke())
}

suspend fun PipelineContext<*, ApplicationCall>.replyErr(e: Any) {
    call.respond(HttpStatusCode.BadRequest, e)
}
