package etl

import zio._
import java.time.Instant

trait FileProcessor {
  def processFile(fileName: String, bucket: String): Task[FileProcessingAudit]
}

object FileProcessor {
  val live: ZLayer[MinioService & HermesService & SuspiciousTransactionRepository & AuditRepository, Nothing, FileProcessor] =
    ZLayer.fromZIO {
      for {
        minio <- ZIO.service[MinioService]
        hermes <- ZIO.service[HermesService]
        repo <- ZIO.service[SuspiciousTransactionRepository]
        auditRepo <- ZIO.service[AuditRepository]
      } yield new FileProcessor {
        override def processFile(fileName: String, bucket: String): Task[FileProcessingAudit] = {
          val startTime = Instant.now()
          
          for {
            // Step 1: Read and validate file
            fileResult <- minio.processFile(fileName, bucket)
            
            // Step 2: Process transactions based on validation result
            audit <- if (fileResult.isValid && fileResult.transactions.nonEmpty) {
              processValidFile(fileName, fileResult.transactions, startTime)
            } else {
              // Log invalid file
              val audit = FileProcessingAudit(
                fileName = fileName,
                totalRecords = 0,
                validRecords = 0,
                invalidRecords = 0,
                invalidReasons = fileResult.errorMessage.toList,
                enrichedRecords = 0,
                scoredRecords = 0,
                insertedToReview = 0,
                processingStartTime = startTime,
                processingEndTime = Instant.now(),
                status = ProcessingFailed
              )
              auditRepo.logFileProcessing(audit) *> ZIO.succeed(audit)
            }
          } yield audit
        }
        
        private def processValidFile(fileName: String, transactions: List[Transaction], startTime: Instant): Task[FileProcessingAudit] = {
          for {
            // Step 1: Validate individual transactions
            validationResults <- validateTransactions(transactions)
            validTransactions = validationResults.filter(_.isValid).map(_.transaction)
            invalidTransactions = validationResults.filter(!_.isValid)
            
            // Step 2: Enrich valid transactions
            enrichedTransactions <- if (validTransactions.nonEmpty) {
              hermes.enrichBatch(validTransactions)
            } else {
              ZIO.succeed(Nil)
            }
            
            // Step 3: Score enriched transactions
            scoredTransactions = enrichedTransactions // Already scored during enrichment
            
            // Step 4: Insert suspicious transactions to review
            insertedCount <- if (scoredTransactions.nonEmpty) {
              ZIO.foreach(scoredTransactions)(txn => repo.upsert(txn, 60))
                .map(_.count(_ => true))
            } else {
              ZIO.succeed(0)
            }
            
            // Step 5: Create audit log
            endTime = Instant.now()
            audit = FileProcessingAudit(
              fileName = fileName,
              totalRecords = transactions.size,
              validRecords = validTransactions.size,
              invalidRecords = invalidTransactions.size,
              invalidReasons = invalidTransactions.flatMap(_.reasons),
              enrichedRecords = enrichedTransactions.size,
              scoredRecords = scoredTransactions.size,
              insertedToReview = insertedCount,
              processingStartTime = startTime,
              processingEndTime = endTime,
              status = ProcessingSuccess
            )
            
            // Step 6: Log audit
            _ <- auditRepo.logFileProcessing(audit)
          } yield audit
        }
        
        private def validateTransactions(transactions: List[Transaction]): Task[List[TransactionValidationResult]] = {
          ZIO.foreach(transactions) { txn =>
            val reasons = validateTransaction(txn)
            val isValid = reasons.isEmpty
            ZIO.succeed(TransactionValidationResult(txn, isValid, reasons))
          }
        }
        
        private def validateTransaction(txn: Transaction): List[String] = {
          var reasons = List.empty[String]
          
          if (txn.txn_id.isEmpty) reasons = "Empty transaction ID" :: reasons
          if (txn.user_id.isEmpty) reasons = "Empty user ID" :: reasons
          if (txn.amount <= 0) reasons = "Invalid amount" :: reasons
          if (txn.timestamp.isEmpty) reasons = "Empty timestamp" :: reasons
          if (txn.merchant.isEmpty) reasons = "Empty merchant" :: reasons
          
          reasons
        }
      }
    }
}

case class TransactionValidationResult(
  transaction: Transaction,
  isValid: Boolean,
  reasons: List[String]
)


