# Typesafe Ktor API (PoC)

A Kotlin Multiplatform library for enabling type-safe HTTP APIs with code shared between server and clients.

Status: Proof of concept, work-in-progress

## About

Building on top of [Ktor](https://ktor.io)'s Resource classes for [type-safe requests](https://ktor.io/docs/type-safe-request.html) with Ktor client and [type-safe routing](https://ktor.io/docs/type-safe-routing.html) for Ktor server, this library adds the remaining bits to ensure that requests and responses adhere to a commonly defined API, for which the code can be shared between server and different clients.

## Overview

Find the whole example in the modules [tka-example-api](tka-example-api), [tka-example-client](tka-example-client) and [tka-example-server](tka-example-server).

### Defining the API

#### Define serializable classes for request/response parameters
```kotlin
@Serializable
data class Order(
    val orderId: Long,
    val customerId: Long,
    val items: List<OrderItem>,
    val totalAmount: Int
)
```

#### Define requests and routes

The API is defined via a hierarchy of Resource classes (as known from Ktor) plus adding `Get`/`Post` interfaces under each endpoint. These interfaces must extend the library's `GET`/`POST` interfaces, specifying request type, result type, error type and parameter type (only for POST).

```kotlin
@Resource("/orders")
class OrdersApi {

    @Resource("list")
    class ListOrders(val p: OrdersApi = OrdersApi(), val userId: Int?, val categoryId: Int?) {
        // Result type: List<Order>, Error type: Unit
        interface Get : GET<ListOrders, List<Order>, Unit>
    }

    @Resource("new")
    class New(val p: OrdersApi = OrdersApi()) {
        // Parameter type: Order, Result type: Int, Error type: Err
        interface Post : POST<New, Order, Int, Post.Err> {
            sealed class Err {
                class OrderWithIdAlreadyExists(val id: Int) : Err()
                data object NoItems : Err()
            }
        }
    }
}
```

### Implementing the API server-side

#### Defining the logic for each request

Implementing the logic simply consists of implementing the `Get` and `Post` interfaces from the API definition.

```kotlin
object OrdersController {
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
```

#### Registering the routes

First define an extension function on `io.ktor.server.routing.Routing` which adds routes for each Resource class using `io.ktor.server.resources.get` etc. by delegating to the implemented `get` and `post` functions.
There will be an annotation generating this glue in the future.

```kotlin
fun Routing.ordersRoutes() {
    get<OrdersApi.ListOrders> {
        reply(it.get())
    }

    post<OrdersApi.New> {
        reply(it.post(call.receive()))
    }
}

```

Finally, the routes must be registered with the Ktor server:

```kotlin
embeddedServer(Netty, 3000) {
    install(ContentNegotiation) {
        json()
    }
    install(Resources)
    install(CORS) {
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowMethod(HttpMethod.Delete)
        anyHost()
        allowHeader(HttpHeaders.ContentType)
        allowCredentials = true
        allowNonSimpleContentTypes = true
    }
    routing {
        ordersRoutes()
    }
}.start(wait = true)
```


### Using the API from a client

#### Registering APIs at the client

The `@WithApis` generates glue for the API's Resource classes to send requests using the annotated client.

```kotlin
@WithApis(apis = [OrdersApi::class])
val client = HttpClient {
    install(ContentNegotiation) {
        json()
    }
    install(Resources)
    ...
}
```

#### Making requests

Requests can then be made by calling `get` and `post` on the Resource classes.

```kotlin
// HTTP GET: /orders/list?userId=42?categoryId=10
val orders = OrdersApi
    .ListOrders(userId = 42, categoryId = 10)
    .get() // The @WithApis annotation generates this function which sends the request using client
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
```

## Usage

### Setup

#### Adding the library to the project

Because of being in a very early stage, the library is currently not published in any repository and has to be added manually to a project, for example as a git submodule:

`git submodule add https://github.com/felixwiemuth/TypesafeKtorAPI`

Then register the modules in your project's `settings.gradle` (assuming that the library is cloned to a subdirectory `TypesafeKtorAPI` of the project):

```kotlin
include("TypesafeKtorAPI:tka-base")
include("TypesafeKtorAPI:tka-server")
include("TypesafeKtorAPI:tka-client")
include("TypesafeKtorAPI:tka-client-plugin")
```

#### Project structure and dependencies

A project using this library would usually introduce a separate API-module in addition to a server- and several client-modules.

The purpose and dependencies for the different modules are as follows:

| Module | Purpose                                                                           | Dependencies                                                                                                                                                                                                                                        |
|:-------|:----------------------------------------------------------------------------------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| API    | API definition and data classes to be shared<br>between server and different clients | <ul><li>io.ktor:ktor-resources</li><li>tka-base</li></ul>                                                                                                                                                                                           |
| Server | Implementation of the API                                                         | <ul><li>io.ktor:ktor-server-cors</li><li>io.ktor:ktor-server-content-negotiation</li><li>io.ktor:ktor-server-serialization-kotlinx.json</li><li>io.ktor:ktor-server-resources</li><li>tka-base</li><li>tka-server</li><li>your-api-module</li></ul> |
| Client | Usage of the API                                                                  | <ul><li>io.ktor:ktor-client-core</li>io.ktor:ktor-client-content-negotiation</li><li>io.ktor:ktor-client-serialization-kotlinx.json</li><li>io.ktor:ktor-client-resources</li><li>tka-base</li><li>tka-client</li><li>tka-client-plugin (optional)</li><li>your-api-module</li></ul>     |

The TKA-dependencies can be added to the modules via
`implementation("TypesafeKtorAPI:tka-base")` etc.

Note that for Kotlin **Multiplatform** projects, the `tka-client-plugin` dependency has to be added differently, namely via the `add` function in the top-level `dependencies` section. See the [official documentation](https://kotlinlang.org/docs/ksp-multiplatform.html) for further information.
