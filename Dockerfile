# ---------- Stage 1: build the jar with Maven + Java 17 ----------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src

# Download dependencies first so Docker can cache this layer
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

# Copy the source code and build (tests skipped for a faster deploy)
COPY src ./src
RUN mvn -q -B package -DskipTests

# ---------- Stage 2: small runtime image with Java 17 JRE only ----------
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as a normal user, and create the folder for uploaded resumes
RUN useradd -r -u 1001 app \
    && mkdir -p /data/uploads \
    && chown -R app /data
USER app

COPY --from=build /src/target/*.jar app.jar

# Same port as server.port in application.properties
EXPOSE 10000

# Low-memory settings for small free servers
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]