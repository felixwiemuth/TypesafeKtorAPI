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

package felixwiemuth.tka.example.api

import felixwiemuth.tka.api.GET
import felixwiemuth.tka.api.POST
import io.ktor.resources.*

@Resource("/orders")
class OrdersApi {

    @Resource("list")
    class ListOrders(val p: OrdersApi = OrdersApi(), val customerId: Long?, val categoryId: Long?) {
        interface Get : GET<ListOrders, List<Order>, Unit>
    }

    @Resource("new")
    class New(val p: OrdersApi = OrdersApi()) {
        interface Post : POST<New, Order, Long, Post.Err> {
            sealed class Err {
                class OrderWithIdAlreadyExists(val id: Long) : Err()
                data object NoItems : Err()
            }
        }
    }

    @Resource("{id}")
    class Id(val p: OrdersApi = OrdersApi(), val id: Long) {

        // Shared error object (ignoring some possible other errors in the different requests)
        object OrderNotExists // Simple way for a descriptive error with just one possible error

        interface Get : GET<Id, Order, OrderNotExists>

        // Query only part of the order object
        @Resource("get-amount")
        class GetAmount(val p: Id) {
            interface Get : GET<GetAmount, Int, OrderNotExists>
        }

        @Resource("delete")
        class Delete(val p: Id) {
            interface Post : POST<Delete, Unit, Unit, OrderNotExists>
        }

        // Nested under the {id} route
        // Update single values
        @Resource("update")
        class Update(val p: Id) {
            @Resource("customer")
            class Customer(val p: Update, val newCustomerId: Long) {
                interface Post : POST<Customer, Long, Unit, OrderNotExists>
            }

            @Resource("add-item")
            class AddItem(val p: Update, val item: OrderItem) {
                interface Post : POST<AddItem, OrderItem, Unit, OrderNotExists>
            }
        }
    }
}