package com.snabbit.prototype.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevSeedPhoneSafetyTest {
    @Test
    fun `development seed sources contain no real Indian mobile number`() {
        val root = generateSequence(Paths.get("").toAbsolutePath()) { it.parent }
            .first { Files.exists(it.resolve("settings.gradle.kts")) }
        val candidates = sequenceOf(
            root.resolve(".env.example"),
            root.resolve("DEPLOY.md"),
        ) + listOf("platform-common", "user-service", "worker-service", "booking-service").asSequence()
            .flatMap { module ->
                Files.walk(root.resolve(module).resolve("src")).use { paths ->
                    paths.asSequence()
                        .filter(Path::isRegularFile)
                        .filter { path -> path.fileName.toString().contains("Seed", ignoreCase = true) }
                        .toList()
                        .asSequence()
                }
            }
        val realIndianMobile = Regex("\\+91[6-9]\\d{9}")
        val violations = candidates.filter { Files.exists(it) }
            .filter { realIndianMobile.containsMatchIn(Files.readString(it)) }
            .map(root::relativize)
            .toList()

        assertTrue(violations.isEmpty(), "Real-looking mobile numbers found in dev seed files: $violations")
    }
}
