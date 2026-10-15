FROM maven:3.9-eclipse-temurin-25 AS build

WORKDIR /workspace

COPY pom.xml .

RUN mvn -B dependency:go-offline

COPY src ./src

RUN mvn -B package


FROM eclipse-temurin:25-jre-noble

RUN apt-get update \
    && apt-get install -y --no-install-recommends \
        tesseract-ocr \
        tesseract-ocr-pol \
        tesseract-ocr-eng \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid 10001 --no-create-home app

WORKDIR /app

COPY --chown=app:app --from=build /workspace/target/backend-0.0.1-SNAPSHOT.jar app.jar

ENV APP_OCR_TESSERACT_DATA_PATH=/usr/share/tesseract-ocr/5/tessdata

EXPOSE 8080

USER app

ENTRYPOINT ["java", "-jar", "app.jar"]
