package etl

import zio._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  val program: ZIO[AppConfig & MinioService & HermesService & SuspiciousTransactionRepository, Throwable, Unit] = for {
    cfg <- ZIO.service[AppConfig]
    minio <- ZIO.service[MinioService]
    txns  <- minio.readAllTransactions(cfg.minio.bucket)
    _     <- ZIO.logInfo(s"Total Records = ${txns.size}")
    hermes <- ZIO.service[HermesService]
    enrichedTxns <- hermes.enrichBatch(txns)
    _     <- ZIO.logInfo(s"Enriched & Scored Records = ${enrichedTxns.size}")
    repo <- ZIO.service[SuspiciousTransactionRepository]
    _ <- ZIO.foreach(enrichedTxns)(txn => repo.upsert(txn, 60))
    _ <- ZIO.logInfo("ETL pipeline completed")
  } yield ()

  override def run = program.provide(ConfigLoader.layer, MinioService.live, HermesService.live, SuspiciousTransactionRepository.live)
}
