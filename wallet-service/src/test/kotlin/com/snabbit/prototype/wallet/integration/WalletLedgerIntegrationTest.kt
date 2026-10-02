package com.snabbit.prototype.wallet.integration

import com.snabbit.prototype.wallet.application.RazorpayWebhookService
import com.snabbit.prototype.wallet.application.WalletService
import com.snabbit.prototype.wallet.booking.BookingPaymentQuote
import com.snabbit.prototype.wallet.booking.BookingQuoteClient
import com.snabbit.prototype.wallet.support.InvalidWebhook
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.context.bean.override.mockito.MockitoBean
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

@SpringBootTest(
    properties = [
        "wallet.razorpay.mode=fake",
        "wallet.razorpay.webhook-secret=integration-secret",
        "wallet.refund-retry-delay=PT24H",
        "eventing.outbox-enabled=false",
        "spring.kafka.listener.auto-startup=false",
    ],
)
@Testcontainers(disabledWithoutDocker = true)
class WalletLedgerIntegrationTest {
    @Autowired
    private lateinit var walletService: WalletService

    @Autowired
    private lateinit var webhookService: RazorpayWebhookService

    @Autowired
    private lateinit var jdbc: JdbcTemplate

    @MockitoBean
    private lateinit var bookingQuotes: BookingQuoteClient

    private val walletId = UUID.fromString("50000000-0000-0000-0000-000000000099")
    private val customerId = UUID.fromString("40000000-0000-0000-0000-000000000099")
    private var quoteAmountMinor: Long = 1

    @BeforeEach
    fun resetDatabase() {
        Mockito.`when`(bookingQuotes.get(ArgumentMatchers.any(UUID::class.java))).thenAnswer { invocation ->
            BookingPaymentQuote(
                bookingId = invocation.getArgument(0),
                customerId = customerId,
                status = "AWAITING_PAYMENT",
                quotedTotalMinor = quoteAmountMinor,
                currency = "INR",
                holdExpiresAt = Instant.now().plusSeconds(300),
            )
        }
        jdbc.execute(
            """
            TRUNCATE payment_refund, booking_payment, wallet_reservation, top_up_order,
                wallet_ledger_entry, processed_webhook, wallet_account, outbox_event CASCADE
            """.trimIndent(),
        )
        jdbc.update(
            "INSERT INTO wallet_account (id, customer_id, currency, created_at, version) VALUES (?, ?, 'INR', ?, 0)",
            walletId,
            customerId,
            Instant.now(),
        )
    }

    @Test
    fun `top-up credits only after verified webhook and remains idempotent`() {
        val topUp = walletService.addMoney(walletId, 10_000, "topup-integration")
        assertEquals(0, walletService.balance(walletId).availableMinor)

        val first = deliverCapture("evt_topup_1", topUp.providerOrderId!!, "pay_topup_1", 10_000)
        val replay = deliverCapture("evt_topup_1", topUp.providerOrderId!!, "pay_topup_1", 10_000)
        val secondEvent = deliverCapture("evt_topup_2", topUp.providerOrderId!!, "pay_topup_1", 10_000)

        assertEquals("TOP_UP_CREDITED", first)
        assertEquals("DUPLICATE", replay)
        assertEquals("ALREADY_PROCESSED", secondEvent)
        assertEquals(10_000, walletService.balance(walletId).availableMinor)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM wallet_ledger_entry", Long::class.java))
    }

    @Test
    fun `Plus purchase activates only through verified capture without changing wallet balance`() {
        val purchase = walletService.createPlusPurchase(
            walletId, customerId, "MONTHLY", 19_900, 30, 4, 3_000, null, "plus-integration",
        )
        assertEquals("PENDING", purchase.status)
        assertEquals(0, walletService.balance(walletId).availableMinor)

        val first = deliverCapture("evt_plus_1", purchase.providerOrderId!!, "pay_plus_1", 19_900)
        val replay = deliverCapture("evt_plus_1", purchase.providerOrderId!!, "pay_plus_1", 19_900)

        assertEquals("PLUS_SUBSCRIPTION_PURCHASED", first)
        assertEquals("DUPLICATE", replay)
        assertEquals(0, walletService.balance(walletId).availableMinor)
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM wallet_ledger_entry", Long::class.java))
        assertEquals(
            1,
            jdbc.queryForObject(
                "SELECT count(*) FROM outbox_event WHERE event_type = 'PlusSubscriptionPurchased'",
                Long::class.java,
            ),
        )
    }

