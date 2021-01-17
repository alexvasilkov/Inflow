package com.alexvasilkov.inflow.se.data.api.response

import com.alexvasilkov.inflow.ext.now
import com.alexvasilkov.inflow.se.model.Profile
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
class ProfileJson(
    val display_name: String,
    val profile_image: String,
    val reputation: Int,
    val link: String
) {

    fun convert() = Profile(
        name = display_name,
        image = profile_image,
        reputation = reputation,
        link = link,
        loadedAt = now()
    )

}
