package com.kyberswap.android.data.api.alert


import com.google.gson.annotations.SerializedName
import java.math.BigDecimal

data class AlertEntity(
    @SerializedName("id")
    val id: Int = 0,
    @SerializedName("base")
    val base: String = "",
    @SerializedName("symbol")
    val symbol: String = "",
    @SerializedName("alert_type")
    val alertType: String = "",
    @SerializedName("alert_price")
    val alertPrice: BigDecimal = BigDecimal.ZERO,
    @SerializedName("created_at_price")
    val createdAtPrice: BigDecimal = BigDecimal.ZERO,
    @SerializedName("percent_change")
    val percentChange: BigDecimal = BigDecimal.ZERO,
    @SerializedName("is_above")
    val isAbove: Boolean = false,
    @SerializedName("status")
    val status: String = "",
    @SerializedName("created_at")
    val createdAt: String = "",
    @SerializedName("updated_at")
    val updatedAt: String = "",
    @SerializedName("triggered_at")
    val triggeredAt: String? = "",
    @SerializedName("filled_at")
    val filledAt: String? = ""
)