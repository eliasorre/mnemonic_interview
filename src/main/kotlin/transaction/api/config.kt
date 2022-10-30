package transaction.api

import io.ktor.server.config.*

data class DatabaseConfig(val applicationConfig: ApplicationConfig) {
    lateinit var url: String
    lateinit var driver: String
    lateinit var user: String
    lateinit var password: String


    init {
        readConfig(applicationConfig)
    }

    private fun readConfig(applicationConfig: ApplicationConfig){
        url = applicationConfig.property("ktor.database.url").getString()
        driver = applicationConfig.property("ktor.database.driver").getString()
        user = applicationConfig.property("ktor.database.user").getString()
        password = applicationConfig.property("ktor.database.password").getString()
    }
}