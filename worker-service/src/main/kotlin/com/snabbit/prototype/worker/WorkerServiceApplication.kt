package com.snabbit.prototype.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WorkerServiceApplication

fun main(args: Array<String>) {
    runApplication<WorkerServiceApplication>(*args)
}
