package com.snabbit.prototype.booking.api

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import com.snabbit.prototype.booking.catalog.ServiceOfferingSnapshot
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services")
class ServiceCatalogController(private val catalog: ServiceCatalogService) {
    @GetMapping
    fun list(): List<ServiceOfferingSnapshot> = catalog.list()
}
