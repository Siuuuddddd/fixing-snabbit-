package com.snabbit.prototype.notification.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.service.NotificationService
import com.snabbit.prototype.notification.domain.RecipientType
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.reactor.asFlux
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.beans.factory.annotation.Qualifier
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
    private val recipientType: RecipientType,
    private val identityHeader: String,
) : WebSocketHandler {
    override fun handle(session: WebSocketSession): Mono<Void> {
        val recipientId = session.handshakeInfo.headers.getFirst(identityHeader)
            ?.let(UUID::fromString)
            ?: return session.close()
        val afterId = session.handshakeInfo.uri.query
            ?.split('&')
            ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
            ?.firstOrNull { it[0] == "afterNotificationId" }
            ?.get(1)
            ?.toLongOrNull()
            ?: 0
        val messages = service.stream(recipientType, recipientId, afterId)
            .map { notification -> session.textMessage(mapper.writeValueAsString(notification)) }
            .asFlux()
        return session.send(messages)
    }
}

@Configuration
class NotificationWebSocketConfiguration {
    @Bean
    fun customerNotificationWebSocketHandler(service: NotificationService, mapper: ObjectMapper) =
        NotificationWebSocketHandler(service, mapper, RecipientType.CUSTOMER, "X-Authenticated-User-Id")

    @Bean
    fun workerNotificationWebSocketHandler(service: NotificationService, mapper: ObjectMapper) =
        NotificationWebSocketHandler(service, mapper, RecipientType.WORKER, "X-Authenticated-Worker-Id")

    @Bean
    fun notificationWebSocketMapping(
        @Qualifier("customerNotificationWebSocketHandler")
        customerNotificationWebSocketHandler: NotificationWebSocketHandler,
        @Qualifier("workerNotificationWebSocketHandler")
        workerNotificationWebSocketHandler: NotificationWebSocketHandler,
    ): HandlerMapping = SimpleUrlHandlerMapping(
        mapOf(
            "/api/v1/notifications/live" to customerNotificationWebSocketHandler,
            "/api/v1/worker/notifications/live" to workerNotificationWebSocketHandler,
        ),
        Ordered.HIGHEST_PRECEDENCE,
    )

    @Bean fun webSocketHandlerAdapter() = WebSocketHandlerAdapter()
}
