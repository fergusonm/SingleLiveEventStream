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
    fun `No emissions observed before on start`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)
        var observed = false

        // WHEN
        stream.observe(lifecycleOwner) {
            observed = true
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // THEN
        assertFalse(observed)
    }

    @Test
    fun `Has emissions observed after on start`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)
        var observed = false

        // WHEN
        stream.observe(lifecycleOwner) {
            observed = true
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // THEN
        assertTrue(observed)
    }

    @Test
    fun `Two early observers, neither observes before on start `() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)

        var observed1 = false
        var observed2 = false

        // WHEN
        stream.observe(lifecycleOwner) {
            observed1 = true
        }
        stream.observe(lifecycleOwner) {
            observed2 = true
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // THEN
        assertFalse(observed1)
        assertFalse(observed2)
    }


    @Test
    fun `No observers after on destroy`() {
        // GIVEN
        val stream = SingleLiveEventStream<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        stream.observe(lifecycleOwner) {}
        stream.observe(lifecycleOwner) {}
        
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)

        // THEN
        assertFalse(stream.hasObservers())
    }

    @Test
    fun `Emissions before lifecycle are buffered`() {
        // GIVEN
        var observed = false
        val stream = SingleLiveEventStream<Int>()
        stream.emit(1)

        // WHEN
        stream.observe(lifecycleOwner) {
            observed = true
        }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // THEN
        assertTrue(observed)
    }

    @Test
    fun `Early observers see both emissions before the lifecycle is good and new emissions after lifecycle is good`() {
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
    fun `Late observers only see new emissions after lifecycle state is good`() {
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
