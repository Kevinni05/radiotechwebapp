#!/bin/sh
set -eu
if [ -n "${RADIOTECH_UPSTREAM_URL:-}" ]; then
  exec java -Djava.net.preferIPv4Stack=true -XX:MaxRAMPercentage=70 -cp /app/edge-proxy EdgeProxy
fi
exec java -XX:MaxRAMPercentage=70 -XX:+ExitOnOutOfMemoryError -jar /app/app.jar
