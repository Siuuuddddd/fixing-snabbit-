package com.snabbit.prototype.notification.events

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.snabbit.prototype.notification.domain.enums.RecipientType
import com.snabbit.prototype.notification.dto.response.NotificationTemplate
import java.util.UUID
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.DltHandler
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.annotation.RetryableTopic
import org.springframework.retry.annotation.Backoff
import org.springframework.stereotype.Component

@Component
class NotificationEventListener(
    private val mapper: ObjectMapper,
    private val resolver: EventRecipientResolver,
    private val workerRecipients: WorkerPushRecipientResolver,
    private val templates: NotificationTemplates,
    private val handler: NotificationEventHandler,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    @RetryableTopic(
        attempts = "4",
        backoff = Backoff(delay = 1_000, multiplier = 2.0),
        retryTopicSuffix = "-retry",
        dltTopicSuffix = "-dlt",
    )
    @KafkaListener(
        topics = ["\${notifications.booking-topic}", "\${notifications.wallet-topic}"],
        groupId = "\${notifications.consumer-group}",
    )
    fun consume(rawEvent: String) {
        val event = mapper.readValue(rawEvent, EventEnvelope::class.java)
        when (event.eventType) {
            "ArrivalOtpSent", "ArrivalOtpDeliveryFailed" -> handleArrivalOtp(event)
            "WorkerOffersCreated" -> templates.forWorkerEvent(event)?.let {
                fanOutWorkerOffers(event, it)
            }
            "WorkerOfferExpired" -> {
                handleCustomer(event)
                templates.forWorkerEvent(event)?.let { fanOutStoredJobs(event, it) }
            }
            "BookingConfirmed", "BookingCancelled", "BookingCustomerCancelled", "BookingPlatformCancelled" -> {
                handleCustomer(event)
                templates.forWorkerEvent(event)?.let { fanOutStoredJobs(event, it) }
            }
            "VisitAssigned", "VisitAssignmentChanged" -> {
                handleCustomer(event)
                val workerId = event.payload.path("workerId").asText().takeIf(String::isNotBlank)?.let(UUID::fromString)
                if (workerId != null) templates.forWorkerEvent(event)?.let {
                    handleWorker(event, workerId, it, "visits", listOf(event.payload))
                }
            }
            else -> handleCustomer(event)
        }
    }

    private fun handleArrivalOtp(event: EventEnvelope) {
        val context = resolver.arrivalNotificationContext(event)
        val customerTemplate = if (event.eventType == "ArrivalOtpSent") {
            templates.arrivalOtpSent(context)
        } else {
            templates.arrivalOtpDeliveryFailed()
        }
        handler.handle(event, RecipientType.CUSTOMER, context.customerId, customerTemplate)
        val workerId = event.payload.path("workerId").asText().takeIf(String::isNotBlank)?.let(UUID::fromString)
            ?: return
        templates.forWorkerEvent(event)?.let { template ->
            handleWorker(event, workerId, template, "visits", listOf(event.payload))
        }
    }

    private fun handleCustomer(event: EventEnvelope) {
        val template = templates.forCustomerEvent(event) ?: return
        handler.handle(event, RecipientType.CUSTOMER, resolver.customerId(event), template)
    }

    private fun fanOutWorkerOffers(event: EventEnvelope, template: NotificationTemplate) {
        val offersByWorker = event.payload.path("offers").mapIndexed { visitIndex, offer ->
            offer.deepCopy<com.fasterxml.jackson.databind.node.ObjectNode>().apply {
                if (path("jobId").asText().isBlank()) put("jobId", path("slotHoldId").asText())
                if (path("visitIndex").isMissingNode) put("visitIndex", visitIndex)
            }
        }
            .filter { it.path("workerId").asText().isNotBlank() }
            .groupBy { UUID.fromString(it.path("workerId").asText()) }
        offersByWorker.forEach { (workerId, workerOffers) ->
            handleWorker(event, workerId, template, "offers", workerOffers)
        }
    }

    private fun fanOutStoredJobs(event: EventEnvelope, template: NotificationTemplate) {
        val bookingId = UUID.fromString(event.payload.path("bookingId").asText())
        workerRecipients.targets(bookingId).forEach { target ->
            handleWorker(event, target.workerId, template, "jobs", target.jobs)
        }
    }

    private fun handleWorker(
        event: EventEnvelope,
        workerId: UUID,
        template: NotificationTemplate,
        entriesField: String,
        workerEntries: List<JsonNode>,
    ) {
            val recipientEventId = UUID.nameUUIDFromBytes("${event.eventId}:$workerId".toByteArray(Charsets.UTF_8))
            val recipientPayload = mapper.createObjectNode().apply {
                set<com.fasterxml.jackson.databind.JsonNode>("bookingId", event.payload.path("bookingId"))
                set<com.fasterxml.jackson.databind.JsonNode>(entriesField, mapper.valueToTree(workerEntries))
            }
            handler.handle(
                event.copy(eventId = recipientEventId, payload = recipientPayload),
                RecipientType.WORKER,
                workerId,
                template,
            )
    }

    @DltHandler
    fun deadLetter(rawEvent: String) {
        logger.error("Notification event moved to dead-letter topic: {}", rawEvent.take(500))
    }
}
