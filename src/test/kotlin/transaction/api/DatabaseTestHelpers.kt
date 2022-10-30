package transaction.api

import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import transaction.api.database.MyDatabase
import transaction.api.model.Account
import transaction.api.model.BankAccounts
import transaction.api.model.BankTransactions
import transaction.api.model.PendingTransactions
import kotlin.random.Random

suspend fun MyDatabase.generateAccounts(
    numberOfAccounts : Int = 10,
    minAvailabeCash : Double = 0.0,
    maxAvaiableCash : Double = 1000.0) : List<Account> {
    var x = 0;
    val accounts = mutableListOf<Account>()
    while (x < numberOfAccounts) {
        accounts.add(
            Account(
                id = x.toLong(),
                name = "TestName $x",
                availableCash = Random.nextDouble(minAvailabeCash, maxAvaiableCash)
            )
        )
        x += 1
    }
    accounts.forEach{this.newAccount(it)}
    return accounts
}

suspend fun MyDatabase.getAccounts(accounts : List<Long>): List<Account> {
    val accountList = mutableListOf<Account>()
    for (account in accounts) {
        accountList.add(this.getAccount(account))
    }
    return accountList.toList()
}

fun dropTables() {
    transaction{
        SchemaUtils.drop(BankAccounts)
        SchemaUtils.drop(BankTransactions)
        SchemaUtils.drop(PendingTransactions)
    }
}

fun generateTables() {
    transaction{
        SchemaUtils.create(BankAccounts)
        SchemaUtils.create(BankTransactions)
        SchemaUtils.create(PendingTransactions)
    }
}