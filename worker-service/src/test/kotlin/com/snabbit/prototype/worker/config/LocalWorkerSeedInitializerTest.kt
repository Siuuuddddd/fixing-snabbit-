package com.snabbit.prototype.worker.config

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AnnotationUtils

class LocalWorkerSeedInitializerTest {
    @Test
    fun `local prototype seed is guarded from production`() {
        val profile = AnnotationUtils.findAnnotation(LocalWorkerSeedInitializer::class.java, Profile::class.java)
        assertArrayEquals(arrayOf("local"), profile?.value)
    }
}
