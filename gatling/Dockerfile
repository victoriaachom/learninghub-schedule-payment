# Runs the Gatling stress test inside Docker, so no local Java or Maven is needed.
#   docker compose --profile stress run --rm gatling
# The HTML report is written to ./gatling/results on your machine.
FROM maven:3.9-eclipse-temurin-21
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q test-compile
ENTRYPOINT ["mvn", "-B", "gatling:test"]
