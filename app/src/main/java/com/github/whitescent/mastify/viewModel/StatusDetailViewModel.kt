package com.github.whitescent.mastify.viewModel

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.connyduck.calladapter.networkresult.fold
import com.github.whitescent.mastify.data.model.ui.StatusUiData
import com.github.whitescent.mastify.mapper.status.toUiData
import com.github.whitescent.mastify.network.MastodonApi
import com.github.whitescent.mastify.network.model.status.Status
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.Continue
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.End
import com.github.whitescent.mastify.network.model.status.Status.ReplyChainType.Start
import com.github.whitescent.mastify.screen.navArgs
import com.github.whitescent.mastify.screen.other.StatusDetailNavArgs
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StatusDetailViewModel @Inject constructor(
  savedStateHandle: SavedStateHandle,
  private val api: MastodonApi
) : ViewModel() {

  private var isInitialLoad = false

  val navArgs: StatusDetailNavArgs = savedStateHandle.navArgs()

  private val _replyText = MutableStateFlow("")
  val replyText = _replyText.asStateFlow()

  var uiState by mutableStateOf(StatusDetailUiState())
    private set

  fun favoriteStatus(id: String) = viewModelScope.launch {
    api.favouriteStatus(id)
  }

  fun unfavoriteStatus(id: String) = viewModelScope.launch {
    api.unfavouriteStatus(id)
  }

  init {
    uiState = uiState.copy(loading = true)
    viewModelScope.launch {
      api.statusContext(navArgs.status.actionableId).fold(
        {
          uiState = uiState.copy(
            loading = false,
            ancestors = markAncestors(it.ancestors),
            descendants = markDescendants(it.descendants)
          )
          isInitialLoad = true
        },
        {
          it.printStackTrace()
          uiState = uiState.copy(loading = false, loadError = true)
        }
      )
    }
  }

  fun updateText(text: String) = _replyText.update { text }

  private fun markAncestors(ancestors: List<Status>): ImmutableList<StatusUiData> {
    if (ancestors.isEmpty()) return persistentListOf()
    val result = ancestors.toMutableList()
    ancestors.forEachIndexed { index, status ->
      when (index) {
        0 -> result[index] = status.copy(replyChainType = Start)
        in 1..ancestors.lastIndex -> result[index] = status.copy(replyChainType = Continue)
      }
    }
    return result.toUiData().toImmutableList()
  }

  private fun markDescendants(descendants: List<Status>): ImmutableList<StatusUiData> {
    if (descendants.isEmpty() || descendants.size == 1)
      return descendants.toUiData().toImmutableList()
    val result = descendants.toMutableList()
    descendants.forEachIndexed { index, status ->
      when {
        index == 0 && descendants[1].inReplyToId == status.id ->
          result[index] = status.copy(replyChainType = Start)
        index == descendants.lastIndex && status.inReplyToId == descendants[index - 1].id ->
          result[index] = status.copy(replyChainType = End)
      }
      if (index > 0 && index < descendants.lastIndex) {
        when {
          descendants[index + 1].inReplyToId == status.id &&
            status.inReplyToId != descendants[index - 1].id -> {
            result[index] = status.copy(replyChainType = Start)
          }
          status.inReplyToId == descendants[index - 1].id &&
            descendants[index + 1].inReplyToId == status.id -> {
            result[index] = status.copy(replyChainType = Continue)
          }
          status.inReplyToId == descendants[index - 1].id &&
            descendants[index + 1].inReplyToId != status.id -> {
            result[index] = status.copy(replyChainType = End)
          }
        }
      }
    }
    return result.toUiData().toImmutableList()
  }
}

@Immutable
data class StatusDetailUiState(
  val loading: Boolean = false,
  val ancestors: ImmutableList<StatusUiData> = persistentListOf(),
  val descendants: ImmutableList<StatusUiData> = persistentListOf(),
  val loadError: Boolean = false
)
