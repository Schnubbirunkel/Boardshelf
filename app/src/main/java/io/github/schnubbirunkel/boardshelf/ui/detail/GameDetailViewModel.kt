package io.github.schnubbirunkel.boardshelf.ui.detail

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.schnubbirunkel.boardshelf.data.Game
import io.github.schnubbirunkel.boardshelf.data.Manual
import io.github.schnubbirunkel.boardshelf.data.Repository
import io.github.schnubbirunkel.boardshelf.data.SaveState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

sealed interface DetailEvent {
    data class Error(val t: Throwable) : DetailEvent
    data class Message(val res: Int) : DetailEvent
    data object Deleted : DetailEvent
    data class Share(val file: File, val mime: String) : DetailEvent
}

class GameDetailViewModel(val repo: Repository, val gameId: Long) : ViewModel() {
    val game = repo.game(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), repo.handover.remove(gameId))
    val saveStates = repo.saveStates(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val photos = repo.saveStatePhotos(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val manuals = repo.manuals(gameId).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val shelves = repo.shelves.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val busy = MutableStateFlow(false)

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    private fun task(block: suspend () -> Unit) {
        viewModelScope.launch {
            busy.value = true
            try {
                block()
            } catch (t: Throwable) {
                _events.emit(DetailEvent.Error(t))
            } finally {
                busy.value = false
            }
        }
    }

    fun update(transform: (Game) -> Game) {
        val g = game.value ?: return
        viewModelScope.launch { repo.updateGame(transform(g)) }
    }

    /** Moves the game to the end of another shelf. */
    fun moveToShelf(shelfId: Long) = task {
        val g = game.value ?: return@task
        if (g.shelfId == shelfId) return@task
        val pos = (repo.db.games().maxPosition(shelfId) ?: -1) + 1
        repo.updateGame(g.copy(shelfId = shelfId, position = pos))
    }

    fun refresh() = task { repo.refreshFromSources(gameId) }

    /** AI researches the game again on the web (fills gaps, replaces the cover if it finds a verified one). */
    fun aiResearch() = viewModelScope.launch {
        try {
            if (!repo.aiEnrich(gameId, replaceCover = true)) _events.emit(DetailEvent.Message(io.github.schnubbirunkel.boardshelf.R.string.cover_ai_none))
        } catch (t: Throwable) {
            _events.emit(DetailEvent.Error(t))
        }
    }
    fun delete() = task {
        game.value?.let { repo.deleteGame(it) }
        _events.emit(DetailEvent.Deleted)
    }

    fun setCover(file: File) = task { repo.setCoverFromFile(gameId, file); file.delete() }
    fun setCoverUri(uri: Uri) = task {
        val tmp = File.createTempFile("cover", ".jpg")
        try {
            repo.files.copyUriTo(uri, tmp); repo.setCoverFromFile(gameId, tmp)
        } finally {
            tmp.delete()
        }
    }

    fun addSaveState(files: List<File>, uris: List<Uri>, title: String, note: String) = task {
        repo.addSaveState(gameId, files, uris, title, note)
    }

    fun addPhotos(stateId: Long, files: List<File>, uris: List<Uri>) = task { repo.addPhotos(stateId, files, uris) }
    fun deletePhoto(p: io.github.schnubbirunkel.boardshelf.data.SaveStatePhoto) = task { repo.deletePhoto(p) }
    fun updateSaveState(s: SaveState) = task { repo.updateSaveState(s) }
    fun deleteSaveState(s: SaveState) = task { repo.deleteSaveState(s) }

    fun addManualUri(uri: Uri, title: String) = task { repo.addManualPdfFromUri(gameId, uri, title) }
    fun downloadManual(url: String, title: String) = task {
        repo.downloadManual(gameId, url, title)
        _events.emit(DetailEvent.Message(io.github.schnubbirunkel.boardshelf.R.string.manual_saved))
    }

    fun renameManual(m: Manual, title: String) = task { repo.renameManual(m, title) }
    fun deleteManual(m: Manual) = task { repo.deleteManual(m) }
    fun shareManual(m: Manual) = task { _events.emit(DetailEvent.Share(repo.manualAsPdf(m), "application/pdf")) }
    fun shareSaveState(s: SaveState) = task {
        repo.files.resolve(s.photoPath)?.let { _events.emit(DetailEvent.Share(it, "image/jpeg")) }
    }
}
