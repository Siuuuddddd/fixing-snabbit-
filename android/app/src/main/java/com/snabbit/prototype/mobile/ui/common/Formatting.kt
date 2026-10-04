package com.snabbit.prototype.mobile.ui.common

import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

fun formatInrMinor(amountMinor: Long): String =
    NumberFormat.getCurrencyInstance(Locale.forLanguageTag("en-IN")).format(BigDecimal.valueOf(amountMinor, 2))
