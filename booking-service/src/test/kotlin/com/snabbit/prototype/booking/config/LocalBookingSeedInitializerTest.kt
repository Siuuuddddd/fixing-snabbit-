package com.snabbit.prototype.booking.config

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Test
import org.springframework.context.annotation.Profile
import org.springframework.core.annotation.AnnotationUtils

class LocalBookingSeedInitializerTest {
    @Test
    fun `local prototype capacity seed is guarded from production`() {
        val profile = AnnotationUtils.findAnnotation(LocalBookingSeedInitializer::class.java, Profile::class.java)
        assertArrayEquals(arrayOf("local", "dev"), profile?.value)
    }
}
