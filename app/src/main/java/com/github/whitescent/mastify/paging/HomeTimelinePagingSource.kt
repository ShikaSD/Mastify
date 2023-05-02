package com.github.whitescent.mastify.paging

import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.github.whitescent.mastify.data.repository.ApiRepository
import com.github.whitescent.mastify.data.repository.PreferenceRepository
import com.github.whitescent.mastify.network.model.response.account.Status
import io.ktor.utils.io.errors.IOException
import retrofit2.HttpException
import javax.inject.Inject

class HomeTimelinePagingSource @Inject constructor(
  private val apiRepository: ApiRepository,
  private val preferenceRepository: PreferenceRepository
) : PagingSource<String, Status>() {

  private var nextPageId: String? = null
  private val account = preferenceRepository.account!!

  override fun getRefreshKey(state: PagingState<String, Status>): String? {
    return if (nextPageId == null) null else nextPageId
  }

  override suspend fun load(params: LoadParams<String>): LoadResult<String, Status> {
    return try {
      val data = apiRepository.getHomeTimeline(
        instanceName = account.instanceName,
        token = account.accessToken,
        maxId = if (nextPageId != null) nextPageId else null
      )
      if (data.isNotEmpty()) {
        LoadResult.Page(
          data = data,
          prevKey = nextPageId,
          nextKey = data[data.size - 1].id
        ).also {
          nextPageId = data[data.size - 1].id
        }
      } else return LoadResult.Error(NullPointerException())
    } catch (exception: IOException) {
      exception.printStackTrace()
      return LoadResult.Error(exception)
    } catch (exception: HttpException) {
      exception.printStackTrace()
      return LoadResult.Error(exception)
    }
  }
}