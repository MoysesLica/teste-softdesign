FROM eclipse-temurin:26-jdk AS builder

WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY src ./src

RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:26-jre

WORKDIR /app

COPY --from=builder /workspace/build/libs/votacao-service.jar votacao-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "votacao-service.jar"]