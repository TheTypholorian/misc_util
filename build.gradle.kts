plugins {
    kotlin("jvm") version "2.4.0"
    id("net.typho.typho_publish") version "1.0.4"
}

group = "net.typho"
version = "1.0.1"

repositories {
    mavenCentral()
}

dependencies {
    kotlin("reflect")
}

kotlin {
    jvmToolchain(8)
}