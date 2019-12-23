package com.mostadequate.liveeventstream

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.Observer
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.never
import com.nhaarman.mockito_kotlin.then
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class SingleLiveEventStreamUnitTests {

    // Necessary to help mockito out with kotlin higher order functions
    private interface Callback<T> : (T) -> Unit

    @Rule
    @JvmField
    var schedulerRule = SchedulerRule()

    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var lifecycleOwner: LifecycleOwner
    lateinit var lifecycle: LifecycleRegistry

    private lateinit var observer: Observer<Int>
    private lateinit var callbackObserver: (Int) -> Unit
    private lateinit var stream: SingleLiveEventStream<Int>
    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        lifecycle = LifecycleRegistry(lifecycleOwner)
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)

        observer = mock()
        callbackObserver = mock<Callback<Int>>()
        stream = SingleLiveEventStream<Int>()
    }

    @After
    fun after() {
        stream.shutdown()
    }

    @Test
    fun `Emissions observed after on start`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        stream.observe(lifecycleOwner, observer)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).should().onChanged(1)
    }

    @Test
    fun `Emissions observed with multiple observers after on start`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        stream.observe(lifecycleOwner, observer)
        stream.observe(lifecycleOwner, callbackObserver)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).should().onChanged(1)
        then(callbackObserver).should().invoke(1)
    }

    @Test
    fun `Emissions observed after on resume`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        stream.observe(lifecycleOwner, observer)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).should().onChanged(1)
    }

    @Test
    fun `No emissions observed before on start`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        stream.observe(lifecycleOwner, observer)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).shouldHaveZeroInteractions()
    }

    @Test
    fun `No emissions observed after on stop`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        stream.observe(lifecycleOwner, observer)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).shouldHaveZeroInteractions()
    }

    @Test
    fun `No emissions after on destroy`() {
        // GIVEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        stream.observe(lifecycleOwner, observer)

        // WHEN
        stream.emit(1)

        // THEN
        then(observer).shouldHaveZeroInteractions()
    }

    @Test
    fun `Emissions before on start are buffered`() {
        // GIVEN
        stream.emit(1)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // WHEN
        stream.observe(lifecycleOwner, observer)

        // THEN
        then(observer).should().onChanged(1)
    }

    @Test
    fun `Multiple emissions before on start are buffered`() {
        // GIVEN
        stream.emit(1)
        stream.emit(2)
        stream.emit(3)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // WHEN
        stream.observe(lifecycleOwner, observer)

        // THEN
        then(observer).should().onChanged(1)
        then(observer).should().onChanged(2)
        then(observer).should().onChanged(3)
    }

    @Test
    fun `Emissions before on start, with observers added before on start, are observed by multiple observers after on start`() {
        // GIVEN
        val observer2 = mock<Observer<Int>>()
        stream.emit(1)
        stream.emit(2)

        stream.observe(lifecycleOwner, observer)
        stream.observe(lifecycleOwner, observer2)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.emit(3)

        // THEN
        then(observer).should().onChanged(1)
        then(observer).should().onChanged(2)
        then(observer).should().onChanged(3)

        then(observer2).should().onChanged(1)
        then(observer2).should().onChanged(2)
        then(observer2).should().onChanged(3)

    }

    @Test
    fun `Observers added after on start do not observer the buffered emissions from before on start`() {
        // GIVEN
        val observer2 = mock<Observer<Int>>()
        stream.emit(1)
        stream.emit(2)

        stream.observe(lifecycleOwner, observer)

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.emit(3)

        // WHEN
        stream.observe(lifecycleOwner, observer2)
        stream.emit(4)

        // THEN
        then(observer).should().onChanged(1)
        then(observer).should().onChanged(2)
        then(observer).should().onChanged(3)
        then(observer).should().onChanged(4)

        then(observer2).should(never()).onChanged(1)
        then(observer2).should(never()).onChanged(2)
        then(observer2).should(never()).onChanged(3)
        then(observer2).should().onChanged(4)
    }

    @Test
    fun `Observers with different lifecycles should only observe emissions when both lifecycles reach on start`() {
        // GIVEN
        val lifecycleOwner2 = mock<LifecycleOwner>()
        val lifecycle2 = LifecycleRegistry(lifecycleOwner2)
        whenever(lifecycleOwner2.lifecycle).thenReturn(lifecycle2)

        val observer2 = mock<Observer<Int>>()
        stream.observe(lifecycleOwner, observer)
        stream.observe(lifecycleOwner2, observer2)

        stream.emit(1)

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // THEN
        then(observer).shouldHaveZeroInteractions()
        then(observer2).shouldHaveZeroInteractions()

        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        then(observer).should().onChanged(1)
        then(observer2).should().onChanged(1)
    }

    @Test
    fun `Observers with orthogonal lifecycles should never observe emissions`() {
        // GIVEN
        val lifecycleOwner2 = mock<LifecycleOwner>()
        val lifecycle2 = LifecycleRegistry(lifecycleOwner2)
        whenever(lifecycleOwner2.lifecycle).thenReturn(lifecycle2)

        val observer2 = mock<Observer<Int>>()
        stream.observe(lifecycleOwner, observer)
        stream.observe(lifecycleOwner2, observer2)

        stream.emit(1)

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // THEN
        then(observer).shouldHaveZeroInteractions()
        then(observer2).shouldHaveZeroInteractions()
        then(observer).shouldHaveNoMoreInteractions()
        then(observer2).shouldHaveNoMoreInteractions()

        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle2.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
}

class SchedulerRule : TestRule {
    override fun apply(base: Statement?, description: Description?): Statement {
        return object : Statement() {
            override fun evaluate() {
                RxJavaPlugins.setIoSchedulerHandler { Schedulers.trampoline() }
                RxJavaPlugins.setComputationSchedulerHandler { Schedulers.trampoline() }
                RxJavaPlugins.setNewThreadSchedulerHandler { Schedulers.trampoline() }
                RxAndroidPlugins.setInitMainThreadSchedulerHandler { Schedulers.trampoline() }

                try {
                    base?.evaluate()
                } finally {
                    RxJavaPlugins.reset()
                    RxAndroidPlugins.reset()
                }
            }
        }
    }
}
