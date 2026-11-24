# Build único dos módulos Maven; dois alvos finais (gateway e worker).
#   docker build --target gateway -t tutoria-gateway backend/
#   docker build --target worker  -t tutoria-worker  backend/
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY common common
COPY gateway gateway
COPY worker worker
RUN --mount=type=cache,target=/root/.m2 sed -i 's/\r$//' mvnw && chmod +x mvnw && ./mvnw -B -q -DskipTests package

FROM eclipse-temurin:21-jre AS gateway
WORKDIR /app
COPY --from=build /src/gateway/target/gateway-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

FROM eclipse-temurin:21-jre AS worker
WORKDIR /app
COPY --from=build /src/worker/target/worker-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
