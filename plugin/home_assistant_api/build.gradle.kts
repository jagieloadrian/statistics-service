val kotlin_version: String by project
val ktor_version: String by project

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":plugin:plugin-api"))
    implementation("io.ktor:ktor-server-core:$ktor_version")

    testImplementation("org.junit.jupiter:junit-jupiter:5.14.0")
    testImplementation("io.kotest:kotest-assertions-core-jvm:6.0.7")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:$kotlin_version")
    testImplementation("org.junit.platform:junit-platform-launcher:1.14.1")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("io.ktor:ktor-server-test-host:$ktor_version")
    testImplementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    testImplementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    testImplementation("io.ktor:ktor-client-content-negotiation:$ktor_version")
}

tasks.withType<Test> {
    useJUnitPlatform()
}
