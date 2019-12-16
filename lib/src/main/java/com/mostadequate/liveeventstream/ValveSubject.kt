package com.mostadequate.liveeventstream

import hu.akarnokd.rxjava2.operators.ObservableTransformers
import io.reactivex.Observer
import io.reactivex.disposables.Disposable
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * A ValveSubject is a Subject that buffers the emitted values when there are no subscribers and drains
 * the buffer when there are one or more subscribers.
 */
class ValveSubject<T> : Subject<T>() {

    private val input: Subject<T> = PublishSubject.create()
    private val valveSource: Subject<Boolean> = BehaviorSubject.create()
    private val output: Subject<T> = PublishWithValveSubject(valveSource)

    init {
        input.compose(ObservableTransformers.valve(valveSource, false)).subscribe(output)
    }

    override fun subscribeActual(observer: Observer<in T>) = output.subscribe(observer)

    override fun onSubscribe(d: Disposable) = output.onSubscribe(d)

    override fun onNext(value: T) = input.onNext(value)

    override fun onError(e: Throwable) = input.onError(e)

    override fun onComplete() {
        input.onComplete()
        output.onComplete()
        valveSource.onComplete()
    }

    override fun hasObservers(): Boolean = output.hasObservers()

    override fun getThrowable(): Throwable? = output.throwable

    override fun hasThrowable(): Boolean = output.hasThrowable()

    override fun hasComplete(): Boolean = output.hasComplete()

}

/**
 * Nearly all of this is copied from PublishSubject, however, it adds in the valve subject that
 * emits 'false' when there are no observers and 'true' when there are observers.
 */
private class PublishWithValveSubject<T>(
    private val valveSource: Subject<Boolean>
) : Subject<T>() {

    private val EMPTY: Array<PublishWithValveDisposable<T>?> = emptyArray()
    private val TERMINATED: Array<PublishWithValveDisposable<T>?> = emptyArray()

    private var error: Throwable? = null
    private val subscribers: AtomicReference<Array<PublishWithValveDisposable<T>?>> = AtomicReference(EMPTY)

    override fun subscribeActual(observer: Observer<in T>) {
        val vs = PublishWithValveDisposable(downstream = observer, parent = this)
        observer.onSubscribe(vs)

        if (add(vs)) {
            // if cancellation happened while a successful add, the remove()
            // didn't work so we need to do it again
            if (vs.isDisposed) {
                remove(vs)
            }
        } else {
            error?.let { observer.onError(it) } ?: observer.onComplete()
        }
    }

    /**
     * Tries to add the given subscriber to the subscribers array atomically
     * or returns false if the subject has terminated.
     * @param ps the subscriber to add
     * @return true if successful, false if the subject has terminated
     */
    private fun add(ps: PublishWithValveDisposable<T>): Boolean {
        while (true) {
            val a: Array<PublishWithValveDisposable<T>?> = subscribers.get()
            if (a === TERMINATED) {
                return false
            }

            val n = a.size
            val b: Array<PublishWithValveDisposable<T>?> = arrayOfNulls(n + 1)
            System.arraycopy(a, 0, b, 0, n)
            b[n] = ps
            if (subscribers.compareAndSet(a, b)) {
                valveSource.onNext(true)
                return true
            }
        }
    }

    /**
     * Atomically removes the given subscriber if it is subscribed to the subject.
     * @param ps the subject to remove
     */
    private fun remove(ps: PublishWithValveDisposable<T>) {
        while (true) {
            val a: Array<PublishWithValveDisposable<T>?> = subscribers.get()
            if (a === TERMINATED || a === EMPTY) {
                return
            }
            val n = a.size
            var j = -1
            for (i in 0 until n) {
                if (a[i] == ps) {
                    j = i
                    break
                }
            }
            if (j < 0) {
                return
            }
            var b: Array<PublishWithValveDisposable<T>?>
            if (n == 1) {
                b = EMPTY
                valveSource.onNext(false) // MINE
            } else {
                b = arrayOfNulls(n - 1)
                System.arraycopy(a, 0, b, 0, j)
                System.arraycopy(a, j + 1, b, j, n - j - 1)
            }
            if (subscribers.compareAndSet(a, b)) {
                return
            }
        }
    }

    override fun onSubscribe(d: Disposable) {
        if (subscribers.get() === TERMINATED) {
            d.dispose()
        }
    }

    override fun onNext(value: T) {
        subscribers.get().forEach { it?.onNext(value) }
    }


    override fun onError(e: Throwable) {
        if (subscribers.get() === TERMINATED) {
            RxJavaPlugins.onError(e)
            return
        }

        error = e
        subscribers.getAndSet(TERMINATED).forEach { it?.onError(e) }
    }


    override fun onComplete() {
        if (subscribers.get() === TERMINATED) {
            return
        }

        subscribers.getAndSet(TERMINATED).forEach { it?.onComplete() }
    }

    override fun hasObservers(): Boolean {
        return subscribers.get().isNotEmpty()
    }

    override fun getThrowable(): Throwable? {
        return if (subscribers.get() === TERMINATED) {
            error
        } else null
    }

    override fun hasThrowable(): Boolean {
        return subscribers.get() === TERMINATED && error != null
    }

    override fun hasComplete(): Boolean {
        return subscribers.get() === TERMINATED && error == null
    }

    /**
     * Constructs a ValveSubjectDisposable, wraps the actual subscriber and the state.
     * @param actual the actual subscriber
     * @param parent the parent ValveProcessor
     */
    private class PublishWithValveDisposable<T>(
        private val downstream: Observer<in T>,
        private val parent: PublishWithValveSubject<T>
    ) : AtomicBoolean(), Disposable {

        fun onNext(t: T) {
            if (!get()) downstream.onNext(t)
        }

        fun onError(t: Throwable) {
            if (get()) {
                RxJavaPlugins.onError(t)
            } else {
                downstream.onError(t)
            }
        }

        fun onComplete() {
            if (!get()) downstream.onComplete()
        }

        override fun dispose() {
            if (compareAndSet(false, true)) {
                parent.remove(this)
            }
        }

        override fun isDisposed(): Boolean {
            return get()
        }

        companion object {
            private const val serialVersionUID = 3562861878281475070L
        }
    }
}