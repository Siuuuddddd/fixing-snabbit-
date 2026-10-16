package com.snabbit.prototype.mobile.ui.login

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class LoginContentSafetyTest {
    @Test
    fun `customer login source excludes the disallowed brand term`() {
        val term = charArrayOf(90.toChar(), 111.toChar(), 109.toChar(), 97.toChar(), 116.toChar(), 111.toChar())
            .concatToString()
        val sourceRoot = listOf(File("src/main"), File("app/src/main")).first(File::exists)
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension in setOf("kt", "xml") }
            .filter { it.readText().contains(term, ignoreCase = true) }
            .map { it.path }
            .toList()

        assertTrue("Disallowed brand term found in: $offenders", offenders.isEmpty())
    }
}
