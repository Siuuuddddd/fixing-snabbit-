package com.snabbit.prototype.user.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("dev-seed")
data class UserDevSeedProperties(
    val enabled: Boolean = false,
    val demoCustomerEmail: String = "",
    val demoCustomerPhone: String = "",
    val demoCustomerName: String = "Demo Customer",
    val plusValidityDays: Int = 365,
    val plusExtendWhenDaysRemain: Int = 30,
) {
    init {
        require(demoCustomerEmail.isBlank() || demoCustomerEmail.contains('@')) {
            "dev-seed.demo-customer-email must be an email address"
        }
        require(demoCustomerPhone.isBlank() || demoCustomerPhone.matches(Regex("^\\+91[6-9][0-9]{9}$"))) {
            "dev-seed.demo-customer-phone must be a +91 Indian mobile number"
        }
        require(demoCustomerPhone.isBlank() || demoCustomerEmail.isNotBlank()) {
            "dev-seed.demo-customer-email is required when demo-customer-phone is configured"
        }
        require(demoCustomerName.isNotBlank() && demoCustomerName.length <= 120) {
            "dev-seed.demo-customer-name must be 1 to 120 characters"
        }
        require(plusValidityDays in 1..365) { "Development Plus validity must be 1 to 365 days" }
        require(plusExtendWhenDaysRemain in 0 until plusValidityDays) {
            "Development Plus extension threshold must be below its validity"
        }
    }
}
