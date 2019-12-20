package com.mostadequate.liveeventstream

import androidx.annotation.VisibleForTesting
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

/**
 * A ValveSubject is a Subject that buffers the emitted values when there are no subscribers and drains
 * the buffer when there are one or more subscribers.
 */
private class ValveSubject<T> : Subject<T>() {

    private val input: Subject<T> = PublishSubject.create()
    private val output: Subject<T> = PublishSubject.create<T>()

    @VisibleForTesting
    internal val lifecycleSubjects = ArrayList<Subject<Boolean>>()

    private val lifecycleSubjectsSubject: Subject<List<Subject<Boolean>>> = BehaviorSubject.create()

    init {
        val switchMap = lifecycleSubjectsSubject.switchMap { list ->
            list.combineLatest { l ->
                l.all {
                    it
                }
            }
        }

        input.compose(ObservableTransformers.valve(switchMap, false)).doOnNext { println("On next $it") }.subscribe(output)
    }

    override fun subscribeActual(observer: Observer<in T>) = output.subscribe(observer)

    override fun onSubscribe(d: Disposable) = output.onSubscribe(d)

    override fun onNext(value: T) = input.onNext(value)

    override fun onError(e: Throwable) = input.onError(e)

    override fun onComplete() {
        input.onComplete()
        output.onComplete()
        lifecycleSubjects.clear()
        lifecycleSubjectsSubject.onComplete()
    }

    override fun hasObservers(): Boolean {
        return output.hasObservers()
    }

    override fun getThrowable(): Throwable? = output.throwable

    override fun hasThrowable(): Boolean = output.hasThrowable()

    override fun hasComplete(): Boolean = output.hasComplete()

    fun addLifecycleSubject(subject: Subject<Boolean>) {
        lifecycleSubjects.add(subject)
        lifecycleSubjectsSubject.onNext(lifecycleSubjects)
    }

    fun removeLifecycleSubject(subject: Subject<Boolean>) {
        lifecycleSubjects.remove(subject)
        lifecycleSubjectsSubject.onNext(lifecycleSubjects)
    }
}

class SingleLiveEventStream<T> {

    private val valveSubject = ValveSubject<T>()

    fun observe(lifecycleOwner: LifecycleOwner, observer: androidx.lifecycle.Observer<T>) {
        ValveSubjectLifecycleAwareSubscriber(this, lifecycleOwner, observer)
    }

    fun observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit) {
        this.observe(lifecycleOwner, androidx.lifecycle.Observer { observer.invoke(it) })
    }

    internal fun subscribe(observer: androidx.lifecycle.Observer<T>, lifecycleSubject: Subject<Boolean>): SingleLiveEventJob {
        object : DisposableObserver<T>() {

            private val l: Subject<Boolean> = lifecycleSubject

            override fun onComplete() {
                valveSubject.removeLifecycleSubject(l)
                dispose()
            }

            override fun onNext(value: T) {
                observer.onChanged(value)
            }

            override fun onError(e: Throwable) {
                valveSubject.removeLifecycleSubject(l)
                dispose()
                throw e
            }
        }.also<DisposableObserver<T>> {
            val job = SingleLiveEventJobImpl(it)
            valveSubject.observeOn(AndroidSchedulers.mainThread()).subscribe(it)
            valveSubject.addLifecycleSubject(lifecycleSubject)

            return job
        }
    }

    fun emit(t: T) {
        valveSubject.onNext(t)
    }

    fun shutdown() {
        valveSubject.onComplete()
    }

    @VisibleForTesting
    internal fun hasObservers(): Boolean {
        return valveSubject.hasObservers()
    }

    @VisibleForTesting
    fun hasLifecycleWatchers(): Boolean {
        return valveSubject.lifecycleSubjects.isEmpty()
    }
}

interface SingleLiveEventJob {
    fun cancel()
}

private class SingleLiveEventJobImpl<T>(private val disposable: DisposableObserver<T>): SingleLiveEventJob {
    override fun cancel() {
        disposable.onComplete()
    }
}

private class ValveSubjectLifecycleAwareSubscriber<T> constructor(
    private val eventSource: SingleLiveEventStream<T>,
    private val lifecycleOwner: LifecycleOwner,
    private val observer: androidx.lifecycle.Observer<T>
) : LifecycleObserver {
    private var eventSourceJob: SingleLiveEventJob? = null
    private var previousEvent: Lifecycle.Event = Lifecycle.Event.ON_CREATE

    private var lifecycleSubject = BehaviorSubject.create<Boolean>()

    init {
        when {
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.INITIALIZED) -> {
                lifecycleSubject.onNext(false)
                eventSourceJob = eventSource.subscribe(observer, lifecycleSubject)

                lifecycleOwner.lifecycle.addObserver(this)
            }
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.DESTROYED) -> {
                eventSourceJob?.cancel()
                lifecycleOwner.lifecycle.removeObserver(this)
            }
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_ANY)
    fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        val state = lifecycleOwner.lifecycle.currentState
        if (state == Lifecycle.State.DESTROYED) {
            eventSourceJob?.cancel()
            lifecycleSubject.onComplete()

            lifecycleOwner.lifecycle.removeObserver(this)
        } else {
            val wasActive = previousEvent == Lifecycle.Event.ON_START || previousEvent == Lifecycle.Event.ON_RESUME
            val isActive = event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_RESUME
            previousEvent = event

            when {
                wasActive && !isActive -> lifecycleSubject.onNext(false)
                !wasActive && isActive -> lifecycleSubject.onNext(true)
            }
        }
    }

}