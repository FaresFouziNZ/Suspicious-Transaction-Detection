# 1. Build stage
FROM sbtscala/scala-sbt:graalvm-community-24.0.2_1.11.4_3.7.2 AS build

WORKDIR /app

COPY build.sbt .
COPY project ./project
COPY src ./src

# Make sure sbt assembly runs and produces the JAR
RUN sbt -batch clean assembly

# 2. Runtime stage
FROM eclipse-temurin:17-jre

WORKDIR /app

# Copy the fat JAR from the build stage
COPY --from=build /app/target/scala-2.13/etl-pipeline-fat.jar /app/app.jar

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
