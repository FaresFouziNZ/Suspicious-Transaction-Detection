package etl

import sttp.client3._
import sttp.client3.httpclient.zio._
import zio._
import zio.json._

object HermesJson {
  implicit val enrichDataDecoder: JsonDecoder[EnrichData]       = DeriveJsonDecoder.gen[EnrichData]
  implicit val enrichResponseDecoder: JsonDecoder[EnrichResponse] = DeriveJsonDecoder.gen[EnrichResponse]
  implicit val scoreDataDecoder: JsonDecoder[ScoreData]         = DeriveJsonDecoder.gen[ScoreData]
  implicit val scoreResponseDecoder: JsonDecoder[ScoreResponse] = DeriveJsonDecoder.gen[ScoreResponse]
}

trait HermesService {
  def enrich(txn: Transaction): Task[EnrichedTransaction]
  def enrichBatch(txns: List[Transaction]): Task[List[EnrichedTransaction]]
}

object HermesService {
  val live: ZLayer[AppConfig, Throwable, HermesService] =
    ZLayer.fromZIO {
      for {
        cfg <- ZIO.service[AppConfig]
      } yield new HermesService {
        import HermesJson._
        
        private val baseUri = uri"${cfg.hermes.url}"
        
        // Retry configuration
        private val maxRetries = 3
        private val baseDelay = 1.second
        private val maxDelay = 30.seconds
        
        private def retryWithBackoff[A](operation: Task[A]): Task[A] = {
          def attempt(retryCount: Int): Task[A] = {
            operation.catchAll { error =>
              if (retryCount >= maxRetries) {
                ZIO.fail(error)
              } else {
                val delay = (baseDelay * math.pow(2, retryCount)).min(maxDelay)
                ZIO.sleep(delay) *> attempt(retryCount + 1)
              }
            }
          }
          attempt(0)
        }

        override def enrich(txn: Transaction): Task[EnrichedTransaction] =
          ZIO.scoped {
            for {
              backend <- HttpClientZioBackend.scoped()
              enrichReq = basicRequest
                .get(baseUri.addPath("enrich")
                  .addParam("txn_id", txn.txn_id)
                  .addParam("amount", txn.amount.toString)
                  .addParam("currency", txn.currency)
                  .addParam("merchant", txn.merchant))
                .response(asStringAlways)

              enrichResp   <- retryWithBackoff(backend.send(enrichReq))
              parsedEnrich <- ZIO.fromEither(enrichResp.body.fromJson[EnrichResponse])
                                .mapError(e => new RuntimeException(s"Failed to parse enrich response: $e"))

              scoreReq = basicRequest
                          .get(baseUri.addPath("score")
                            .addParam("txn_id", txn.txn_id)
                            .addParam("amount_sar", parsedEnrich.data.amount_sar.toString)
                            .addParam("category", parsedEnrich.data.category)
                            .addParam("timestamp", txn.timestamp))
                          .response(asStringAlways)

              scoreResp   <- retryWithBackoff(backend.send(scoreReq))
              parsedScore <- ZIO.fromEither(scoreResp.body.fromJson[ScoreResponse])
                              .mapError(e => new RuntimeException(s"Failed to parse score response: $e"))
            } yield EnrichedTransaction(
              txn.txn_id,
              txn.user_id,
              txn.amount,
              txn.timestamp,
              txn.currency,
              txn.merchant,
              parsedEnrich.data.amount_sar,
              parsedEnrich.data.category,
              parsedScore.data.suspicion_score
            )
          }

        override def enrichBatch(txns: List[Transaction]): Task[List[EnrichedTransaction]] = {
          ZIO.foreach(txns)(enrich)
        }
      }
    }
}

