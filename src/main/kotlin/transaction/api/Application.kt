package transaction.api

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.plugins.cors.routing.*

import kotlinx.serialization.json.Json
import transaction.api.database.MyDatabase
import transaction.api.routes.transactionsRoute

fun main(args: Array<String>): Unit = EngineMain.main(args)

@Suppress("unused")
fun Application.module() {
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            coerceInputValues = true
            isLenient = true
            ignoreUnknownKeys = true
            encodeDefaults = true
        })
    }
    install(CORS) {
        allowMethod(HttpMethod.Options)
        allowHeader(HttpHeaders.ContentType)
        allowHeader(HttpHeaders.Authorization)
        anyHost()
    }
    val config = DatabaseConfig(environment.config)

    val myDatabase = MyDatabase(config)
    install(Routing) {
        transactionsRoute(myDatabase)
    }
}
