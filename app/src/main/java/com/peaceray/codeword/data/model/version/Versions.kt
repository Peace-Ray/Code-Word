package com.peaceray.codeword.data.model.version

import android.os.Parcelable
import com.google.gson.annotations.SerializedName
import kotlinx.parcelize.Parcelize

@Parcelize
data class Versions(
    @SerializedName("seed") val seed: Int,
    @SerializedName("android") val application: Int
): Parcelable

@Parcelize
data class SupportedVersions(
    val minimum: Versions,
    val current: Versions,
    val lifespan: Long
): Parcelable