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

package com.github.whitescent.mastify.paging

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import com.github.whitescent.mastify.paging.LoadState.Error
import com.github.whitescent.mastify.paging.LoadState.NotLoading
import kotlinx.coroutines.launch

class Paginator<Key, Item>(
  private val refreshKey: Key,
  private inline val getAppendKey: suspend () -> Key,
  private inline val onLoadUpdated: (LoadState) -> Unit,
  private inline val onError: suspend (Throwable?) -> Unit,
  private inline val onRequest: suspend (Key) -> Result<List<Item>>,
  private inline val onSuccess: suspend (loadState: LoadState, items: List<Item>) -> Unit,
) : PaginatorInterface<Key, Item> {

  var loadState = NotLoading
    private set

  var endReached: Boolean = false
    private set

  override suspend fun append() {
    if (loadState == LoadState.Append) return
    loadState = LoadState.Append
    onLoadUpdated(loadState)
    try {
      val appendKey = getAppendKey()
      val result = onRequest(appendKey).getOrElse {
        onError(it)
        loadState = Error
        onLoadUpdated(loadState)
        return
      }
      if (result.isEmpty()) endReached = true
      onSuccess(loadState, result)
      loadState = NotLoading
      onLoadUpdated(loadState)
    } catch (e: Exception) {
      onError(e)
      loadState = Error
      onLoadUpdated(loadState)
      return
    }
  }

  override suspend fun refresh() {
    if (loadState == LoadState.Refresh) return
    loadState = LoadState.Refresh
    onLoadUpdated(loadState)
    try {
      val result = onRequest(refreshKey).getOrElse {
        onError(it)
        loadState = Error
        onLoadUpdated(loadState)
        return
      }
      if (result.isEmpty()) endReached = true
      onSuccess(loadState, result)
      loadState = NotLoading
      onLoadUpdated(loadState)
    } catch (e: Exception) {
      onError(e)
      loadState = Error
      onLoadUpdated(loadState)
      return
    }
  }
}

@Composable
fun <T> LaunchPaginatorListener(
  lazyListState: LazyListState,
  list: List<T>,
  paginator: Paginator<*, *>,
  fetchNumber: Int,
  threshold: Int = 10
) {
  val firstVisibleItemIndex by remember(lazyListState) {
    derivedStateOf {
      lazyListState.firstVisibleItemIndex
    }
  }
  if (list.isNotEmpty()) {
    if (!paginator.endReached && paginator.loadState == NotLoading &&
      firstVisibleItemIndex >= (list.size - ((list.size / fetchNumber) * threshold))
    ) {
      val scope = rememberCoroutineScope()
      scope.launch {
        paginator.append()
      }
    }
  }
}

enum class LoadState {
  Refresh, Append, Error, NotLoading
}
