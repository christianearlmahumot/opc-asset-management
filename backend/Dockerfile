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
# Ubuntu 24.04 (noble): its Tesseract package is version 5, which Tess4J 5 needs (22.04 has 4.1).
FROM eclipse-temurin:17-jre-noble
WORKDIR /app

# Tesseract OCR 5 with English for Document Capture (SRS 3.1.2). Tess4J loads "libtesseract.so",
# which only the -dev package links, so link it to the runtime library instead of installing that.
RUN apt-get update \
    && apt-get install -y --no-install-recommends tesseract-ocr tesseract-ocr-eng \
    && ln -sf "$(ls /usr/lib/*/libtesseract.so.5 | head -n 1)" /usr/lib/libtesseract.so \
    && rm -rf /var/lib/apt/lists/*
# One OCR thread: the free plan has a fraction of a CPU, and threads only add memory.
ENV OMP_THREAD_LIMIT=1

RUN useradd --system --uid 10001 fams
COPY --from=build /app/app.jar app.jar
USER fams

# Render's free plan has 512 MB of memory. Keep the heap well inside it and use the
# lightweight serial collector; the rest is left for metaspace, threads and Tesseract, which
# works outside the Java heap.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60 -XX:+UseSerialGC -Xss512k -XX:TieredStopAtLevel=1"

# Render sets PORT; application.yml reads it (server.port: ${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
