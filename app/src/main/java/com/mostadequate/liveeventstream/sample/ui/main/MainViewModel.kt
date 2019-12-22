package com.mostadequate.liveeventstream.sample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.mostadequate.liveeventstream.SingleLiveEventSource
import com.mostadequate.liveeventstream.SingleLiveEventStream

class MainViewModel : ViewModel() {

    private val mockRepo = MutableLiveData<Int>(1)
    private val mockSomething = MutableLiveData<String>("Loading")

    private val eventSource: SingleLiveEventStream<Event> = SingleLiveEventStream()
    private val mediatedViewState = MediatorLiveData<ViewState>().apply {
        this.value = ViewState(isLoading = true)

        addSource(mockRepo) {
            if (it == 10) {
                this.value = this.value?.copy(isLoading = false)
                eventSource.emit(BroadcastToTheWorldEvent)
            }
        }
        addSource(mockSomething) {
            this.value = this.value?.copy(message = it)
        }
    }

    // The "public" interface to receive data from the view model
    val viewState: LiveData<ViewState> = mediatedViewState

    // Events are only received once
    val events: SingleLiveEventSource<Event> = eventSource

    init {
        // emit an event right away, to demonstrate the lack of dependence on the lifecycle of the
        // observer
        eventSource.emit(Event1)
        eventSource.emit(Event2)
    }

    fun buttonClicked() {
        mockRepo.value = mockRepo.value?.inc()

        if (mockRepo.value == 3) {
            mockSomething.value = "Three"
        } else {
            mockSomething.value = mockRepo.value?.toString()
        }

        if (mockRepo.value == 2) {
            eventSource.emit(ShowSnackBarEvent)
        }
    }

    override fun onCleared() {
        super.onCleared()
        eventSource.shutdown()
    }
}

data class ViewState(
    val isLoading: Boolean = false,
    val message: String? = null
)

sealed class Event
object Event1: Event()
object Event2: Event()
object BroadcastToTheWorldEvent: Event()
object ShowSnackBarEvent: Event()