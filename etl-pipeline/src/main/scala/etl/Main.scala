package etl

import zio._
import zio.logging.backend.SLF4J

object Main extends ZIOAppDefault {

  val program: ZIO[AppConfig & MinioService, Throwable, Unit] = for {
    cfg <- ZIO.service[AppConfig]
    minio <- ZIO.service[MinioService]
    _     <- ZIO.logInfo(s"Minio bucket: ${cfg.minio.bucket} started ✅")
    txns  <- minio.readAllTransactions(cfg.minio.bucket)
    _     <- ZIO.logInfo(s"Found ${txns.size} transactions in bucket ${cfg.minio.bucket}")
  } yield ()

  override def run = program.provide(ConfigLoader.layer, MinioService.live)
}
