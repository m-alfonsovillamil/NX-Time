
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}


group = "com.nxtime"
version = "0.0.1-SNAPSHOT"
description = "nx-time-backend"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}


dependencies {
    // Librerías principales de Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Documentación de la API (Swagger UI / OpenAPI) - se usa a partir de la Fase 6
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Lombok: getters/setters/constructores en las entidades JPA. No genera
    // equals/hashCode (eso se escribe a mano, basado solo en el id: ver
    // auditoría, antipatrón de equals/hashCode sobre relaciones lazy).
    compileOnly("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")

    // MapStruct: mapeo entidad -> DTO, sustituye a las funciones de
    // extensión de Kotlin (toDTO(), toRegistroEquipoDTO()...) dispersas.
    implementation("org.mapstruct:mapstruct:1.6.3")
    annotationProcessor("org.mapstruct:mapstruct-processor:1.6.3")

    // Migraciones de esquema versionadas (ver src/main/resources/db/migration).
    // Sustituye a ddl-auto=update, que generaba el esquema sin control de
    // versiones y sin claves foráneas ni índices (ver auditoría).
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")

    // Librerías para Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    // Testcontainers: PROBADO en la Fase 3 y aparcado por ahora (ver
    // ApiContractTest). Con Docker Desktop 4.87 en Windows, los tres
    // transportes disponibles (los pipes con nombre docker_engine /
    // dockerDesktopLinuxEngine / docker_cli, y el daemon expuesto por
    // TCP en localhost:2375) devuelven una respuesta que la librería
    // docker-java de Testcontainers 1.21.3 no interpreta correctamente
    // -- verificado con un cliente docker-java aislado, no es un
    // problema de configuración de DOCKER_HOST ni de propagación de
    // entorno. Es plausible que sea específico de esta combinación
    // Windows + Docker Desktop y que no se reproduzca en Linux (CI).
    // Los tests usan en su lugar el propio Postgres de
    // docker-compose.yml, creando una base de datos nueva por
    // ejecución -- mismo principio de aislamiento que antes con
    // SQLite, sin la complicación de Testcontainers en este entorno.
    // Se dejan las dependencias listas por si se retoma en la Fase 5.
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")

    /*
     * Base de datos: PostgreSQL (antes SQLite, ver Fase 3 del plan).
     */
    runtimeOnly("org.postgresql:postgresql")

    /*
     * Dependencias para JSON Web Tokens (JWT). 0.12.x desde la Fase 4
     * (antes 0.11.5, con la API setClaims/parserBuilder ya deprecada).
     */
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")

    // Rate limiting en /auth/login y /auth/register-manager (ver
    // LoginRateLimitFilter) -- antes la fuerza bruta era libre.
    implementation("com.bucket4j:bucket4j-core:8.10.1")
}

/*
 * Configura las tareas de testing para que usen JUnit 5.
 */
tasks.withType<Test> {
    useJUnitPlatform()
}
