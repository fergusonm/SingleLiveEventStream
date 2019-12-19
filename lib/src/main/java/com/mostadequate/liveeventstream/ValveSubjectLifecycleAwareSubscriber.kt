package com.mostadequate.liveeventstream

import androidx.lifecycle.*
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.subjects.BehaviorSubject

class ValveSubjectLifecycleAwareSubscriber<T> constructor(
    private val eventSource: ValveSubject<T>,
    private val lifecycleOwner: LifecycleOwner,
    private val observer: Observer<T>
) : LifecycleObserver {
    private var eventSourceDisposable: Disposable? = null
    private var previousEvent: Lifecycle.Event = Lifecycle.Event.ON_CREATE

    private var lifecycleSubject = BehaviorSubject.create<Boolean>()

    init {
        when {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) -> {
                eventSource.addLifecycleSubject(lifecycleSubject)
                lifecycleSubject.onNext(false)
                lifecycleOwner.lifecycle.addObserver(this)

            }
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.DESTROYED) -> {
                eventSource.removeLifecycleSubject(lifecycleSubject)
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        val state = lifecycleOwner.lifecycle.currentState
        if (state == Lifecycle.State.DESTROYED) {

            eventSourceDisposable?.dispose()
            lifecycleSubject.onComplete()

            eventSource.removeLifecycleSubject(lifecycleSubject)
            lifecycleOwner.lifecycle.removeObserver(this)
        } else {
            val wasActive = previousEvent == Lifecycle.Event.ON_START || previousEvent == Lifecycle.Event.ON_RESUME
            val isActive = event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME
            previousEvent = event

            when {
                wasActive && !isActive -> {
                    eventSourceDisposable?.dispose()
                    lifecycleSubject.onNext(false)
                }
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
                    }.also<DisposableObserver<T>> {
                        eventSource.subscribe(it)
                        lifecycleSubject.onNext(true)
                    }
                }
            }
        }
    }

}


fun <T> ValveSubject<T>.observe(lifecycleOwner: LifecycleOwner, observer: Observer<T>) {
    ValveSubjectLifecycleAwareSubscriber(this, lifecycleOwner, observer)
}

fun <T> ValveSubject<T>.observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
    this.observe(lifecycleOwner, Observer { observer.invoke(it) })
}