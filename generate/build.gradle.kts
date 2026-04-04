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
}

application {
    mainClass.set("GenerateExpected")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
