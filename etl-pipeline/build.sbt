// build.sbt

ThisBuild / scalaVersion := "2.13.13"

lazy val root = (project in file("."))
  .settings(
    name := "etl-pipeline",
    version := "0.1.0",

    // Dependencies
    libraryDependencies ++= Seq(
      "dev.zio" %% "zio" % "2.0.21",
      "dev.zio" %% "zio-config" % "4.0.2",
      "dev.zio" %% "zio-config-magnolia" % "4.0.2",
      "dev.zio" %% "zio-config-typesafe" % "4.0.2",
      "dev.zio" %% "zio-logging" % "2.1.14",
      "dev.zio" %% "zio-logging-slf4j" % "2.1.14",
      "io.minio" % "minio" % "8.5.7",
      "org.postgresql" % "postgresql" % "42.6.0",
      "com.softwaremill.sttp.client3" %% "core" % "3.9.8",
      "com.softwaremill.sttp.client3" %% "zio"  % "3.9.8",
      "com.softwaremill.sttp.client3" %% "async-http-client-backend-zio" % "3.9.8",
      "io.circe" %% "circe-core" % "0.14.5",
      "io.circe" %% "circe-generic" % "0.14.5",
      "io.circe" %% "circe-parser" % "0.14.5",
      "dev.zio" %% "zio-json" % "0.6.2"
    ),

    Compile / mainClass := Some("etl.Main"),

    // sbt-assembly plugin settings
    assembly / assemblyJarName := "etl-pipeline-fat.jar",
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", xs @ _*) => MergeStrategy.discard
      case "reference.conf"               => MergeStrategy.concat
      case x                              => MergeStrategy.first
    }
  )
