package transaction.api

import io.ktor.client.call.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.config.*
import io.ktor.server.testing.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.jupiter.api.BeforeEach
import transaction.api.database.MyDatabase
import transaction.api.model.Account
import transaction.api.model.BankTransaction
import transaction.api.model.TransactionRequest
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.test.*

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        val response = client.get("/")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("Hello World!", response.bodyAsText())
    }
    class TransactionTests {
        private val database = MyDatabase(DatabaseConfig(ApplicationConfig("applicaton_test.conf")))

        @Before
        fun `reset`() {
            dropTables()
            generateTables()
        }

        @Test
        fun `generate Accounts`() = testApplication {
            environment {
                config = ApplicationConfig("applicaton_test.conf")
            }
            val accounts = database.generateAccounts()
            val accountList = accounts.map { it.id }
            val databaseAccounts = database.getAccounts(accountList)
            assertEquals(databaseAccounts, accounts)
        }

        @Test
        fun `test single transaction`() = testApplication {
            val account1Cash: Double = 100.0
            val account2Cash: Double = 0.0
            val transactionAmount: Double = 25.0

            environment {
                config = ApplicationConfig("applicaton_test.conf")
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        coerceInputValues = true
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
            }
            val account1 = Account(
                id = 1,
                name = "Test account 1",
                availableCash = account1Cash
            )
            val account2 = Account(
                id = 2,
                name = "Test account 2",
                availableCash = account2Cash
            )
            database.newAccount(account1)
            database.newAccount(account2)
            val response = client.post("/transaction") {
                contentType(ContentType.Application.Json)
                setBody(
                    TransactionRequest(
                        sourceAccountId = 1,
                        destinationAccountId = 2,
                        cashAmount = transactionAmount
                    )
                )
            }
            val jsonResponse = response.body<BankTransaction>()
            val success = (account1Cash - transactionAmount >= 0.0)
            val correctTransaction = BankTransaction(
                destinationAccount = Account(
                    id = 2,
                    availableCash = if (success) account2Cash + transactionAmount else account2Cash,
                    name = "Test account 2"
                ),
                sourceAccount = Account(
                    id = 1,
                    availableCash = if (success) account1Cash - transactionAmount else account1Cash,
                    name = "Test account 1"
                ),
                cashAmount = transactionAmount,
                success = success,
                registeredTime = jsonResponse.registeredTime,
                executedTime = jsonResponse.executedTime
            )
            assertEquals(correctTransaction, jsonResponse)
        }
        @Test
        fun `test many transactions`() = testApplication {
            environment {
                config = ApplicationConfig("applicaton_test.conf")
            }
            val client = createClient {
                install(ContentNegotiation) {
                    json(Json {
                        prettyPrint = true
                        coerceInputValues = true
                        isLenient = true
                        ignoreUnknownKeys = true
                        encodeDefaults = true
                    })
                }
            }
            val numberOfAccounts = 10
            val accounts = database.generateAccounts(numberOfAccounts)
            val accountList = accounts.map { it.id }
            val cashBefore = accounts.sumOf { it.availableCash }
            var successes = mutableListOf<BankTransaction>()
            var fails = mutableListOf<BankTransaction>()
            runBlocking {
                for (i in 0..100) {
                    launch {
                        val sourceAccountId = Random.nextInt(0, numberOfAccounts).toLong()
                        var destinationAccountId = Random.nextInt(0, numberOfAccounts).toLong()
                        if (destinationAccountId == sourceAccountId) {
                            destinationAccountId = if(destinationAccountId < numberOfAccounts - 1) destinationAccountId + 1 else 0
                        }
                        val response = client.post("/transaction") {
                            contentType(ContentType.Application.Json)
                            setBody(
                                TransactionRequest(
                                    sourceAccountId = sourceAccountId,
                                    destinationAccountId = destinationAccountId,
                                    cashAmount = Random.nextDouble(40.3, 200.0)
                                )
                            )
                        }
                        val transaction = response.body<BankTransaction>()
                        if (transaction.success) {
                            successes.add(transaction)
                        } else fails.add(transaction)
                    }
                }
            }
            val databaseAccounts = database.getAccounts(accountList)
            val cashAfter = databaseAccounts.sumOf { it.availableCash }
            println("Succeses: ${successes.count()}")
            println("Fails: ${fails.count()}")
            assertEquals(accounts.map{it.id}, databaseAccounts.map { it.id })
            assertEquals((100* cashBefore).roundToInt().toDouble() / 100, (100* cashAfter).roundToInt().toDouble() / 100)
        }
    }
}

