import io.ktor.plugin.OpenApiPreview

val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.serialization") version "2.2.21"
    id("io.ktor.plugin") version "3.3.2"
}

group = "com.anjo"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    //Headers
    implementation("io.ktor:ktor-server-default-headers")
    //Core
    implementation("io.ktor:ktor-server-core")
    implementation("io.ktor:ktor-server-netty")
    implementation("io.ktor:ktor-serialization-kotlinx-json")
    implementation("io.ktor:ktor-server-content-negotiation")
    implementation("io.ktor:ktor-server-di")
    implementation("io.ktor:ktor-server-config-yaml")
    implementation("io.ktor:ktor-server-request-validation")
    implementation("io.github.oshai:kotlin-logging-jvm:7.0.14")

    //CACHE
    implementation("com.ucasoft.ktor:ktor-simple-cache:0.55.3")
    implementation("com.ucasoft.ktor:ktor-simple-redis-cache:0.55.3")

    //METRICS
    implementation("dev.hayden:khealth:3.0.2")
    implementation("io.ktor:ktor-server-metrics")
    implementation("io.ktor:ktor-server-call-logging")
    implementation("io.ktor:ktor-server-call-id")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("io.ktor:ktor-server-cors")
    implementation("io.ktor:ktor-server-host-common")
    implementation("io.ktor:ktor-server-status-pages")

    //Redis
    implementation("io.lettuce:lettuce-core:7.2.1.RELEASE")

    //COROUTINES
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.10.2")
    runtimeOnly("org.jetbrains.kotlinx:kotlinx-coroutines-reactive:1.10.2")


    //SZWAGIER
    implementation("io.ktor:ktor-server-swagger:${ktor_version}")

    //TESTS
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
    testImplementation("io.mockk:mockk-jvm:1.14.7")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.platform:junit-platform-launcher:1.14.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")

    //TEST CONTAINERS
    testImplementation("org.testcontainers:testcontainers:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

ktor {
    @OptIn(OpenApiPreview::class)
    openApi {
        title = "StatisticsService"
        version = "1.0"
        description = "This is a small project to store and calculate statistics from diff sources"
        target = project.layout.projectDirectory.dir("openapi").file("document.yaml")
    }
}
