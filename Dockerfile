# syntax=docker/dockerfile:1

# ---- build stage: compile, generate the OpenAPI sources, and package the jar ----
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Resolve dependencies first so code-only changes reuse this cached layer.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline

COPY src ./src
# Tests run in CI (./mvnw verify); the image build only packages the application.
RUN ./mvnw -B -q package -DskipTests \
 && mv target/fraud-detection-service-*.jar /workspace/app.jar

# ---- runtime stage: JRE only, non-root, no build tools ----
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app app
WORKDIR /app
COPY --from=build /workspace/app.jar /app/app.jar
USER app
EXPOSE 8070

# Container-aware heap sizing; the container memory limit, not the host, sets the ceiling.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
