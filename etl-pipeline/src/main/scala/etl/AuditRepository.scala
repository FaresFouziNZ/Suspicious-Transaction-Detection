package etl

import zio._
import java.sql.{Connection, Timestamp}
import java.time.Instant

trait AuditRepository {
  def logFileProcessing(audit: FileProcessingAudit): Task[Unit]
  def logProcessingError(error: ProcessingError): Task[Unit]
  def getFileProcessingHistory(fileName: String): Task[List[FileProcessingAudit]]
}

object AuditRepository {
  val live: ZLayer[Any, Nothing, AuditRepository] =
    ZLayer.succeed(new AuditRepository {
      override def logFileProcessing(audit: FileProcessingAudit): Task[Unit] =
        DatabaseClient.getConnection.flatMap { conn =>
          ZIO.attemptBlocking {
            val stmt = conn.prepareStatement(
              """
              INSERT INTO file_processing_audit (
                file_name, total_records, valid_records, invalid_records, 
                invalid_reasons, enriched_records, scored_records, 
                inserted_to_review, processing_start_time, processing_end_time, status
              ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
              """
            )
            stmt.setString(1, audit.fileName)
            stmt.setInt(2, audit.totalRecords)
            stmt.setInt(3, audit.validRecords)
            stmt.setInt(4, audit.invalidRecords)
            stmt.setString(5, audit.invalidReasons.mkString(";"))
            stmt.setInt(6, audit.enrichedRecords)
            stmt.setInt(7, audit.scoredRecords)
            stmt.setInt(8, audit.insertedToReview)
            stmt.setTimestamp(9, Timestamp.from(audit.processingStartTime))
            stmt.setTimestamp(10, Timestamp.from(audit.processingEndTime))
            stmt.setString(11, audit.status.toString)
            stmt.executeUpdate()
          }.ensuring(ZIO.attemptBlocking(conn.close()).orDie).unit
        }

      override def logProcessingError(error: ProcessingError): Task[Unit] =
        DatabaseClient.getConnection.flatMap { conn =>
          ZIO.attemptBlocking {
            val stmt = conn.prepareStatement(
              """
              INSERT INTO processing_errors (file_name, error, timestamp)
              VALUES (?, ?, ?)
              """
            )
            stmt.setString(1, error.fileName)
            stmt.setString(2, error.error)
            stmt.setTimestamp(3, Timestamp.from(error.timestamp))
            stmt.executeUpdate()
          }.ensuring(ZIO.attemptBlocking(conn.close()).orDie).unit
        }

      override def getFileProcessingHistory(fileName: String): Task[List[FileProcessingAudit]] =
        DatabaseClient.getConnection.flatMap { conn =>
          ZIO.attemptBlocking {
            val stmt = conn.prepareStatement(
              """
              SELECT file_name, total_records, valid_records, invalid_records,
                     invalid_reasons, enriched_records, scored_records,
                     inserted_to_review, processing_start_time, processing_end_time, status
              FROM file_processing_audit
              WHERE file_name = ?
              ORDER BY processing_start_time DESC
              """
            )
            stmt.setString(1, fileName)
            val rs = stmt.executeQuery()
            
            var audits = List.empty[FileProcessingAudit]
            while (rs.next()) {
              val audit = FileProcessingAudit(
                fileName = rs.getString("file_name"),
                totalRecords = rs.getInt("total_records"),
                validRecords = rs.getInt("valid_records"),
                invalidRecords = rs.getInt("invalid_records"),
                invalidReasons = Option(rs.getString("invalid_reasons"))
                  .map(_.split(";").toList.filter(_.nonEmpty))
                  .getOrElse(Nil),
                enrichedRecords = rs.getInt("enriched_records"),
                scoredRecords = rs.getInt("scored_records"),
                insertedToReview = rs.getInt("inserted_to_review"),
                processingStartTime = rs.getTimestamp("processing_start_time").toInstant,
                processingEndTime = rs.getTimestamp("processing_end_time").toInstant,
                status = if (rs.getString("status") == "ProcessingSuccess") ProcessingSuccess else ProcessingFailed
              )
              audits = audit :: audits
            }
            audits.reverse
          }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
        }
    })
}


