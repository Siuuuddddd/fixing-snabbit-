package com.snabbit.prototype.support

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class SupportServiceApplication

fun main(args: Array<String>) {
    runApplication<SupportServiceApplication>(*args)
}
