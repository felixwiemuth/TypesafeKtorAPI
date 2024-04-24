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
import felixwiemuth.tka.example.OrdersApiClient.ListOrders.get
import felixwiemuth.tka.example.OrdersApiClient.New.post
import felixwiemuth.tka.example.api.Order
import felixwiemuth.tka.example.api.OrdersApi
import kotlinx.coroutines.runBlocking

class Main {
    fun main(args: Array<String>) {
        runBlocking {
            // HTTP GET: /orders/list?userId=42?categoryId=10
            val orders = OrdersApi
                .ListOrders(customerId = 42, categoryId = 10)
                .get()
                .result() // Get the result or throw an exception if an error is returned
            orders.forEach { println("Order ${it.orderId} from customer ${it.customerId}") }

            // HTTP POST: /orders/new
            when (val r = OrdersApi
                .New()
                .post(Order(orderId = 573, customerId = 123, items = emptyList(), totalAmount = 0))
            ) {
                is ApiResponse.Ok -> println("Order created with id ${r.result}")
                is ApiResponse.RequestErr -> when (val e = r.err) {
                    OrdersApi.New.Post.Err.NoItems -> println("Could not create order: the order contains no items")
                    // Note that we get type-safe access to the error message's parameters!
                    is OrdersApi.New.Post.Err.OrderWithIdAlreadyExists -> println("An order with id ${e.id} already exists")
                }

                is ApiResponse.Err -> println("Error sending or receiving")
            }
        }
    }
}