plugins {
    java
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.typesafe:config:1.4.6")
    implementation("com.google.code.gson:gson:2.11.0")
    implementation("org.yaml:snakeyaml:2.2")
}

application {
    mainClass.set("GenerateExpected")
}

tasks.register<JavaExec>("generateHarvested") {
    group = "build"
    description = "Regenerate expected/harvested/ from the harvested ecosystem corpus (testdata/harvested/)"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("GenerateHarvested")
}

tasks.register<JavaExec>("probeIssue31") {
    group = "verification"
    description = "Probe Lightbend behavior for xx.hocon issue #31 matrix"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ProbeIssue31")
}

tasks.register<JavaExec>("probeS13_9") {
    group = "verification"
    description = "Probe Lightbend S13.9 behavior — HOME=null + optional/required substitution"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ProbeS13_9")
}

tasks.register<JavaExec>("probeKeyHyphenAndPathWS") {
    group = "verification"
    description = "Probe Lightbend behavior for xx.hocon issue #42 — S8.6 in key + path-expression whitespace"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ProbeKeyHyphenAndPathWS")
}

// ---- Cross-impl differential harness (the in-house "cgordon") ----
// Artifacts (corpus, report) live at the repo root next to testdata/ and
// expected/, so the run working dir is the project's parent.
val differentialPropPrefixes = listOf("adapter.", "corpus.dir", "report.dir", "suppression.file", "fuzz.")
fun JavaExec.forwardDifferentialProps() {
    System.getProperties().forEach { k, v ->
        val key = k.toString()
        if (differentialPropPrefixes.any { key == it || key.startsWith(it) }) {
            systemProperty(key, v.toString())
        }
    }
}

tasks.register<JavaExec>("differentialCorpus") {
    group = "verification"
    description = "Generate the cross-impl differential seed corpus under differential/corpus/"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("CorpusGenerator")
    workingDir = project.projectDir.parentFile
}

tasks.register<JavaExec>("differential") {
    group = "verification"
    description = "Run the cross-impl differential harness (Lightbend oracle + go/rs/ts adapters)"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("DifferentialDriver")
    workingDir = project.projectDir.parentFile
    forwardDifferentialProps()
}

tasks.register<JavaExec>("differentialFuzz") {
    group = "verification"
    description = "Fuzz the impls against the Lightbend oracle (-Dfuzz.seed, -Dfuzz.count)"
    dependsOn(tasks.named("classes"))
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("FuzzRunner")
    workingDir = project.projectDir.parentFile
    forwardDifferentialProps()
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}
