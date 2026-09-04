FROM eclipse-temurin:26-jdk AS builder

WORKDIR /workspace

COPY libs/cpf-contract ./libs/cpf-contract

COPY services/validacao-cpf-service/gradlew \
     services/validacao-cpf-service/settings.gradle \
     services/validacao-cpf-service/build.gradle \
     ./services/validacao-cpf-service/

COPY services/validacao-cpf-service/gradle \
     ./services/validacao-cpf-service/gradle

WORKDIR /workspace/services/validacao-cpf-service

RUN chmod +x gradlew
RUN ./gradlew dependencies --no-daemon

COPY services/validacao-cpf-service/src ./src

RUN ./gradlew bootJar --no-daemon


FROM eclipse-temurin:26-jre

WORKDIR /app

COPY --from=builder \
    /workspace/services/validacao-cpf-service/build/libs/validacao-cpf-service.jar \
    validacao-cpf-service.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "validacao-cpf-service.jar"]