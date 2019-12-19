package com.mostadequate.liveeventstream

import hu.akarnokd.rxjava2.operators.ObservableTransformers
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.combineLatest
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject

/**
 * A ValveSubject is a Subject that buffers the emitted values when there are no subscribers and drains
 * the buffer when there are one or more subscribers.
 */
class ValveSubject<T> : Subject<T>() {

    private val input: Subject<T> = PublishSubject.create()
    private val output: Subject<T> = PublishSubject.create<T>()
    private val lifecycleSubjects = ArrayList<Subject<Boolean>>()
    private val lifecycleSubjectsSubject: Subject<List<Subject<Boolean>>> = BehaviorSubject.create()

    init {
        val switchMap = lifecycleSubjectsSubject.switchMap { list ->
            list.combineLatest {
                it.all {
                    it
                }
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
        lifecycleSubjects.clear()
        lifecycleSubjectsSubject.onComplete()
    }

    override fun hasObservers(): Boolean {
        return output.hasObservers()
    }

    override fun getThrowable(): Throwable? = output.throwable

    override fun hasThrowable(): Boolean = output.hasThrowable()

    override fun hasComplete(): Boolean = output.hasComplete()

    fun addLifecycleSubject(subject: BehaviorSubject<Boolean>) {
        lifecycleSubjects.add(subject)
        lifecycleSubjectsSubject.onNext(lifecycleSubjects)
    }

    fun removeLifecycleSubject(subject: BehaviorSubject<Boolean>) {
        lifecycleSubjects.remove(subject)
        lifecycleSubjectsSubject.onNext(lifecycleSubjects)
    }
}