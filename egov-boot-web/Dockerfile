FROM eclipse-temurin:17-jre

WORKDIR /app

ARG JAR_FILE=target/egovframe-project-1.0.0.jar
COPY ${JAR_FILE} app.jar

ENV SERVER_PORT=8081
EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
