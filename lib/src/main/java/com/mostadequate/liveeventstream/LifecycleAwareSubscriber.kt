package com.mostadequate.liveeventstream

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import io.reactivex.ObservableSource
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver

class LifecycleAwareSubscriber<T> constructor(
    private val eventSource: ObservableSource<T>,
    private val lifecycleOwner: LifecycleOwner,
    private val observer: (T) -> Unit
) : LifecycleObserver {
    private var eventSourceDisposable: Disposable? = null
    private var previousState: Lifecycle.State = Lifecycle.State.INITIALIZED

    init {
        when {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) -> {
                lifecycleOwner.lifecycle.addObserver(this)
            }
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.DESTROYED) -> {
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        val state = lifecycleOwner.lifecycle.currentState
        if (state == Lifecycle.State.DESTROYED) {
            eventSourceDisposable?.dispose()
            lifecycleOwner.lifecycle.removeObserver(this)
        } else {
            val wasActive = previousState.isAtLeast(Lifecycle.State.STARTED)
            val isActive = state.isAtLeast(Lifecycle.State.STARTED)
            previousState = state

            when {
                wasActive && !isActive -> eventSourceDisposable?.dispose()
                !wasActive && isActive -> {
                    // Start observing
                    eventSourceDisposable?.dispose()
                    eventSourceDisposable = object : DisposableObserver<T>() {
                        override fun onComplete() = dispose()

                        override fun onNext(value: T) = observer.invoke(value)

                        override fun onError(e: Throwable) {
                            dispose()
                            throw e
                        }
                    }.also<DisposableObserver<T>> { eventSource.subscribe(it) }
                }
            }
        }
    }

}


fun <T> ObservableSource<T>.observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
    LifecycleAwareSubscriber(this, lifecycleOwner, observer)
}