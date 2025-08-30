package etl

import zio._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  val program: ZIO[AppConfig & FileProcessor & AuditRepository & MinioService, Throwable, Unit] = for {
    cfg <- ZIO.service[AppConfig]
    fileProcessor <- ZIO.service[FileProcessor]
    auditRepo <- ZIO.service[AuditRepository]
    
    // Get list of files to process
    minio <- ZIO.service[MinioService]
    files <- minio.listFiles(cfg.minio.bucket)
    
    _ <- ZIO.logInfo(s"Found ${files.size} files to process")
    
    // Process each file individually
    results <- ZIO.foreach(files) { fileName =>
      for {
        _ <- ZIO.logInfo(s"Processing file: $fileName")
        audit <- fileProcessor.processFile(fileName, cfg.minio.bucket)
        _ <- ZIO.logInfo(s"File $fileName processed: ${audit.totalRecords} total, ${audit.validRecords} valid, ${audit.enrichedRecords} enriched, ${audit.insertedToReview} inserted to review")
      } yield audit
    }
    
    // Summary statistics
    totalFiles = results.size
    totalRecords = results.map(_.totalRecords).sum
    totalValid = results.map(_.validRecords).sum
    totalEnriched = results.map(_.enrichedRecords).sum
    totalInserted = results.map(_.insertedToReview).sum
    
    _ <- ZIO.logInfo(s"ETL pipeline completed. Processed $totalFiles files:")
    _ <- ZIO.logInfo(s"  Total records: $totalRecords")
    _ <- ZIO.logInfo(s"  Valid records: $totalValid")
    _ <- ZIO.logInfo(s"  Enriched records: $totalEnriched")
    _ <- ZIO.logInfo(s"  Inserted to review: $totalInserted")
    
  } yield ()

  override def run = program.provide(
    ConfigLoader.layer, 
    MinioService.live, 
    HermesService.live, 
    SuspiciousTransactionRepository.live,
    AuditRepository.live,
    FileProcessor.live
  )
}
