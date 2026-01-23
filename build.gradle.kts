val kotlin_version: String by project
val logback_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
    id("io.ktor.plugin")
    jacoco
}

group = "com.anjo"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

dependencies {
    //Headers
    implementation("io.ktor:ktor-server-default-headers")
    //Kotlinx date time
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.7.1")

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
    // will be removed with ktor 3.4.1
    implementation("io.ktor:ktor-server-routing-openapi:${ktor_version}")

    //TESTS
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
    testImplementation("io.mockk:mockk-jvm:1.14.7")
    testImplementation("io.ktor:ktor-server-test-host")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.platform:junit-platform-launcher:1.14.1")
    testImplementation("io.ktor:ktor-client-content-negotiation:${ktor_version}")
    testImplementation("redis.clients:jedis:7.2.0")

    //TEST CONTAINERS
    testImplementation("org.testcontainers:testcontainers:1.20.3")
    testImplementation("org.testcontainers:junit-jupiter:1.20.3")
    testImplementation("com.redis:testcontainers-redis:2.2.4")
}

jacoco {
    toolVersion = "0.8.14"
}

tasks.jacocoTestReport {
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}
//
//ktor {
//    openApi {}
//}
