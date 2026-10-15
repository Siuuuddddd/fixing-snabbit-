package com.snabbit.prototype.mobile.ui.emailauth

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SensitiveAuthLoggingTest {
    @Test fun `email auth implementation has no direct logging calls`() {
        val roots = listOf(
            File("src/main/java/com/snabbit/prototype/mobile/ui/emailauth"),
            File("src/main/java/com/snabbit/prototype/mobile/data/auth/DefaultEmailAuthRepository.kt"),
            File("src/main/java/com/snabbit/prototype/mobile/data/auth/remote/email"),
        )
        val sources = roots.flatMap { root ->
            when {
                root.isDirectory -> root.walkTopDown().filter { it.extension == "kt" }.toList()
                root.isFile -> listOf(root)
                else -> emptyList()
            }
        }
        assertTrue("Expected email-auth sources", sources.isNotEmpty())
        sources.forEach { source ->
            val text = source.readText()
            assertFalse("Direct Android logging in ${source.name}", Regex("\\bLog\\.[a-zA-Z]+\\(").containsMatchIn(text))
            assertFalse("Console logging in ${source.name}", Regex("\\bprintln\\(").containsMatchIn(text))
        }
    }
}
