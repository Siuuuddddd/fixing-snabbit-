package com.snabbit.prototype.mobile.domain.home

import com.snabbit.prototype.mobile.domain.address.AddressRepository

class GetSelectedAddressUseCase(
    private val addressRepository: AddressRepository,
) {
    suspend operator fun invoke(): SelectedHomeAddress? {
        val addresses = addressRepository.addresses()
        return addresses.firstOrNull { it.verified } ?: addresses.firstOrNull()
    }
}
