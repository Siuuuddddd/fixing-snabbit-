# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace

COPY . .

ARG SERVICE
RUN --mount=type=cache,target=/root/.gradle \
    test -n "$SERVICE" \
    && chmod +x gradlew \
    && ./gradlew ":${SERVICE}:bootJar" --no-daemon \
    && cp "${SERVICE}/build/libs/${SERVICE}-0.1.0-SNAPSHOT.jar" /workspace/application.jar

FROM eclipse-temurin:21-jre-jammy
WORKDIR /application

RUN useradd --system --uid 10001 --create-home spring
USER spring

COPY --from=build /workspace/application.jar application.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "application.jar"]
