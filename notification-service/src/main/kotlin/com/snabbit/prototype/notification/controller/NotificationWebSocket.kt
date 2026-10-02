package com.snabbit.prototype.notification.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.service.NotificationService
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.Ordered
import org.springframework.web.reactive.HandlerMapping
import org.springframework.web.reactive.handler.SimpleUrlHandlerMapping
import org.springframework.web.reactive.socket.WebSocketHandler
import org.springframework.web.reactive.socket.WebSocketSession
import org.springframework.web.reactive.socket.server.support.WebSocketHandlerAdapter
import reactor.core.publisher.Mono
import java.util.UUID

class NotificationWebSocketHandler(
    private val service: NotificationService,
    private val mapper: ObjectMapper,
) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        val customerId = session.handshakeInfo.headers.getFirst("X-Authenticated-User-Id")
            ?.let(UUID::fromString)
            ?: return session.close()
        val afterId = session.handshakeInfo.uri.query
            ?.split('&')
            ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            ?.firstOrNull { it[0] == "afterNotificationId" }
            ?.get(1)
            ?.toLongOrNull()
            ?: 0
        val messages = service.stream(customerId, afterId)
            .map { notification -> session.textMessage(mapper.writeValueAsString(notification)) }
            .asFlux()
        return session.send(messages)
    }
}

@Configuration
class NotificationWebSocketConfiguration {
    @Bean
    fun notificationWebSocketHandler(service: NotificationService, mapper: ObjectMapper) =
        NotificationWebSocketHandler(service, mapper)

    @Bean
    fun notificationWebSocketMapping(handler: NotificationWebSocketHandler): HandlerMapping =
        SimpleUrlHandlerMapping(mapOf("/api/v1/notifications/live" to handler), Ordered.HIGHEST_PRECEDENCE)

    @Bean fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()
}
