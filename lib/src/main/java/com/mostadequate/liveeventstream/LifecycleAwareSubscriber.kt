package com.mostadequate.liveeventstream

import androidx.lifecycle.*
import io.reactivex.ObservableSource
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver

class LifecycleAwareSubscriber<T> constructor(
    private val eventSource: ObservableSource<T>,
    private val lifecycleOwner: LifecycleOwner,
    private val observer: Observer<T>
) : LifecycleObserver {
    private var eventSourceDisposable: Disposable? = null
    private var previousEvent: Lifecycle.Event = Lifecycle.Event.ON_CREATE

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
            val wasActive = previousEvent == Lifecycle.Event.ON_START || previousEvent == Lifecycle.Event.ON_RESUME
            val isActive = event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME
            previousEvent = event

            when {
                wasActive && !isActive -> eventSourceDisposable?.dispose()
                !wasActive && isActive -> {
                    // Start observing
                    eventSourceDisposable?.dispose()
                    eventSourceDisposable = object : DisposableObserver<T>() {
                        override fun onComplete() {
                            dispose()
                        }

                        override fun onNext(value: T) {
                            observer.onChanged(value)
                        }

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


fun <T> ObservableSource<T>.observe(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    LifecycleAwareSubscriber(this, lifecycleOwner, observer)
}

fun <T> ObservableSource<T>.observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
    this.observe(lifecycleOwner, Observer { observer.invoke(it) })
}