FROM node:22-bookworm-slim AS frontend
WORKDIR /frontend
COPY package.json package-lock.json ./
RUN npm ci
COPY scripts scripts
COPY frontend frontend
COPY src/main/resources/static/assets/mfa-dialog.js src/main/resources/static/assets/mfa-dialog.js
RUN npm run build:auth && npm run build:control-room && npm run build:operations

FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew
COPY src src
COPY --from=frontend /frontend/src/main/resources/static/assets/auth-client.js src/main/resources/static/assets/auth-client.js
COPY --from=frontend /frontend/src/main/resources/static/assets/control-room.js src/main/resources/static/assets/control-room.js
COPY --from=frontend /frontend/src/main/resources/static/assets/operations-upgrade.js src/main/resources/static/assets/operations-upgrade.js
RUN ./gradlew bootJar --no-daemon --console=plain

FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*
RUN groupadd --gid 10001 radiotech \
    && useradd --uid 10001 --gid radiotech --no-create-home radiotech \
    && (getent group 1000 >/dev/null || groupadd --gid 1000 rendersecrets) \
    && usermod -a -G 1000 radiotech
WORKDIR /app
COPY --from=build --chown=radiotech:radiotech /workspace/build/libs/radiotech.jar app.jar
RUN mkdir -p /app/.dist/private-files \
    && chown 10001:10001 /app/.dist/private-files \
    && chmod 700 /app/.dist/private-files
USER 10001:10001
ENV SPRING_PROFILES_ACTIVE=production
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "/app/app.jar"]
