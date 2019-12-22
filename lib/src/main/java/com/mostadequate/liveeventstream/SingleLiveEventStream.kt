package com.mostadequate.liveeventstream

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import hu.akarnokd.rxjava2.operators.ObservableTransformers
import io.reactivex.Observer
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.observers.DisposableObserver
import io.reactivex.rxkotlin.combineLatest
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

class SingleLiveEventStream<T> : SingleLiveEventSource<T> {

    private val valveSubject = ValveSubject<T>()

    override fun observe(lifecycleOwner: LifecycleOwner, observer: androidx.lifecycle.Observer<T>) {
        ValveSubjectLifecycleAwareSubscriber(this, lifecycleOwner, observer)
    }

    override fun observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
        this.observe(lifecycleOwner, androidx.lifecycle.Observer {
            observer.invoke(it)
        })
    }

    internal fun subscribeWithLifecycle(
        observer: androidx.lifecycle.Observer<T>,
        valveSource: Subject<Boolean>
    ): SingleLiveEventCancelable {
        val disposableObserver = object : DisposableObserver<T>() {

            private val source: Subject<Boolean> = valveSource

            override fun onComplete() {
                valveSubject.removeValveSource(source)
                dispose()
            }

            override fun onNext(value: T) {
                observer.onChanged(value)
            }

            override fun onError(e: Throwable) {
                valveSubject.removeValveSource(source)
                dispose()
                throw e
            }
        }

        val job = object : SingleLiveEventCancelable {
            override fun cancel() {
                disposableObserver.onComplete()
                disposableObserver.dispose()
            }
        }
        valveSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(disposableObserver)
        valveSubject.addValveSource(valveSource)

        return job
    }

    fun emit(t: T) {
        valveSubject.onNext(t)
    }

    fun shutdown() {
        valveSubject.onComplete()
    }
}

/**
 * A ValveSubject is a Subject that buffers the emitted values when the valve
 */
private class ValveSubject<T> : Subject<T>() {

    private val input: Subject<T> = PublishSubject.create()
    private val output: Subject<T> = PublishSubject.create<T>()

    private val valves = ArrayList<Subject<Boolean>>()
    private val valveSources: Subject<List<Subject<Boolean>>> = BehaviorSubject.create()

    init {
        val switchMap = valveSources.switchMap { valveEmitters ->
            valveEmitters.combineLatest { isReadyList ->
                isReadyList.all { it }
            }
        }

        input.compose(ObservableTransformers.valve(switchMap, false)).subscribe(output)
    }

    override fun subscribeActual(observer: Observer<in T>) = output.subscribe(observer)

    override fun onSubscribe(d: Disposable) = output.onSubscribe(d)

    override fun onNext(value: T) = input.onNext(value)

    override fun onError(e: Throwable) = input.onError(e)

    override fun onComplete() {
        input.onComplete()
        output.onComplete()
        valves.forEach { it.onComplete() }
        valves.clear()
    }

    override fun hasObservers(): Boolean = output.hasObservers()

    override fun getThrowable(): Throwable? = output.throwable

    override fun hasThrowable(): Boolean = output.hasThrowable()

    override fun hasComplete(): Boolean = output.hasComplete()

    internal fun addValveSource(subject: Subject<Boolean>) {
        valves.add(subject)
        valveSources.onNext(valves)
    }

    internal fun removeValveSource(subject: Subject<Boolean>) {
        valves.remove(subject)
        valveSources.onNext(valves)
    }
}

private class ValveSubjectLifecycleAwareSubscriber<T> constructor(
    eventSource: SingleLiveEventStream<T>,
    private val lifecycleOwner: LifecycleOwner,
    observer: androidx.lifecycle.Observer<T>
) : LifecycleObserver {

    private var eventSourceCancelable: SingleLiveEventCancelable? = null
    private var previousState: Lifecycle.State = Lifecycle.State.INITIALIZED
    private var lifecycleSubject = BehaviorSubject.create<Boolean>()

    init {
        when {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) -> {
                lifecycleSubject.onNext(false)
                eventSourceCancelable = eventSource.subscribeWithLifecycle(observer, lifecycleSubject)

                lifecycleOwner.lifecycle.addObserver(this)
            }
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.DESTROYED) -> {
                eventSourceCancelable?.cancel()
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        val state = lifecycleOwner.lifecycle.currentState
        if (state == Lifecycle.State.DESTROYED) {
            eventSourceCancelable?.cancel()
            lifecycleSubject.onComplete()

            lifecycleOwner.lifecycle.removeObserver(this)
        } else {
            val wasActive = previousState.isAtLeast(Lifecycle.State.STARTED)
            val isActive = state.isAtLeast(Lifecycle.State.STARTED)
            previousState = state

            when {
                wasActive && !isActive -> lifecycleSubject.onNext(false)
                !wasActive && isActive -> lifecycleSubject.onNext(true)
            }
        }
    }
}