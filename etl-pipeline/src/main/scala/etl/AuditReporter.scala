package etl

import zio._
import java.time.{Instant, LocalDateTime, ZoneId}

trait AuditReporter {
  def getProcessingSummary(startDate: Option[LocalDateTime], endDate: Option[LocalDateTime]): Task[ProcessingSummary]
  def getFileProcessingHistory(fileName: String): Task[List[FileProcessingAudit]]
  def getFailedFiles(startDate: Option[LocalDateTime], endDate: Option[LocalDateTime]): Task[List[ProcessingError]]
}

case class ProcessingSummary(
  totalFiles: Int,
  successfulFiles: Int,
  failedFiles: Int,
  totalRecords: Int,
  validRecords: Int,
  invalidRecords: Int,
  enrichedRecords: Int,
  insertedToReview: Int,
  averageProcessingTimeMs: Long
)

object AuditReporter {
  val live: ZLayer[AuditRepository, Nothing, AuditReporter] =
    ZLayer.fromZIO {
      for {
        auditRepo <- ZIO.service[AuditRepository]
      } yield new AuditReporter {
        override def getProcessingSummary(startDate: Option[LocalDateTime], endDate: Option[LocalDateTime]): Task[ProcessingSummary] =
          DatabaseClient.getConnection.flatMap { conn =>
            ZIO.attemptBlocking {
              val whereClause = buildDateFilter(startDate, endDate)
              val sql = s"""
                SELECT 
                  COUNT(*) as total_files,
                  COUNT(CASE WHEN status = 'ProcessingSuccess' THEN 1 END) as successful_files,
                  COUNT(CASE WHEN status = 'ProcessingFailed' THEN 1 END) as failed_files,
                  SUM(total_records) as total_records,
                  SUM(valid_records) as valid_records,
                  SUM(invalid_records) as invalid_records,
                  SUM(enriched_records) as enriched_records,
                  SUM(inserted_to_review) as inserted_to_review,
                  AVG(EXTRACT(EPOCH FROM (processing_end_time - processing_start_time)) * 1000) as avg_processing_time_ms
                FROM file_processing_audit
                $whereClause
              """
              
              val stmt = conn.prepareStatement(sql)
              setDateParameters(stmt, startDate, endDate, 1)
              val rs = stmt.executeQuery()
              
              if (rs.next()) {
                ProcessingSummary(
                  totalFiles = rs.getInt("total_files"),
                  successfulFiles = rs.getInt("successful_files"),
                  failedFiles = rs.getInt("failed_files"),
                  totalRecords = Option(rs.getInt("total_records")).getOrElse(0),
                  validRecords = Option(rs.getInt("valid_records")).getOrElse(0),
                  invalidRecords = Option(rs.getInt("invalid_records")).getOrElse(0),
                  enrichedRecords = Option(rs.getInt("enriched_records")).getOrElse(0),
                  insertedToReview = Option(rs.getInt("inserted_to_review")).getOrElse(0),
                  averageProcessingTimeMs = Option(rs.getLong("avg_processing_time_ms")).getOrElse(0L)
                )
              } else {
                ProcessingSummary(0, 0, 0, 0, 0, 0, 0, 0, 0L)
              }
            }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
          }

        override def getFileProcessingHistory(fileName: String): Task[List[FileProcessingAudit]] =
          auditRepo.getFileProcessingHistory(fileName)

        override def getFailedFiles(startDate: Option[LocalDateTime], endDate: Option[LocalDateTime]): Task[List[ProcessingError]] =
          DatabaseClient.getConnection.flatMap { conn =>
            ZIO.attemptBlocking {
              val whereClause = buildDateFilter(startDate, endDate)
              val sql = s"""
                SELECT file_name, error, timestamp
                FROM processing_errors
                $whereClause
                ORDER BY timestamp DESC
              """
              
              val stmt = conn.prepareStatement(sql)
              setDateParameters(stmt, startDate, endDate, 1)
              val rs = stmt.executeQuery()
              
              var errors = List.empty[ProcessingError]
              while (rs.next()) {
                val error = ProcessingError(
                  fileName = rs.getString("file_name"),
                  error = rs.getString("error"),
                  timestamp = rs.getTimestamp("timestamp").toInstant
                )
                errors = error :: errors
              }
              errors.reverse
            }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
          }

        private def buildDateFilter(startDate: Option[LocalDateTime], endDate: Option[LocalDateTime]): String = {
          (startDate, endDate) match {
            case (Some(start), Some(end)) => 
              "WHERE created_at >= ? AND created_at <= ?"
            case (Some(start), None) => 
              "WHERE created_at >= ?"
            case (None, Some(end)) => 
              "WHERE created_at <= ?"
            case (None, None) => 
              ""
          }
        }

        private def setDateParameters(stmt: java.sql.PreparedStatement, startDate: Option[LocalDateTime], endDate: Option[LocalDateTime], startIndex: Int): Unit = {
          var paramIndex = startIndex
          startDate.foreach { date =>
            stmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(date))
            paramIndex += 1
          }
          endDate.foreach { date =>
            stmt.setTimestamp(paramIndex, java.sql.Timestamp.valueOf(date))
            paramIndex += 1
          }
        }
      }
    }
}


