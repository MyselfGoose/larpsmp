plugins {
    java
}

group = "com.larpsmp"
version = "0.1.0"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

val postgresqlVersion = "42.7.4"
val hikariCpVersion = "6.2.1"
val bouncyCastleVersion = "1.79"

dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")

    compileOnly("org.postgresql:postgresql:$postgresqlVersion")
    compileOnly("com.zaxxer:HikariCP:$hikariCpVersion")
    compileOnly("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.postgresql:postgresql:$postgresqlVersion")
    testImplementation("com.zaxxer:HikariCP:$hikariCpVersion")
    testImplementation("org.bouncycastle:bcprov-jdk18on:$bouncyCastleVersion")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

tasks.processResources {
    val props = mapOf("version" to version)
    inputs.properties(props)
    filesMatching("plugin.yml") {
        expand(props)
    }
}

tasks.jar {
    archiveBaseName.set("money-event")
}
