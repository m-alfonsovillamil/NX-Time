
plugins {
    kotlin("jvm")
    kotlin("plugin.spring")
    id("org.springframework.boot")
    id("io.spring.dependency-management")
    kotlin("plugin.jpa")
}


group = "com.nxtime"
version = "0.0.1-SNAPSHOT"
description = "nx-time-backend"


java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}


dependencies {
    // Librerías principales de Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    developmentOnly("org.springframework.boot:spring-boot-devtools")

    // Documentación de la API (Swagger UI / OpenAPI) - se usa a partir de la Fase 6
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")

    // Librerías para Tests
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.springframework.security:spring-security-test")
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


    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}


allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

/*
 * Configura las tareas de testing para que usen JUnit 5.
 */
tasks.withType<Test> {
    useJUnitPlatform()
}