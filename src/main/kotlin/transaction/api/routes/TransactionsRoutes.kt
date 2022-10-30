package transaction.api.routes
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement
import transaction.api.database.MyDatabase
import transaction.api.model.Account
import transaction.api.model.BankTransaction
import transaction.api.model.TransactionRequest
import kotlin.random.Random

fun Route.transactionsRoute(database : MyDatabase) {
    static("client") {
        resources("javascript")
    }
    get("/"){
        call.respond("Hello World!")
    }

    post("transaction"){
        val requestedTransaction = call.receive<TransactionRequest>()
        launch(Dispatchers.Default) {
            val registeredTime = System.currentTimeMillis()
            val requiredAccounts = listOf(requestedTransaction.destinationAccountId, requestedTransaction.sourceAccountId)
            val recievedAcounts = database.initTransaction(requiredAccounts)

            val sourceAccount = database.getAccount(requestedTransaction.sourceAccountId)
            val destinationAccount = database.getAccount(requestedTransaction.destinationAccountId)
            val success = if (recievedAcounts.containsAll(requiredAccounts)) {
                if (sourceAccount.availableCash - requestedTransaction.cashAmount >= 0) {
                    database.updateAccount(destinationAccount.copy(availableCash = destinationAccount.availableCash + requestedTransaction.cashAmount))
                    database.updateAccount(sourceAccount.copy(availableCash = sourceAccount.availableCash - requestedTransaction.cashAmount))
                    true
                } else false
            } else false
            database.closeTransaction(recievedAcounts)
            val transaction = BankTransaction(
                registeredTime = registeredTime,
                executedTime = System.currentTimeMillis(),
                success = success,
                sourceAccount = if (success) sourceAccount.copy(availableCash = sourceAccount.availableCash - requestedTransaction.cashAmount) else sourceAccount,
                destinationAccount = if (success) destinationAccount.copy(availableCash = destinationAccount.availableCash + requestedTransaction.cashAmount) else destinationAccount,
                cashAmount = requestedTransaction.cashAmount
            )
            database.insertTransaction(transaction)
            call.respond(if (success) HttpStatusCode.OK else HttpStatusCode.BadRequest, Json.encodeToJsonElement(transaction))
        }
    }
}




