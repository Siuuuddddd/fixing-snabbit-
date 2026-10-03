package com.snabbit.prototype.booking.controller

import com.snabbit.prototype.booking.dto.response.ServiceOfferingSnapshot

import com.snabbit.prototype.booking.catalog.ServiceCatalogService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/services")
class ServiceCatalogController(private val catalog: ServiceCatalogService) {
    @GetMapping
    fun list(): List<ServiceOfferingSnapshot> = catalog.list()
}
