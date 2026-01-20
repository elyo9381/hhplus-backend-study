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
    implementation("org.springframework.boot:spring-boot-starter-data-redis")

    // Redisson
    implementation("org.redisson:redisson-spring-boot-starter:3.24.3")

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
	
	// 테스트 순차 실행
	maxParallelForks = 1
	
	// JVM 메모리 설정
	jvmArgs("-Xmx1024m")
}

// 단위 테스트만 실행 (빠름, Mock 기반)
tasks.register<Test>("unitTest") {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
	
	// DB 필요한 테스트 제외
	exclude("**/integration/**")
	exclude("**/outbox/**")
	exclude("**/*IntegrationTest*")
	exclude("**/*ConcurrencyTest*")
	exclude("**/*RepositoryTest*")
	exclude("**/*LockTest*")
	exclude("**/SimpleSpringBootTest*")
	exclude("**/ServerApplicationTests*")
}

// 통합 테스트만 실행 (Testcontainers 사용)
tasks.register<Test>("integrationTest") {
	useJUnitPlatform()
	systemProperty("user.timezone", "UTC")
	
	// DB 필요한 테스트 포함
	include("**/integration/**")
	include("**/outbox/**")
	include("**/*IntegrationTest*")
	include("**/*ConcurrencyTest*")
	include("**/*RepositoryTest*")
	include("**/SimpleSpringBootTest*")
	include("**/ServerApplicationTests*")
}
