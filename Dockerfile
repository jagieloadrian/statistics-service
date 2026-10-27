# Runtime-only image: run `./gradlew buildFatJar` first. Config comes from env vars (see application.yaml).
FROM eclipse-temurin:17-jre
RUN useradd --system app
USER app
COPY build/libs/*-all.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
