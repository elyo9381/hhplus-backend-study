plugins {
	java
	id("org.springframework.boot") version "3.4.1"
	id("io.spring.dependency-management") version "1.1.7"
}

fun getGitHash(): String {
	return providers.exec {
		commandLine("git", "rev-parse", "--short", "HEAD")
	}.standardOutput.asText.get().trim()
}

group = "kr.hhplus.be"
version = getGitHash()

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencyManagement {
	imports {
		mavenBom("org.springframework.cloud:spring-cloud-dependencies:2024.0.0")
	}
}

dependencies {
    // Spring
	implementation("org.springframework.boot:spring-boot-starter-actuator")
	implementation("org.springframework.boot:spring-boot-starter-data-jpa")
	implementation("org.springframework.boot:spring-boot-starter-web")
	implementation("org.springframework.boot:spring-boot-starter-webflux") // WebClient

    // Lombok
	compileOnly("org.projectlombok:lombok")
	annotationProcessor("org.projectlombok:lombok")

    // DB
	runtimeOnly("com.mysql:mysql-connector-j")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
	testImplementation("org.springframework.boot:spring-boot-testcontainers")
	testImplementation("org.testcontainers:junit-jupiter")
	testImplementation("org.testcontainers:mysql")
	testImplementation("com.h2database:h2")  // 단위 테스트용
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
	
	// 테스트 병렬 실행 (CPU 코어 수의 절반)
	maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
	
	// JVM 메모리 설정
	jvmArgs("-Xmx1024m")
}

// 단위 테스트만 실행 (빠름, H2 사용)
tasks.register<Test>("unitTest") {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
	
	// 통합 테스트 제외
	exclude("**/integration/**")
	exclude("**/outbox/**")  // Outbox 테스트는 통합 테스트
	exclude("**/*IntegrationTest*")
	exclude("**/*ConcurrencyTest*")
}

// 통합 테스트만 실행 (Testcontainers 사용)
tasks.register<Test>("integrationTest") {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
	
	// 통합 테스트만 포함
	include("**/integration/**")
	include("**/outbox/**")
	include("**/*IntegrationTest*")
	include("**/*ConcurrencyTest*")
}
