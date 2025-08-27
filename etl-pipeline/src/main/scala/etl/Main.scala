package etl

import zio._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  val program: ZIO[AppConfig & MinioService & HermesService, Throwable, Unit] = for {
    cfg <- ZIO.service[AppConfig]
    minio <- ZIO.service[MinioService]
    _     <- ZIO.logInfo(s"Minio bucket: ${cfg.minio.bucket} started ✅")
    txns  <- minio.readAllTransactions(cfg.minio.bucket)
    _     <- ZIO.logInfo(s"Found ${txns.size} transactions in bucket ${cfg.minio.bucket}")
    hermes <- ZIO.service[HermesService]
    enrichedTxns <- hermes.enrichBatch(txns)
    _ <- ZIO.foreach(enrichedTxns)(f => ZIO.logInfo(s"${f}"))

  } yield ()

  override def run = program.provide(ConfigLoader.layer, MinioService.live, HermesService.live)
}
