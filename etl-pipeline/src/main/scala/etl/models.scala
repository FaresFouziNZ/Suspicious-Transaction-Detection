package etl

// ===== Core Domain Models =====
case class Transaction(
  txn_id: String,
  user_id: String,
  amount: Double,
  timestamp: String,
  currency: String,
  merchant: String,
)

case class EnrichedTransaction(
  txn_id: String,
  user_id: String,
  amount: Double,
  timestamp: String,
  currency: String,
  merchant: String,
  amount_sar: Double,
  category: String,
  suspicion_score: Int
)

// ===== Hermes Service Models =====
case class EnrichResponse(status: String, data: EnrichData)
case class EnrichData(txn_id: String, amount_sar: Double, category: String)

case class ScoreResponse(status: String, data: ScoreData)
case class ScoreData(txn_id: String, suspicion_score: Int)
