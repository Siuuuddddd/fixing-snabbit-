package com.snabbit.prototype.user.plus

import com.snabbit.prototype.user.dto.response.PlusPurchaseSnapshot

import com.snabbit.prototype.user.dto.request.CreatePlusWalletPurchaseRequest

import com.snabbit.prototype.user.support.CustomerAccessDenied
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class PlusPurchaseServiceTest {
    private val wallet = mockk<PlusWalletClient>()
    private val service = PlusPurchaseService(
        wallet,
        PlusConfigurationProperties(
            Duration.ofMinutes(1),
            mapOf("monthly" to PlusPlanProperties(19_900, 30, 4, 3_000)),
        ),
    )

    @Test
    fun `customer selects only plan code and server sends authoritative terms`() {
        val userId = UUID.randomUUID()
        val walletId = UUID.randomUUID()
        val request = slot<CreatePlusWalletPurchaseRequest>()
        val expected = PlusPurchaseSnapshot(
            UUID.randomUUID(), walletId, userId, "MONTHLY", 19_900, "PENDING", "order_plus",
        )
        every { wallet.create(walletId, "plus-once", capture(request)) } returns expected

        val result = service.purchase(userId, userId, walletId, "monthly", "plus-once")

        assertEquals(expected, result)
        assertEquals(19_900, request.captured.amountMinor)
        assertEquals(30, request.captured.durationDays)
        assertEquals(4, request.captured.passCount)
        assertEquals(3_000, request.captured.passDiscountMinor)
    }

    @Test
    fun `customer cannot purchase Plus for another account`() {
        assertThrows(CustomerAccessDenied::class.java) {
            service.purchase(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "monthly", "key")
        }
        verify(exactly = 0) { wallet.create(any(), any(), any()) }
    }

    @Test
    fun `unknown plan is rejected before money order creation`() {
        val userId = UUID.randomUUID()
        assertThrows(PlusRequestRejected::class.java) {
            service.purchase(userId, userId, UUID.randomUUID(), "invented", "key")
        }
        verify(exactly = 0) { wallet.create(any(), any(), any()) }
    }
}