    @Test
    fun `split payment captures reservation and full refund restores wallet portion`() {
        creditWallet(10_000)
        val bookingId = UUID.randomUUID()
        quoteAmountMinor = 15_000
        val payment = walletService.createBookingPayment(
            walletId,
            bookingId,
            useWallet = true,
            idempotencyKey = "booking-split",
        )

        assertEquals(10_000, payment.walletAmountMinor)
        assertEquals(5_000, payment.onlineAmountMinor)
        assertEquals(0, walletService.balance(walletId).availableMinor)

        deliverCapture("evt_booking", payment.providerOrderId!!, "pay_booking", 5_000)
        val capturedBalance = walletService.balance(walletId)
        assertEquals(0, capturedBalance.availableMinor)
        assertEquals(0, capturedBalance.reservedMinor)
        assertEquals(10_000, capturedBalance.totalSpentMinor)

        val refund = walletService.refund(payment.paymentId, "refund-split")

        assertEquals("COMPLETED", refund.status)
        assertEquals(10_000, walletService.balance(walletId).availableMinor)
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM payment_refund", Long::class.java))
    }

    @Test
    fun `concurrent payments cannot reserve more than available balance`() {
        creditWallet(10_000)
        quoteAmountMinor = 8_000
        val ready = CountDownLatch(2)
        val start = CountDownLatch(1)
        val executor = Executors.newFixedThreadPool(2)
        val tasks = (1..2).map { index ->
            Callable {
                ready.countDown()
                start.await()
                walletService.createBookingPayment(
                    walletId,
                    UUID.randomUUID(),
                    useWallet = true,
                    idempotencyKey = "concurrent-$index",
                )
            }
        }
        val futures = tasks.map(executor::submit)
        ready.await()
        start.countDown()
        val payments = futures.map { it.get() }
        executor.shutdown()

        assertEquals(10_000, payments.sumOf { it.walletAmountMinor })
        assertEquals(10_000, walletService.balance(walletId).reservedMinor)
        assertEquals(0, walletService.balance(walletId).availableMinor)
    }

    @Test
    fun `invalid webhook signature never credits wallet`() {
        val topUp = walletService.addMoney(walletId, 5_000, "bad-signature")
        val body = captureBody(topUp.providerOrderId!!, "pay_bad", 5_000)

        assertThrows(InvalidWebhook::class.java) {
            webhookService.handle(body, "invalid", "evt_bad")
        }
        assertEquals(0, walletService.balance(walletId).availableMinor)
    }

    @Test
    fun `capture after cancellation is refunded instead of completing payment`() {
        quoteAmountMinor = 5_000
        val payment = walletService.createBookingPayment(
            walletId,
            UUID.randomUUID(),
            useWallet = false,
            idempotencyKey = "late-capture",
        )
        walletService.cancelBookingPayment(payment.paymentId)

        val result = deliverCapture("evt_late", payment.providerOrderId!!, "pay_late", 5_000)

        assertEquals("REFUND_REQUIRED", result)
        assertEquals(
            "COMPLETED",
            jdbc.queryForObject("SELECT status FROM payment_refund", String::class.java),
        )
        assertEquals(
            "REFUNDED",
            jdbc.queryForObject("SELECT status FROM booking_payment WHERE id = ?", String::class.java, payment.paymentId),
        )
    }

    private fun creditWallet(amountMinor: Long) {
        val topUp = walletService.addMoney(walletId, amountMinor, "seed-credit-$amountMinor")
        deliverCapture("evt_seed_$amountMinor", topUp.providerOrderId!!, "pay_seed_$amountMinor", amountMinor)
    }

    private fun deliverCapture(eventId: String, orderId: String, paymentId: String, amountMinor: Long): String {
        val body = captureBody(orderId, paymentId, amountMinor)
        return webhookService.handle(body, signature(body), eventId)
    }

    private fun captureBody(orderId: String, paymentId: String, amountMinor: Long): ByteArray =
        """
        {
          "event": "payment.captured",
          "payload": {
            "payment": {
              "entity": {
                "id": "$paymentId",
                "order_id": "$orderId",
                "amount": $amountMinor
              }
            }
          }
        }
        """.trimIndent().toByteArray()

    private fun signature(body: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec("integration-secret".toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(body).joinToString("") { "%02x".format(it) }
    }

    class KPostgreSQLContainer(imageName: String) : PostgreSQLContainer<KPostgreSQLContainer>(imageName)

    companion object {
        @Container
        @JvmStatic
        val postgres = KPostgreSQLContainer("postgres:17-alpine")

        @DynamicPropertySource
        @JvmStatic
        fun databaseProperties(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }
}
