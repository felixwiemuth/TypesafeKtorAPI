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

/**
 * General interface for "get" API functions. It is extended in the respective API files to specify APIs.
 * It is implemented in the server to implement the functionality.
 * @param R Resource class defining the API route
 * @param T Return type for the API call
 * @param E Error type for the API call
 */
interface GET<R, T, E> {
    suspend fun R.get(): ApiResponse<T, E>
}

/**
 * General interface for "post" API functions. It is extended in the respective API files to specify APIs.
 * It is implemented in the server to implement the functionality.
 * @param R Resource class defining the API route
 * @param P Parameter type for the request's body
 * @param T Return type for the API call
 * @param E Error type for the API call
 */
interface POST<R, P, T, E> {
    suspend fun R.post(param: P): ApiResponse<T, E>
}