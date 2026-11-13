package com.example.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.BookmarkRepository
import com.example.model.AccessRecord
import com.example.model.Bookmark
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface BookmarkListState {
  data object Loading : BookmarkListState

  data class Ready(val bookmarks: List<Bookmark>) : BookmarkListState

  data class Error(val message: String) : BookmarkListState
}

class BookmarkViewModel(
  private val repository: BookmarkRepository,
  private val currentUserId: String,
) : ViewModel() {

  private val _selectedTagFilter = MutableStateFlow<String?>(null)
  val selectedTagFilter: StateFlow<String?> = _selectedTagFilter

  val bookmarkState: StateFlow<BookmarkListState> =
    repository
      .observeBookmarks(currentUserId)
      .map<List<Bookmark>, BookmarkListState> { list -> BookmarkListState.Ready(list) }
      .catch { error ->
        val firestoreError =
          generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        val rootError = firestoreError ?: error
        Log.w(TAG, "Error observing bookmarks", rootError)
        emit(BookmarkListState.Error(rootError.message ?: "Failed to load bookmarks"))
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MILLIS),
        initialValue = BookmarkListState.Loading,
      )

  fun setTagFilter(tag: String?) {
    _selectedTagFilter.value = if (_selectedTagFilter.value == tag) null else tag
  }

  fun saveBookmark(
    sourceFile: String,
    record: AccessRecord,
    tags: List<String>,
    note: String,
    onComplete: (Boolean, String) -> Unit,
  ) {
    viewModelScope.launch {
      val result = repository.saveBookmark(currentUserId, sourceFile, record, tags, note)
      result.fold(
        onSuccess = { onComplete(true, "Saved bookmark with ${tags.size} tag(s)") },
        onFailure = { err ->
          onComplete(false, err.localizedMessage ?: "Failed to save bookmark.")
        },
      )
    }
  }

  fun updateBookmark(
    bookmarkId: String,
    tags: List<String>,
    note: String,
    onComplete: (Boolean, String) -> Unit,
  ) {
    viewModelScope.launch {
      val result = repository.updateBookmarkTagsAndNote(currentUserId, bookmarkId, tags, note)
      result.fold(
        onSuccess = { onComplete(true, "Updated bookmark tags") },
        onFailure = { err ->
          onComplete(false, err.localizedMessage ?: "Failed to update bookmark.")
        },
      )
    }
  }

  fun deleteBookmark(bookmarkId: String, onComplete: (Boolean, String) -> Unit) {
    viewModelScope.launch {
      val result = repository.deleteBookmark(currentUserId, bookmarkId)
      result.fold(
        onSuccess = { onComplete(true, "Bookmark removed") },
        onFailure = { err ->
          onComplete(false, err.localizedMessage ?: "Failed to remove bookmark.")
        },
      )
    }
  }

  private companion object {
    const val TAG = "BookmarkVM"
    const val STOP_TIMEOUT_MILLIS = 5000L
  }
}
