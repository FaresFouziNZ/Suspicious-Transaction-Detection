package etl

import etl.DatabaseClient
import io.minio._
import io.minio.messages.Item
import scala.jdk.CollectionConverters._
import zio._
import io.circe.parser._
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

trait MinioService {
  def listFiles(bucket: String): Task[List[String]]
  def readObject(path: String): Task[String]
  def readAllTransactions(bucket: String): Task[List[Transaction]] // Keep for backward compatibility
  def processFile(fileName: String, bucket: String): Task[FileProcessingResult]
}

case class FileProcessingResult(
  fileName: String,
  transactions: List[Transaction],
  isValid: Boolean,
  errorMessage: Option[String] = None
)

object MinioService {
  val live: ZLayer[AppConfig, Throwable, MinioService] =
    ZLayer.fromZIO {
      for {
        cfg <- ZIO.service[AppConfig]
        client = MinioClient.builder()
          .endpoint(cfg.minio.endpoint)
          .credentials(cfg.minio.accessKey, cfg.minio.secretKey)
          .build()
      } yield new MinioService {
        override def listFiles(bucket: String): Task[List[String]] =
          ZIO.attempt {
            client.listObjects(
              ListObjectsArgs.builder().bucket(bucket).recursive(true).build()
            ).iterator().asScala.toList.map(_.get().objectName())
          }
        
        override def readObject(path: String): Task[String] =
          ZIO.attempt {
            val results = client.getObject(
              io.minio.GetObjectArgs.builder().bucket(cfg.minio.bucket).`object`(path).build()
            )
            scala.io.Source.fromInputStream(results).mkString
          }

        override def processFile(fileName: String, bucket: String): Task[FileProcessingResult] =
          for {
            stat <- getObjectStat(bucket, fileName)
            already <- isAlreadyProcessed(fileName, stat.etag)
            result <- if (already) {
              ZIO.succeed(FileProcessingResult(fileName, Nil, isValid = false, Some("File already processed with same etag")))
            } else if (!isValidExtension(fileName)) {
              ZIO.succeed(FileProcessingResult(fileName, Nil, isValid = false, Some("Invalid file extension")))
            } else {
              (for {
                content <- readObject(fileName)
                txs <- ZIO
                  .attempt(parseFile(fileName, content))
                  .tapError(e => ZIO.logError(s"Corrupted file $fileName: ${e.getMessage}"))
                  .orElseSucceed(Nil)
                _ <- if (txs.nonEmpty) markProcessed(fileName, stat.etag) else ZIO.unit
              } yield FileProcessingResult(fileName, txs, isValid = true))
                .catchAll(e => ZIO.succeed(FileProcessingResult(fileName, Nil, isValid = false, Some(e.getMessage))))
            }
          } yield result

        override def readAllTransactions(bucket: String): Task[List[Transaction]] =
          for {
            files <- listFiles(bucket)
            results <- ZIO.foreach(files) { file =>
              processFile(file, bucket).map(_.transactions)
            }
          } yield results.flatten
        
        // --- New helper ---
        private def getObjectStat(bucket: String, file: String): Task[ObjectStat] =
          ZIO.attempt {
            val stat = client.statObject(
              StatObjectArgs.builder().bucket(bucket).`object`(file).build()
            )
            ObjectStat(stat.etag())
          }
        
        private def isValidExtension(fileName: String): Boolean =
          fileName.endsWith(".json") || fileName.endsWith(".csv")

        private def parseFile(fileName: String, content: String): List[Transaction] = {
          if (fileName.endsWith(".json")) {
            parseJson(content)
          } else if (fileName.endsWith(".csv")) {
            parseCsv(content)
          } else {
            Nil
          }
        }

        implicit val transactionDecoder: Decoder[Transaction] = new Decoder[Transaction] {
          final def apply(c: HCursor): Decoder.Result[Transaction] =
            for {
              txn_id    <- c.downField("txn_id").as[String]
              .orElse(Right("unknown"))
              user_id   <- c.downField("user_id").as[String]
              .orElse(Right("unknown"))
              amount    <- c.downField("amount").as[Double]
              .orElse(c.downField("amount").as[String].flatMap { s =>
                scala.util.Try(s.toDouble).toOption match {
                  case Some(amount) => Right(amount)
                  case None => Right(0.0)
                }
              })
              timestamp <- c.downField("timestamp").as[String]
              currency  <- c.downField("currency").as[String]
              merchant  <- c.downField("merchant").as[String]
            } yield Transaction(txn_id, user_id, amount, timestamp, currency, merchant)
        }

        private def parseJson(json: String): List[Transaction] = {
          decode[List[Transaction]](json).toOption
            .orElse {
              val lines = json.split("\n").toList.filter(_.nonEmpty)
              val decoded = lines.flatMap(line => decode[Transaction](line).toOption)
              if (decoded.nonEmpty) Some(decoded) else None
            }
            .getOrElse(Nil)
        }

          private def parseCsv(csv: String): List[Transaction] = {
            val lines = csv.split("\n").toList.filter(_.nonEmpty)
            val header = lines.head.split(",").map(_.trim).zipWithIndex.toMap
          
            lines.tail.flatMap { line =>
              val parts = line.split(",").map(_.trim)
          
              def get(col: String): Option[String] = header.get(col).flatMap(idx => parts.lift(idx))
          
              val txn_id    = get("txn_id").getOrElse("")
              val user_id   = get("user_id").getOrElse("")
              val amountStr = get("amount").getOrElse("0.0")
              val amount    = scala.util.Try(amountStr.toDouble).getOrElse(0.0)
              val timestamp = get("timestamp").getOrElse("")
              val currency  = get("currency").getOrElse("")
              val merchant  = get("merchant").getOrElse("")
          
              if (txn_id.nonEmpty && user_id.nonEmpty && timestamp.nonEmpty) {
                Some(Transaction(txn_id, user_id, amount, timestamp, currency, merchant))
              } else {
                None
              }
            }
          }

          private def isAlreadyProcessed(fileName: String, etag: String): Task[Boolean] =
            DatabaseClient.getConnection.flatMap { conn =>
              ZIO.attemptBlocking {
                val stmt = conn.prepareStatement(
                  "SELECT 1 FROM processed_files WHERE file_name = ? AND etag = ?"
                )
                stmt.setString(1, fileName)
                stmt.setString(2, etag)
                val rs = stmt.executeQuery()
                rs.next()
              }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
            }

          private def markProcessed(fileName: String, etag: String): Task[Unit] =
            DatabaseClient.getConnection.flatMap { conn =>
              ZIO.attemptBlocking {
                val stmt = conn.prepareStatement(
                  "INSERT INTO processed_files (file_name, etag) VALUES (?, ?) " +
                  "ON CONFLICT (file_name) DO UPDATE SET etag = EXCLUDED.etag"
                )
                stmt.setString(1, fileName)
                stmt.setString(2, etag)
                stmt.executeUpdate()
                ()
              }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
            }
      }
    }
}
