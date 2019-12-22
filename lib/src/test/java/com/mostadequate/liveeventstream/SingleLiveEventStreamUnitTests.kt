package com.mostadequate.liveeventstream

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.nhaarman.mockito_kotlin.whenever
import io.reactivex.android.plugins.RxAndroidPlugins
import io.reactivex.plugins.RxJavaPlugins
import io.reactivex.schedulers.Schedulers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.mockito.Mock
import org.mockito.MockitoAnnotations

class SingleLiveEventStreamUnitTests {

    @Rule
    @JvmField
    var schedulerRule = SchedulerRule()

    @Rule
    @JvmField
    val rule = InstantTaskExecutorRule()

    @Mock
    lateinit var lifecycleOwner: LifecycleOwner
    lateinit var lifecycle: LifecycleRegistry

    @Before
    fun before() {
        MockitoAnnotations.initMocks(this)
        lifecycle = LifecycleRegistry(lifecycleOwner)
        whenever(lifecycleOwner.lifecycle).thenReturn(lifecycle)
    }

    @Test
    fun `Emissions observed after on start`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        var observed = false

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        stream.observe(lifecycleOwner) {
            observed = true
        }

        stream.emit(1)

        // THEN
        assertTrue(observed)
    }

    @Test
    fun `Emissions observed after on resume`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        var observed = false

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.observe(lifecycleOwner) {
            observed = true
        }

        stream.emit(1)

        // THEN
        assertTrue(observed)
    }

    @Test
    fun `No emissions observed before on start`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        var observed = false

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        stream.observe(lifecycleOwner) {
            observed = true
        }
        stream.emit(1)

        // THEN
        assertFalse(observed)
    }

    @Test
    fun `No emissions observed after on stop`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        var observed = false

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        stream.observe(lifecycleOwner) {
            observed = true
        }
        stream.emit(1)

        // THEN
        assertFalse(observed)
    }

    @Test
    fun `No emissions after on destroy`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        var observed = false

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        stream.observe(lifecycleOwner) {
            observed = true
        }
        stream.emit(1)

        // THEN
        assertFalse(observed)
    }

    @Test
    fun `Emissions before on start are buffered`() {
        // GIVEN
        var observed = false
        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)

        // WHEN
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.observe(lifecycleOwner) {
            observed = true
        }

        // THEN
        assertTrue(observed)
    }

    @Test
    fun `Emissions before on start, with observers added before on start, are observed by multiple observers after on start`() {
        // GIVEN
        val values1 = ArrayList<Int>()
        val values2 = ArrayList<Int>()


        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)
        stream.emit(2)

        // WHEN
        stream.observe(lifecycleOwner) {
            values1.add(it)
        }
        stream.observe(lifecycleOwner) {
            values2.add(it)
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.emit(3)

        // THEN
        assertTrue(values1.contains(1))
        assertTrue(values1.contains(2))
        assertTrue(values1.contains(3))

        assertTrue(values2.contains(1))
        assertTrue(values2.contains(2))
        assertTrue(values2.contains(3))
    }

    @Test
    fun `Observers added after on start do not see the buffered emissions from before on start`() {
        // GIVEN
        val values1 = ArrayList<Int>()
        val values2 = ArrayList<Int>()

        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)
        stream.emit(2)

        // WHEN
        stream.observe(lifecycleOwner) {
            values1.add(it)
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.emit(3)

        stream.observe(lifecycleOwner) {
            values2.add(it)
        }

        stream.emit(4)

        // THEN
        assertTrue(values1.contains(1))
        assertTrue(values1.contains(2))
        assertTrue(values1.contains(3))
        assertTrue(values1.contains(4))

        assertFalse(values2.contains(1))
        assertFalse(values2.contains(2))
        assertFalse(values2.contains(3))
        assertTrue(values2.contains(4))
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
