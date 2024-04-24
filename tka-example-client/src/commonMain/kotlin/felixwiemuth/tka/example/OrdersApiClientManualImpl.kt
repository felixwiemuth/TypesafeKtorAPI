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
import felixwiemuth.tka.example.api.Order
import felixwiemuth.tka.example.api.OrdersApi

/**
 * This is an example of the code which is generated
 */
object OrdersApiClientManualImpl {
    object ListOrders : OrdersApi.ListOrders.Get {
        override suspend fun OrdersApi.ListOrders.get(): ApiResponse<List<Order>, Unit> = get(this)
    }

    public object New : OrdersApi.New.Post {
        override suspend fun OrdersApi.New.post(`param`: Order):
                ApiResponse<Long, OrdersApi.New.Post.Err> = post(this, param)
    }
}
