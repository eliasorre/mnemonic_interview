package transaction.api.database
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.transactions.transaction
import transaction.api.DatabaseConfig
import transaction.api.model.*
import transaction.api.model.BankAccounts.account_id

class MyDatabase(config: DatabaseConfig) {
    private val mutex = Mutex()

    init {
        Database.connect(config.url, config.driver, config.user, config.password)
        transaction{
            SchemaUtils.create(BankAccounts)
            SchemaUtils.create(BankTransactions)
            SchemaUtils.create(PendingTransactions)
        }
    }

    suspend fun <T> dbQuery(block: suspend () -> T): T =
        newSuspendedTransaction(Dispatchers.IO) { block() }

    suspend fun insertTransaction(bankTransaction: BankTransaction) {
        dbQuery {
            BankTransactions.insert {
                it[registeredTime] = bankTransaction.registeredTime
                it[executedTime] = bankTransaction.executedTime
                it[cashAmount] = bankTransaction.cashAmount
                it[sourceAccount] = Json.encodeToJsonElement(bankTransaction.sourceAccount)
                it[destinationAccount] = Json.encodeToJsonElement(bankTransaction.destinationAccount)
                it[success] = bankTransaction.success
            }
        }
    }

    suspend fun getAccount(accountId: Long): Account {
        return dbQuery {
            BankAccountInstance.find { BankAccounts.account_id eq accountId }
                .map {
                    Account(
                        id = it.account_id,
                        name = it.name,
                        availableCash = it.availableCash
                    )
                }
                .get(0)
        }
    }

    suspend fun updateAccount(account: Account) {
        dbQuery {
            BankAccounts.update({ account_id eq account.id }) {
                it[account_id] = account.id
                it[availableCash] = account.availableCash
                it[name] = account.name
            }
        }
    }

    suspend fun newAccount(account: Account) {
        dbQuery {
            BankAccounts.insert {
                it[account_id] = account.id
                it[availableCash] = account.availableCash
                it[name] = account.name
            }
        }
    }

    suspend fun initTransaction(requiredAccounts: List<Long>, timeOutMilli : Long = 5000) : List<Long>{
        val receivedAccounts = mutableListOf<Long>()
        try {
            withTimeout(timeOutMilli) {
                    var isAvailable = false
                    while (!isAvailable) {
                        mutex.withLock {
                            transaction {
                                isAvailable = true
                                requiredAccounts.forEach {neededAccount ->
                                    if (!PendingTransactionstInstance.find { PendingTransactions.account_id eq neededAccount }.empty()) {
                                        isAvailable = false
                                    }
                                }
                                if (isAvailable){
                                    requiredAccounts.forEach {neededAccount -> PendingTransactions.insert {
                                            it[account_id] = neededAccount
                                        }
                                    }
                                }
                        }}
                    if (!isAvailable) {
                        delay(100)
                    }
                }
                receivedAccounts.addAll(requiredAccounts)
            }
        } catch(e : Exception) { }
        return receivedAccounts
    }


    suspend fun closeTransaction(requiredAccounts: List<Long>) {
        transaction {
            requiredAccounts.forEach {used_account ->
                PendingTransactions.deleteWhere { PendingTransactions.account_id eq used_account}
            }
        }
    }
}