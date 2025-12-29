FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
COPY relboard-crawler ./relboard-crawler
COPY libs ./libs

RUN chmod +x gradlew
RUN ./gradlew :relboard-crawler:bootJar --no-daemon

FROM eclipse-temurin:21-jre

WORKDIR /app

ENV JAVA_OPTS=""

COPY --from=builder /app/relboard-crawler/build/libs/*-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java ${JAVA_OPTS} -jar app.jar"]
