FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY --chmod=755 mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY config-server/pom.xml config-server/
COPY discovery-server/pom.xml discovery-server/
COPY api-gateway/pom.xml api-gateway/
COPY user-service/pom.xml user-service/
COPY event-service/pom.xml event-service/
COPY payment-service/pom.xml payment-service/
COPY booking-service/pom.xml booking-service/
COPY notification-service/pom.xml notification-service/

COPY discovery-server/src discovery-server/src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -B -ntp -pl discovery-server -am package -DskipTests \
    && cp discovery-server/target/*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
COPY --from=build /workspace/app.jar app.jar
USER app

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

EXPOSE 8761
ENTRYPOINT ["java", "-jar", "app.jar"]
