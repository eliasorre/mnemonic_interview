package transaction.api.model;

import com.sun.jdi.InvalidTypeException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import org.jetbrains.exposed.dao.IntEntity
import org.jetbrains.exposed.dao.IntEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.IntIdTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.api.PreparedStatementApi
import org.postgresql.util.PGobject
import transaction.api.model.BankAccounts.autoIncrement
import transaction.api.model.BankAccounts.uniqueIndex

object BankAccounts: IntIdTable() {
    val account_id : Column<Long> = long("account_id").autoIncrement().uniqueIndex()
    val name: Column<String> = varchar("name", 50)
    val availableCash : Column<Double> = double("availableCash")
}
class BankAccountInstance(entryId: EntityID<Int>) : IntEntity(entryId) {
    companion object : IntEntityClass<BankAccountInstance>(BankAccounts)
    var account_id by BankAccounts.account_id
    var name by BankAccounts.name
    var availableCash by BankAccounts.availableCash
}

object BankTransactions: Table() {
    val transaction_id : Column<Long> = long("transaction_id").autoIncrement().uniqueIndex()
    val registeredTime: Column<Long> = long("registeredTime")
    val executedTime: Column<Long> = long("executedTime")
    val cashAmount : Column<Double> = double("cashAmount")
    val sourceAccount = jsonb("sourceAccount", JsonElement::class.java)
    val destinationAccount = jsonb("destinationAccount", JsonElement::class.java)
    val success : Column<Boolean> = bool("success")
}

object PendingTransactions: IntIdTable() {
    val account_id : Column<Long> = long("account_id")
}

class PendingTransactionstInstance(entryId: EntityID<Int>) : IntEntity(entryId) {
    companion object : IntEntityClass<PendingTransactionstInstance>(PendingTransactions)
    var account_id by PendingTransactions.account_id
}


@Serializable
data class BankTransaction(
    val registeredTime : Long,
    val executedTime : Long,
    val success : Boolean,
    val sourceAccount : Account,
    val destinationAccount : Account,
    val cashAmount : Double
)



@Serializable
data class Account(
    val id : Long,
    val name : String,
    val availableCash : Double
)


fun <T : Any> Table.jsonb(name: String, JsonClass: Class<T>): Column<T>
        = registerColumn(name, JsonColumn(JsonClass))


private class JsonColumn<out T : Any>(private val JsonClass: Class<T>) : ColumnType() {
    override fun sqlType() = "jsonb"

    override fun setParameter(stmt: PreparedStatementApi, index: Int, value: Any?) {
        val obj = PGobject()
        obj.type = "jsonb"
        obj.value = value as String
        stmt[index] = obj
    }

    override fun valueFromDB(value: Any): Any {
        value as PGobject
        return try {
            Json.decodeFromString<JsonElement>(value.value!!)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("Can't parse JSON: $value")
        }
    }


    override fun notNullValueToDB(value: Any): Any = when(value) {
        is JsonElement -> Json.encodeToString(value)
        else -> throw InvalidTypeException()
    }
    override fun nonNullValueToString(value: Any): String = when(value) {
        is JsonElement ->  "$value"
        else -> throw InvalidTypeException()
    }

}

