FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 radiotech && useradd --uid 10001 --gid radiotech --no-create-home radiotech
WORKDIR /app
COPY --from=build --chown=radiotech:radiotech /workspace/build/libs/radiotech.jar app.jar
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
