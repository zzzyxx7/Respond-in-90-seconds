FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
RUN chmod +x mvnw

COPY src src
RUN ./mvnw -DskipTests package

FROM eclipse-temurin:21-jre

WORKDIR /app

COPY --from=build /workspace/target/doc-fusion-0.0.1-SNAPSHOT.jar /app/app.jar

RUN mkdir -p /app/uploads

EXPOSE 8081

ENTRYPOINT ["java", "-Xms512m", "-Xmx1024m", "-jar", "/app/app.jar"]
