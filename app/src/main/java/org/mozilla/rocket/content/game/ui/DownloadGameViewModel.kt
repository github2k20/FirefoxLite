package org.mozilla.rocket.content.game.ui

import android.view.ContextMenu
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.mozilla.focus.R
import org.mozilla.rocket.adapter.DelegateAdapter
import org.mozilla.rocket.content.Result
import org.mozilla.rocket.content.game.domain.GetDownloadGameListUseCase
import org.mozilla.rocket.content.game.ui.model.Game
import org.mozilla.rocket.download.SingleLiveEvent

class DownloadGameViewModel(private val getDownloadGameList: GetDownloadGameListUseCase) : ViewModel() {

    private val _isDataLoading = MutableLiveData<State>()
    val isDataLoading: LiveData<State> = _isDataLoading

    private val _downloadGameItems by lazy {
        MutableLiveData<List<DelegateAdapter.UiModel>>().apply {
            launchDataLoad {
                val result = getDownloadGameList()
                if (result is Result.Success) {
                    value = GameDataMapper.toGameUiModel(result.data)
                } else if (result is Result.Error) {
                    throw (result.exception)
                }
            }
        }
    }
    val downloadGameItems: LiveData<List<DelegateAdapter.UiModel>> = _downloadGameItems

    private lateinit var selectedGame: Game

    var event = SingleLiveEvent<GameAction>()

    fun onGameItemClicked(gameItem: Game) {
        event.value = GameAction.Install(gameItem.linkUrl)
    }

    fun onGameItemLongClicked(gameItem: Game): Boolean {
        selectedGame = gameItem
        return false
    }

    fun onCreateContextMenu(menu: ContextMenu) {
        menu.setHeaderTitle(selectedGame.name)
        menu.add(0, R.id.share, 0, R.string.gaming_vertical_menu_option_1)?.setOnMenuItemClickListener {
            onContextMenuClicked(ContextMenuAction.Share)
        }
    }

    fun onRetryButtonClicked() {
        launchDataLoad {
            val result = getDownloadGameList()
            if (result is Result.Success) {
                _downloadGameItems.postValue(GameDataMapper.toGameUiModel(result.data))
            } else if (result is Result.Error) {
                throw (result.exception)
            }
        }
    }

    private fun onContextMenuClicked(contextMenuAction: ContextMenuAction): Boolean {
        when (contextMenuAction) {
            is ContextMenuAction.Share -> {
                event.value = GameAction.Share(selectedGame.linkUrl)
            }
        }
        return false
    }

    private fun launchDataLoad(block: suspend () -> Unit): Job {
        return viewModelScope.launch {
            try {
                _isDataLoading.value = State.Loading
                block()
                _isDataLoading.value = State.Idle
            } catch (t: Throwable) {
                _isDataLoading.value = State.Error(t)
            }
        }
    }

    sealed class State {
        object Idle : State()
        object Loading : State()
        class Error(val t: Throwable) : State()
    }

    sealed class GameAction {
        data class Install(val url: String) : GameAction()
        data class Share(val url: String) : GameAction()
    }

    sealed class ContextMenuAction {
        object Share : ContextMenuAction()
    }
}