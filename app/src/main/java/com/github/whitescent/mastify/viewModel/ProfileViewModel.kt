/*
 * Copyright 2023 WhiteScent
 *
 * This file is a part of Mastify.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation; either version 3 of the
 * License, or (at your option) any later version.
 *
 * Mastify is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with Mastify; if not,
 * see <http://www.gnu.org/licenses>.
 */

package com.github.whitescent.mastify.viewModel

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.github.whitescent.R
import com.github.whitescent.mastify.data.model.StatusBackResult
import com.github.whitescent.mastify.data.model.ui.StatusCommonListData
import com.github.whitescent.mastify.data.model.ui.StatusUiData
import com.github.whitescent.mastify.data.repository.StatusRepository
import com.github.whitescent.mastify.database.AppDatabase
import com.github.whitescent.mastify.domain.StatusActionHandler
import com.github.whitescent.mastify.extensions.updateStatusActionData
import com.github.whitescent.mastify.mapper.status.toUiData
import com.github.whitescent.mastify.network.MastodonApi
import com.github.whitescent.mastify.network.model.account.Account
import com.github.whitescent.mastify.network.model.status.Status
import com.github.whitescent.mastify.paging.LoadState
import com.github.whitescent.mastify.paging.Paginator
import com.github.whitescent.mastify.screen.navArgs
import com.github.whitescent.mastify.screen.profile.ProfileNavArgs
import com.github.whitescent.mastify.utils.StatusAction
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  db: AppDatabase,
  private val statusActionHandler: StatusActionHandler,
  private val api: MastodonApi,
  private val repository: StatusRepository,
) : ViewModel() {

  private val navArgs: ProfileNavArgs = savedStateHandle.navArgs()
  private val accountDao = db.accountDao()

  val snackBarFlow = statusActionHandler.snackBarFlow

  private var profileStatusFlow = MutableStateFlow(StatusCommonListData<StatusUiData>())
  private var profileStatusWithReplyFlow = MutableStateFlow(StatusCommonListData<StatusUiData>())
  private var profileStatusWithMediaFlow = MutableStateFlow(StatusCommonListData<StatusUiData>())

  private var currentProfileKindFlow = MutableStateFlow(ProfileKind.Status)
  val currentProfileKind = currentProfileKindFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = ProfileKind.Status
    )

  var uiState by mutableStateOf(ProfileUiState(account = navArgs.account))
    private set

  val profileStatus = profileStatusFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = StatusCommonListData()
    )

  val profileStatusWithReply = profileStatusWithReplyFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = StatusCommonListData()
    )

  val profileStatusWithMedia = profileStatusWithMediaFlow
    .stateIn(
      scope = viewModelScope,
      started = SharingStarted.Eagerly,
      initialValue = StatusCommonListData()
    )

  val statusPager = Paginator(
    refreshKey = null,
    getAppendKey = {
      profileStatusFlow.value.timeline.lastOrNull()?.id
    },
    onLoadUpdated = {
      profileStatusFlow.value = profileStatusFlow.value.copy(loadState = it)
    },
    onError = { it?.printStackTrace() },
    onRequest = { nextPage ->
      repository.getAccountStatus(
        onlyMedia = false,
        excludeReplies = true,
        maxId = nextPage,
        accountId = uiState.account.id
      )
    },
    onSuccess = { state, item ->
      when (state) {
        LoadState.Append -> {
          val timeline = profileStatusFlow.value.timeline
          profileStatusFlow.emit(
            profileStatusFlow.value.copy(
              timeline = timeline + item.toUiData(),
              endReached = item.isEmpty()
            )
          )
        }
        LoadState.Refresh -> {
          profileStatusFlow.emit(
            profileStatusFlow.value.copy(timeline = item.toUiData(), endReached = item.isEmpty())
          )
        }
        else -> Unit
      }
    }
  )

  val statusWithReplyPager = Paginator(
    refreshKey = null,
    getAppendKey = {
      profileStatusWithReplyFlow.value.timeline.lastOrNull()?.id
    },
    onLoadUpdated = {
      profileStatusWithReplyFlow.value = profileStatusWithReplyFlow.value.copy(loadState = it)
    },
    onError = { it?.printStackTrace() },
    onRequest = { nextPage ->
      repository.getAccountStatus(
        excludeReplies = false,
        maxId = nextPage,
        accountId = uiState.account.id
      )
    },
    onSuccess = { state, item ->
      when (state) {
        LoadState.Append -> {
          val timeline = profileStatusWithReplyFlow.value.timeline
          profileStatusWithReplyFlow.emit(
            profileStatusWithReplyFlow.value.copy(
              timeline = (timeline + item.toUiData()).distinctBy { it.id },
              endReached = item.isEmpty()
            )
          )
        }
        LoadState.Refresh -> {
          profileStatusWithReplyFlow.emit(
            profileStatusWithReplyFlow.value.copy(
              timeline = item.toUiData().distinctBy { it.id },
              endReached = item.isEmpty()
            )
          )
        }
        else -> Unit
      }
    }
  )

  val statusWithMediaPager = Paginator(
    refreshKey = null,
    getAppendKey = {
      profileStatusWithMediaFlow.value.timeline.lastOrNull()?.id
    },
    onLoadUpdated = {
      profileStatusWithMediaFlow.value = profileStatusWithMediaFlow.value.copy(loadState = it)
    },
    onError = { it?.printStackTrace() },
    onRequest = { nextPage ->
      repository.getAccountStatus(
        onlyMedia = true,
        maxId = nextPage,
        accountId = uiState.account.id
      )
    },
    onSuccess = { state, item ->
      when (state) {
        LoadState.Append -> {
          val timeline = profileStatusWithMediaFlow.value.timeline
          profileStatusWithMediaFlow.emit(
            profileStatusWithMediaFlow.value.copy(
              timeline = timeline + item.toUiData(),
              endReached = item.isEmpty()
            )
          )
        }
        LoadState.Refresh -> {
          profileStatusWithMediaFlow.emit(
            profileStatusWithMediaFlow.value.copy(
              timeline = item.toUiData(),
              endReached = item.isEmpty()
            )
          )
        }
        else -> Unit
      }
    }
  )

  init {
    viewModelScope.launch {
      statusPager.refresh()
      uiState = uiState.copy(
        isSelf = navArgs.account.id == accountDao.getActiveAccount()!!.accountId
      )
      getRelationship(navArgs.account.id)
      fetchAccount(navArgs.account.id)
      launch {
        currentProfileKindFlow.collect {
          when (it) {
            ProfileKind.StatusWithReply ->
              if (profileStatusWithReplyFlow.value.timeline.isEmpty()) statusWithReplyPager.refresh()
            ProfileKind.StatusWithMedia ->
              if (profileStatusWithMediaFlow.value.timeline.isEmpty()) statusWithMediaPager.refresh()
            else -> Unit
          }
        }
      }
    }
  }

  fun appendProfileKind(kind: ProfileKind) {
    viewModelScope.launch {
      when (kind) {
        ProfileKind.Status -> statusPager.append()
        ProfileKind.StatusWithReply -> statusWithReplyPager.append()
        ProfileKind.StatusWithMedia -> statusWithMediaPager.append()
      }
    }
  }

  fun refreshProfileKind(kind: ProfileKind) {
    viewModelScope.launch {
      when (kind) {
        ProfileKind.Status -> statusPager.refresh()
        ProfileKind.StatusWithReply -> statusWithReplyPager.refresh()
        ProfileKind.StatusWithMedia -> statusWithMediaPager.refresh()
      }
    }
  }

  fun syncProfileTab(page: Int) {
    currentProfileKindFlow.value = ProfileKind.entries.toTypedArray()[page]
  }

  fun onStatusAction(action: StatusAction, context: Context, status: Status) {
    viewModelScope.launch(Dispatchers.IO) {
      profileStatusFlow.update {
        it.copy(
          timeline = StatusActionHandler.updateStatusListActions(it.timeline, action, status.id)
        )
      }
      profileStatusWithReplyFlow.update {
        it.copy(
          timeline = StatusActionHandler.updateStatusListActions(it.timeline, action, status.id)
        )
      }
      profileStatusWithMediaFlow.update {
        it.copy(
          timeline = StatusActionHandler.updateStatusListActions(it.timeline, action, status.id)
        )
      }
      statusActionHandler.onStatusAction(action, context)
    }
  }

  fun updateStatusFromDetailScreen(newStatus: StatusBackResult) {
    val status = profileStatusFlow.value.timeline
    val statusWithReply = profileStatusWithReplyFlow.value.timeline
    val statusWithMedia = profileStatusWithMediaFlow.value.timeline
    profileStatusFlow.value = profileStatusFlow.value.copy(
      timeline = status.updateStatusActionData(newStatus)
    )
    profileStatusWithReplyFlow.value = profileStatusWithReplyFlow.value.copy(
      timeline = statusWithReply.updateStatusActionData(newStatus)
    )
    profileStatusWithMediaFlow.value = profileStatusWithMediaFlow.value.copy(
      timeline = statusWithMedia.updateStatusActionData(newStatus)
    )
  }

  private suspend fun fetchAccount(accountId: String) {
    api.account(accountId).fold(
      {
        uiState = uiState.copy(account = it)
      },
      {
        it.printStackTrace()
      }
    )
  }

  private suspend fun getRelationship(accountId: String) {
    api.relationships(listOf(accountId)).fold(
      {
        uiState = uiState.copy(isFollowing = it.first().following)
      },
      {
        it.printStackTrace()
      }
    )
  }
}

data class ProfileUiState(
  val account: Account,
  val isSelf: Boolean? = null,
  val isFollowing: Boolean? = null,
)

enum class ProfileKind(
  @StringRes val stringRes: Int
) {
  Status(R.string.post_title),
  StatusWithReply(R.string.reply_title),
  StatusWithMedia(R.string.media_title)
}
