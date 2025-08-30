package etl

import zio._
import java.sql.{Connection, DriverManager, ResultSet, Timestamp}
import com.zaxxer.hikari.HikariDataSource
import javax.sql.DataSource

object DatabaseClient {
  lazy val dataSource: DataSource = {
    val ds = new HikariDataSource()
    ds.setJdbcUrl(sys.env.getOrElse("POSTGRES_URL", "jdbc:postgresql://postgres:5432/appdb"))
    ds.setUsername(sys.env.getOrElse("POSTGRES_USER", "postgres"))
    ds.setPassword(sys.env.getOrElse("POSTGRES_PASSWORD", "postgres"))
    ds.setDriverClassName("org.postgresql.Driver")
    ds.setMaximumPoolSize(10)
    ds
  }

  def getConnection: Task[Connection] =
    ZIO.attemptBlocking(dataSource.getConnection())
}
