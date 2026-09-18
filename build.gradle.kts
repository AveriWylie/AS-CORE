
plugins {java; id("org.springframework.boot") version "3.5.0"; id("io.spring.dependency-management") version "1.1.7" }

group = "ascore"

version = "0.0.1-SNAPSHOT"

java {toolchain {languageVersion = JavaLanguageVersion.of(21)}}
repositories {mavenCentral()}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-websocket")
	implementation("org.springframework.boot:spring-boot-starter-data-mongodb")
	implementation("org.springframework.boot:spring-boot-starter-data-redis")
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-security")
	implementation("net.logstash.logback:logstash-logback-encoder:7.4")
	runtimeOnly("io.micrometer:micrometer-registry-prometheus")

	testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.security:spring-security-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mongodb")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {useJUnitPlatform()}

// V7: bootRun logs as plain text; everything else logs as JSON
tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {systemProperty("spring.profiles.active", "dev")}
