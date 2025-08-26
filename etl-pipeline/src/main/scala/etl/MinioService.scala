package etl

import io.minio._
import io.minio.messages.Item
import scala.jdk.CollectionConverters._
import zio._
import io.circe.parser._
import io.circe.generic.semiauto._
import io.circe.{Decoder, HCursor}

case class Transaction(
  txn_id: String,
  user_id: String,
  amount: Double,
  timestamp: String,
  currency: String,
  merchant: String,
)

trait MinioService {
  def listFiles(bucket: String): Task[List[String]]
  def readObject(path: String): Task[String]
  def readAllTransactions(bucket: String): Task[List[Transaction]]
}

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
        override def readAllTransactions(bucket: String): Task[List[Transaction]] =
          for {
            files    <- listFiles(bucket)
            grouped  = files.groupBy(_.split("/").headOption.getOrElse("unknown"))
            results  <- ZIO.foreach(grouped.toList) { case (_, bankFiles) =>
                          ZIO.foreach(bankFiles) { file =>
                            readObject(file).map(content => parseFile(file, content))
                          }.map(_.flatten)
                        }
          } yield results.flatten

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
          
      }
    }
}
