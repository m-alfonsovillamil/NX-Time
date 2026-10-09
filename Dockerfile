# syntax=docker/dockerfile:1
#
# Imagen del backend de NX Time (Fase 7 del plan de profesionalización).
# Multi-stage: la etapa de build (JDK completo + Gradle) no llega a la
# imagen final -- solo el JRE y el jar en capas.
#
# Usa settings-docker.gradle.kts (raíz del monorepo) en vez del
# settings.gradle.kts normal: ese incluye también el módulo Android, que
# necesita el SDK de Android para configurarse y no tiene sentido dentro
# de esta imagen (ver el propio settings-docker.gradle.kts).

# ---- Etapa 1: build ---------------------------------------------------
FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace

# Copiados por separado del código fuente a propósito: Docker cachea
# cada capa por separado, así que mientras no cambien el wrapper ni las
# dependencias declaradas, "gradlew dependencies" reutiliza la capa
# entera aunque el código (nx-time-backend/src) cambie constantemente.
#
# settings-docker.gradle.kts se copia CON EL NOMBRE settings.gradle.kts: hasta
# Gradle 8 se elegía con la opción -c, que Gradle 9 ha quitado. Dentro de la
# imagen no hay otro settings, así que basta con llamarlo como Gradle espera.
COPY gradlew ./
COPY settings-docker.gradle.kts settings.gradle.kts
COPY gradle gradle
COPY nx-time-backend/build.gradle.kts nx-time-backend/build.gradle.kts
RUN chmod +x gradlew && \
    ./gradlew :nx-time-backend:dependencies --no-daemon > /dev/null 2>&1 || true

COPY nx-time-backend/src nx-time-backend/src

# -x test: los tests (Fase 5, 117 y contando) necesitan el PostgreSQL
# de docker-compose corriendo aparte -- no tiene sentido ni es posible
# ejecutarlos dentro del build de la imagen. Se ejecutan en CI (Fase 11)
# y en local antes de construir la imagen, no aquí.
RUN ./gradlew :nx-time-backend:bootJar -x test --no-daemon

# "tools extract --layers" (jarmode "tools"; sustituye al "layertools"
# de versiones anteriores de Spring Boot, ya deprecado) separa el jar
# ejecutable en capas (dependencias de terceros, dependencias snapshot,
# el propio Spring Boot Loader, y el código de la aplicación) en vez de
# copiar un único jar monolítico: las tres primeras apenas cambian de
# una build a otra, así que Docker reutiliza esas capas de la imagen
# final casi siempre -- solo reconstruye la capa "application" (ligera)
# cuando cambia el código.
RUN java -Djarmode=tools -jar nx-time-backend/build/libs/*.jar extract --layers --launcher --destination extracted

# ---- Etapa 2: runtime ---------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Usuario no-root: si el proceso Java se ve comprometido, no hereda
# privilegios de root dentro del contenedor.
RUN addgroup -S nxtime && adduser -S nxtime -G nxtime
USER nxtime

COPY --from=build --chown=nxtime:nxtime /workspace/extracted/dependencies/ ./
COPY --from=build --chown=nxtime:nxtime /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=nxtime:nxtime /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=nxtime:nxtime /workspace/extracted/application/ ./

EXPOSE 8080

# /actuator/health, público sin token (ver SecurityConfig) y con
# show-details:never (ver application.yml) -- da un UP/DOWN agregado,
# sin filtrar si es Postgres quien está caído.
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1

# Memoria (revisión de seguridad del 1/10/2026). Sin MaxRAMPercentage, la JVM
# en un contenedor se queda con el 25 % de la memoria para el heap: en los
# 512 MB del plan gratuito de Render, 128 MB, poco para un PDF y una foto a la
# vez. Con 75 % queda margen para el resto del proceso (metaspace, hilos,
# buffers de Tomcat). ExitOnOutOfMemoryError: si aun así se agota, el proceso
# muere y Render lo arranca de nuevo, en vez de quedarse vivo y medio roto.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "org.springframework.boot.loader.launch.JarLauncher"]
