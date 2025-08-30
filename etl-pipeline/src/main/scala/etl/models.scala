package etl

import java.time.Instant


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

case class SuspiciousTransaction(
  txnId: String,
  userId: String,
  amountSar: BigDecimal,
  merchant: String,
  category: String,
  timestampLocal: Instant,
  suspicionScore: Double,
  checked: Boolean,
  checkedBy: Option[String] = None,
  checkedAt: Option[java.time.LocalDateTime] = None,
  checkNotes: Option[String] = None
)

case class ObjectStat(etag: String)

// ===== Audit Logging Models =====
case class FileProcessingAudit(
  fileName: String,
  totalRecords: Int,
  validRecords: Int,
  invalidRecords: Int,
  invalidReasons: List[String],
  enrichedRecords: Int,
  scoredRecords: Int,
  insertedToReview: Int,
  processingStartTime: Instant,
  processingEndTime: Instant,
  status: ProcessingStatus
)

sealed trait ProcessingStatus
case object ProcessingSuccess extends ProcessingStatus
case object ProcessingFailed extends ProcessingStatus

case class ProcessingError(
  fileName: String,
  error: String,
  timestamp: Instant
)

// ===== Hermes Service Models =====
case class EnrichResponse(status: String, data: EnrichData)
case class EnrichData(txn_id: String, amount_sar: Double, category: String)

case class ScoreResponse(status: String, data: ScoreData)
case class ScoreData(txn_id: String, suspicion_score: Int)
