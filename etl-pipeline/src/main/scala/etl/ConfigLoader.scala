package etl

import zio._
import zio.config._
import zio.config.magnolia._
import zio.config.typesafe.TypesafeConfigProvider

object ConfigLoader {

  private val descriptor = deriveConfig[AppConfig]

  val layer: ZLayer[Any, Config.Error, AppConfig] =
    ZLayer.fromZIO {
      ZIO.config(descriptor).provide(
        // ZLayer.succeed(TypesafeConfigProvider.fromResourcePath())
      )
    }
}
