package etl

import zio._
import java.sql.{Connection, DriverManager, ResultSet, Timestamp}
import java.time.Instant


trait SuspiciousTransactionRepository {
  def upsert(tx: EnrichedTransaction, threshold: Double): Task[Unit]
}

object SuspiciousTransactionRepository {

  val live: ZLayer[Any, Nothing, SuspiciousTransactionRepository] =
    ZLayer.succeed(new SuspiciousTransactionRepository {
      
      private def getConnection: Task[Connection] =
        ZIO.attempt {
          Class.forName("org.postgresql.Driver")
          DriverManager.getConnection(
            sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://postgres:5432/appdb"),
            sys.env.getOrElse("POSTGRES_USER", "postgres"),
            sys.env.getOrElse("POSTGRES_PASSWORD", "postgres")
          )
        }

      private def mapEnrichedToSuspicious(tx: EnrichedTransaction): SuspiciousTransaction =
        SuspiciousTransaction(
          txnId = tx.txn_id,
          userId = tx.user_id,
          amountSar = tx.amount_sar,
          merchant = tx.merchant,
          category = tx.category,
          timestampLocal = Instant.parse(tx.timestamp),
          suspicionScore = tx.suspicion_score,
          checked = false,
          checkedBy = None,
          checkedAt = None,
          checkNotes = None
        )
      private def shouldInsertOrUpdate(tx: SuspiciousTransaction, threshold: Double): Task[Boolean] =
        getConnection.flatMap { conn =>
          ZIO.attemptBlocking {
            val whitelistStmt = conn.prepareStatement(
              """
              SELECT 1
              FROM whitelisted_merchants
              WHERE LOWER(merchant_name) = LOWER(?)
              """
            )
            whitelistStmt.setString(1, tx.merchant)
            val whitelistRs = whitelistStmt.executeQuery()
            val isWhitelisted = whitelistRs.next()

            if (isWhitelisted) {
              false
            } else {
              val stmt = conn.prepareStatement(
                """
                SELECT checked, suspicion_score
                FROM suspicious_transactions
                WHERE txn_id = ?
                """
              )
              stmt.setString(1, tx.txnId)
              val rs = stmt.executeQuery()

              val result =
                if (rs.next()) {
                  val checked = rs.getBoolean("checked")
                  val prevScore = rs.getDouble("suspicion_score")
                  if (checked) false
                  else if (prevScore >= tx.suspicionScore) false
                  else true
                } else if (tx.suspicionScore < threshold) false
                else true

              result
            }
          }.ensuring(ZIO.attemptBlocking(conn.close()).orDie)
        }


      override def upsert(tx: EnrichedTransaction, threshold: Double): Task[Unit] = {
        val suspiciousTx = mapEnrichedToSuspicious(tx)

        shouldInsertOrUpdate(suspiciousTx, threshold).flatMap {
          case false => ZIO.unit
          case true =>
            getConnection.flatMap { conn =>
              ZIO.attemptBlocking {
                val stmt = conn.prepareStatement(
                  """
                  INSERT INTO suspicious_transactions
                    (txn_id, user_id, amount_sar, category, timestamp_local, suspicion_score, checked)
                  VALUES (?, ?, ?, ?, ?, ?, false)
                  ON CONFLICT (txn_id) DO UPDATE
                  SET suspicion_score = EXCLUDED.suspicion_score
                  WHERE suspicious_transactions.checked = false
                  """
                )
                stmt.setString(1, suspiciousTx.txnId)
                stmt.setString(2, suspiciousTx.userId)
                stmt.setBigDecimal(3, suspiciousTx.amountSar.bigDecimal)
                stmt.setString(4, suspiciousTx.category)
                stmt.setTimestamp(5, Timestamp.from(suspiciousTx.timestampLocal))
                stmt.setDouble(6, suspiciousTx.suspicionScore)
                stmt.executeUpdate()
              }.ensuring(ZIO.attemptBlocking(conn.close()).orDie).unit
            }
        }
      }
    })
}
