package etl

final case class AppConfig(
  minio: MinioConfig,
  postgres: PostgresConfig,
  hermes: HermesConfig
)

final case class MinioConfig(
  endpoint: String,
  accessKey: String,
  secretKey: String,
  bucket: String
)

final case class PostgresConfig(
  url: String,
  user: String,
  password: String,
  database: String
)

final case class HermesConfig(
  url: String
)
