FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY casino-domain/pom.xml casino-domain/
COPY casino-game/pom.xml casino-game/
COPY casino-app/pom.xml casino-app/
RUN mvn dependency:go-offline -B
COPY casino-domain/src casino-domain/src
COPY casino-game/src casino-game/src
COPY casino-app/src casino-app/src
RUN mvn clean package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl
WORKDIR /app
COPY --from=build /app/casino-app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
