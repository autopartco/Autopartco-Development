#!/bin/sh
# Arranque del backend en contenedor.
# - Puerto: usa PORT si la plataforma lo inyecta (Railway, Render, Cloud Run); si no, SERVER_PORT o 8081.
# - Llaves JWT: los .pem no están en el repo. Si llegan como contenido en RSA_PUBLIC_KEY_PEM /
#   RSA_PRIVATE_KEY_PEM, se escriben en /app/certs y se apuntan RSA_*_KEY_PATH a esos archivos.
set -eu

if [ -n "${RSA_PUBLIC_KEY_PEM:-}" ]; then
    printf '%s\n' "$RSA_PUBLIC_KEY_PEM" > /app/certs/pub.pem
    export RSA_PUBLIC_KEY_PATH=file:/app/certs/pub.pem
fi

if [ -n "${RSA_PRIVATE_KEY_PEM:-}" ]; then
    umask 077
    printf '%s\n' "$RSA_PRIVATE_KEY_PEM" > /app/certs/pri.pem
    export RSA_PRIVATE_KEY_PATH=file:/app/certs/pri.pem
fi

export SERVER_PORT="${PORT:-${SERVER_PORT:-8081}}"

# shellcheck disable=SC2086
exec java $JAVA_OPTS -jar /app/app.jar
