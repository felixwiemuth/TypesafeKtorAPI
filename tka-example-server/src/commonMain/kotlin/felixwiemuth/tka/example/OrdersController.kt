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
import felixwiemuth.tka.example.OrdersController.ListOrders.get
import felixwiemuth.tka.example.OrdersController.New.post
import felixwiemuth.tka.example.api.Order
import felixwiemuth.tka.example.api.OrdersApi
import felixwiemuth.tka.server.err
import felixwiemuth.tka.server.ok
import felixwiemuth.tka.server.reply
import io.ktor.server.application.call
import io.ktor.server.request.receive
import io.ktor.server.resources.get
import io.ktor.server.resources.post
import io.ktor.server.routing.Routing

object OrdersController {
    // Declaring routes. Note the imports, they are not necessarily suggested automatically.
    fun Routing.ordersRoutes() {
        get<OrdersApi.ListOrders> {
            reply(it.get())
        }

        post<OrdersApi.New> {
            it.post(call.receive())
        }
    }

    object ListOrders : OrdersApi.ListOrders.Get {
        override suspend fun OrdersApi.ListOrders.get(): ApiResponse<List<Order>, Unit> =
        // We have direct access to the orders parameters
            // "ok" is a shortcut for replying with ApiResponse.Ok
            ok(OrdersRepository.orders.filter { it.customerId == customerId && it.items.any { it.categoryId == categoryId } })
    }

    object New : OrdersApi.New.Post {
        override suspend fun OrdersApi.New.post(param: Order): ApiResponse<Long, OrdersApi.New.Post.Err> =
            when {
                OrdersRepository.orders.any { it.orderId == param.orderId } ->
                    err(OrdersApi.New.Post.Err.OrderWithIdAlreadyExists(param.orderId))

                param.items.isEmpty() ->
                    err(OrdersApi.New.Post.Err.NoItems)

                else -> ok(param.orderId)
            }
    }
}