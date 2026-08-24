
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

    // Librerías para Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.security:spring-security-test")
    testCompileOnly("org.projectlombok:lombok:1.18.34")
    testAnnotationProcessor("org.projectlombok:lombok:1.18.34")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")

    /*
     * Dependencias específicas de la Base de Datos (SQLite)
     */
    implementation("org.xerial:sqlite-jdbc:3.43.0.0")
    implementation("org.hibernate.orm:hibernate-community-dialects:6.2.7.Final")

    /*
     * Dependencias para JSON Web Tokens (JWT)
     */
    implementation("io.jsonwebtoken:jjwt-api:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.11.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.11.5")
}

/*
 * Configura las tareas de testing para que usen JUnit 5.
 */
tasks.withType<Test> {
    useJUnitPlatform()
}
