# Imagen de producción del backend (Railway, Render, Fly.io, Cloud Run...).
# Contexto de build: backend/  ->  docker build -t autopartco-backend backend
# Java 26 (pom.xml): los buildpacks automáticos aún no lo detectan, por eso se usa Docker.

# --- Etapa 1: compilar el jar ---
FROM eclipse-temurin:26-jdk AS build
WORKDIR /app

# Wrapper y pom primero: las dependencias quedan en caché mientras no cambie el pom.
COPY mvnw pom.xml ./
COPY .mvn .mvn
# Un checkout en Windows puede dejar mvnw con CRLF.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -B -q dependency:go-offline

COPY src src
# main no trae src/test; las pruebas se ejecutan en develop.
RUN ./mvnw -B -q package -DskipTests \
    && cp target/*.jar app.jar

# --- Etapa 2: solo el runtime ---
FROM eclipse-temurin:26-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /app/app.jar app.jar
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN sed -i 's/\r$//' /usr/local/bin/docker-entrypoint.sh \
    && chmod +x /usr/local/bin/docker-entrypoint.sh \
    && mkdir -p /app/certs && chown app:app /app/certs

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

EXPOSE 8081
ENTRYPOINT ["docker-entrypoint.sh"]
