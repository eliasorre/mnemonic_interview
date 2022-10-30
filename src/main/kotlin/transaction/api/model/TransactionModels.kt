package transaction.api.model

import kotlinx.serialization.Serializable

@Serializable
data class TransactionRequest(
    val sourceAccountId : Long,
    val destinationAccountId : Long,
    val cashAmount : Double
)