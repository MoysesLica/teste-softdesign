FROM eclipse-temurin:26-jdk AS builder

WORKDIR /workspace

COPY libs/cpf-contract ./libs/cpf-contract

COPY services/votacao-service/gradlew \
     services/votacao-service/settings.gradle \
     services/votacao-service/build.gradle \
     ./services/votacao-service/

COPY services/votacao-service/gradle \
     ./services/votacao-service/gradle

WORKDIR /workspace/services/votacao-service

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY services/votacao-service/src ./src

RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:26-jre

WORKDIR /app

COPY --from=builder \
    /workspace/services/votacao-service/build/libs/votacao-service.jar \
    votacao-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "votacao-service.jar"]