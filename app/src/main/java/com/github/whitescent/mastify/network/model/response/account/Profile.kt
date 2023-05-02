package com.github.whitescent.mastify.network.model.response.account

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Profile(
  val id: String,
  @SerialName("display_name") val name: String,
  val note: String,
  val url: String,
  val avatar: String,
  val header: String,
  @SerialName("followers_count") val followersCount: Long,
  @SerialName("following_count") val followingCount: Long,
  @SerialName("statuses_count") val statusesCount: Long,
  @SerialName("created_at") val createdAt: String,
  val source: Source,
  val fields: List<Fields>
)

@Serializable
data class Source(
  val note: String,
  val fields: List<Fields>
)

@Serializable
data class Fields(
  val name: String,
  val value: String,
  @SerialName("verified_at") val verifiedAt: String?
)