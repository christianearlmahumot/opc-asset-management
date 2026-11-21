# Back end image for Render (SRS 3.1.2: "deployed as a Render web service from a Docker image").
# Build:  docker build -t fams-api ./backend

# ---- build stage: compile the jar with the Maven wrapper -------------------------------------
FROM eclipse-temurin:17-jdk-jammy AS build
WORKDIR /app

# Dependencies first, so they are cached when only the source changes.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN sh mvnw -q -B dependency:go-offline

COPY src src
RUN sh mvnw -q -B -DskipTests package \
    && cp target/fams-api-*.jar app.jar

# ---- run stage: a small JRE image ------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Tesseract OCR 5 for Document Capture (Module 1) is added here in that build step.

RUN useradd --system --uid 10001 fams
COPY --from=build /app/app.jar app.jar
USER fams

# Render's free plan has 512 MB of memory. Keep the heap well inside it and use the
# lightweight serial collector; the rest is left for metaspace, threads and PDF/Excel work.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

# Render sets PORT; application.yml reads it (server.port: ${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
