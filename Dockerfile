# Stage 1: Construcción de la aplicación
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Instalamos dos2unix (necesario para convertir line endings)
RUN apt-get update && apt-get install -y --no-install-recommends dos2unix && rm -rf /var/lib/apt/lists/*

COPY . .

# Convertimos line endings y damos permisos
RUN dos2unix gradlew && chmod +x gradlew

# Construimos el fat-jar
RUN ./gradlew clean shadowJar

# -----------------------------------------------------------------------
# Stage 2: Imagen de ejecución
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copiamos el JAR generado desde el stage 'builder'
COPY --from=builder /app/build/libs/*.jar app.jar

# Logs y Puertos
VOLUME ["/app/logs"]
ENV PORT=7000
EXPOSE ${PORT}

ENTRYPOINT ["java", "-jar", "/app/app.jar"]