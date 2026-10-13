package com.snabbit.prototype.user.controller

import com.snabbit.prototype.user.dto.response.FavouritesSnapshot
import com.snabbit.prototype.user.dto.response.PublicWorkerProfileSnapshot
import com.snabbit.prototype.user.favourite.FavouriteWorkerService
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/users/{userId}/favourites")
class FavouriteWorkerController(private val favourites: FavouriteWorkerService) {
    @GetMapping
    fun list(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): FavouritesSnapshot = favourites.list(userId.also { requireOwner(authenticatedUserId, it) })

    @PostMapping("/{workerId}")
    @ResponseStatus(HttpStatus.CREATED)
    fun add(
        @PathVariable userId: UUID,
        @PathVariable workerId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): FavouritesSnapshot = favourites.add(userId.also { requireOwner(authenticatedUserId, it) }, workerId)

    @DeleteMapping("/{workerId}")
    fun remove(
        @PathVariable userId: UUID,
        @PathVariable workerId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): FavouritesSnapshot = favourites.remove(userId.also { requireOwner(authenticatedUserId, it) }, workerId)

    @GetMapping("/suggestions")
    fun suggestions(
        @PathVariable userId: UUID,
        @RequestHeader("X-Authenticated-User-Id") authenticatedUserId: UUID,
    ): List<PublicWorkerProfileSnapshot> = favourites.suggestions(
        userId.also { requireOwner(authenticatedUserId, it) },
    )
}
