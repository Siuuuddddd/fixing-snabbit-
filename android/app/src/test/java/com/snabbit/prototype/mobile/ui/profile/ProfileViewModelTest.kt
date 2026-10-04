package com.snabbit.prototype.mobile.ui.profile

import com.snabbit.prototype.mobile.domain.profile.ProfileRepository
import com.snabbit.prototype.mobile.domain.profile.ProfileSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileViewModelTest {
    @Test fun `loads composed profile and signs out`() = runTest {
        val repository = FakeProfileRepository()
        val viewModel = ProfileViewModel(repository, StandardTestDispatcher(testScheduler))
        runCurrent()
        assertEquals(2, viewModel.uiState.value.profile?.savedAddressCount)
        viewModel.signOut()
        runCurrent()
        assertTrue(repository.signedOut)
    }
}

private class FakeProfileRepository : ProfileRepository {
    var signedOut = false
    override suspend fun summary() = ProfileSummary("user", "CUSTOMER", 2, true, true, 3)
    override suspend fun signOut() { signedOut = true }
}
