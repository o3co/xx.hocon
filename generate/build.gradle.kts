plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.typesafe:config:1.4.3")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.2")
}

application {
    mainClass.set("GenerateExpected")
}

tasks.register<JavaExec>("probeIssue31") {
    group = "verification"
    description = "Probe Lightbend behavior for xx.hocon issue #31 matrix"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ProbeIssue31")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
