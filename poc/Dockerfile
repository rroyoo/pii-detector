FROM maven:3.9-eclipse-temurin-17 AS agent-build
WORKDIR /src/agent
COPY agent/pom.xml .
COPY agent/src ./src
RUN mvn -q package

FROM maven:3.9-eclipse-temurin-17 AS app-build
WORKDIR /src
COPY poc/pom.xml ./pom.xml
COPY poc/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=agent-build /src/agent/target/pii-detector-agent-1.0.0-SNAPSHOT.jar /opt/pii/pii-detector-agent.jar
COPY --from=app-build /src/target/pii-detector-poc-1.0.0-SNAPSHOT.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-javaagent:/opt/pii/pii-detector-agent.jar", "-jar", "/app/app.jar"]
