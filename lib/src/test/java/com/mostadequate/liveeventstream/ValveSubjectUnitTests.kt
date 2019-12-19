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

class ValveSubjectUnitTests {

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
    fun `No observers before on start`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        // THEN
        assertFalse(stream.hasObservers())
    }

    @Test
    fun `Has observers after on start`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // THEN
        assertTrue(stream.hasObservers())
    }

    @Test
    fun `Has observers after on resume`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // THEN
        assertTrue(stream.hasObservers())
    }

    @Test
    fun `No observers after on pause`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)

        // THEN
        assertFalse(stream.hasObservers())
    }

    @Test
    fun `No observers after on stop`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)

        // THEN
        assertFalse(stream.hasObservers())
    }

    @Test
    fun `Has observers after on pause and then on resume`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // THEN
        assertTrue(stream.hasObservers())
    }

    @Test
    fun `Has observers after on stop and then on start`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
        stream.observe(lifecycleOwner) {}
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)

        // THEN
        assertTrue(stream.hasObservers())
    }

    @Test
    fun `No observers after on destroy`() {
        // GIVEN
        val stream = ValveSubject<Int>()

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
        val stream = ValveSubject<Int>()
        stream.onNext(1)

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
    fun `Emissions before lifecycle are observed by all observers added before lifecycle is ready`() {
        // GIVEN
        var observed1 = false
        var observed2 = false
        val stream = ValveSubject<Int>()
        stream.onNext(1)

        // WHEN
        stream.observe(lifecycleOwner) {
            observed1 = true
        }
        stream.observe(lifecycleOwner) {
            observed2 = true
        }
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        // THEN
        assertTrue(observed1)
        assertTrue(observed2)
    }


    @Test
    fun `Early observers see emissions after lifecycle state is good`() {
        // GIVEN
        val values1 = ArrayList<Int>()
        val values2 = ArrayList<Int>()
        val values3 = ArrayList<Int>()


        val stream = ValveSubject<Int>()
        stream.onNext(1)
        stream.onNext(2)

        // WHEN
        stream.observe(lifecycleOwner) {
            values1.add(it)
        }
        stream.observe(lifecycleOwner) {
            values2.add(it)
        }
        stream.observe(lifecycleOwner) {
            values3.add(it)
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.onNext(3)

        // THEN
        assertTrue(values1.contains(1))
        assertTrue(values1.contains(2))
        assertTrue(values1.contains(3))

        assertTrue(values2.contains(1))
        assertTrue(values2.contains(2))
        assertTrue(values2.contains(3))

        assertTrue(values3.contains(1))
        assertTrue(values3.contains(2))
        assertTrue(values3.contains(3))
    }

    @Test
    fun `Late observers only see new emissions after lifecycle state is good`() {
        // GIVEN
        val values3 = ArrayList<Int>()
        val values4 = ArrayList<Int>()


        val stream = ValveSubject<Int>()
        stream.onNext(1)
        stream.onNext(2)

        // WHEN
        stream.observe(lifecycleOwner) {
            values3.add(it)
        }

        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycle.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)

        stream.onNext(3)

        stream.observe(lifecycleOwner) {
            values4.add(it)
        }

        stream.onNext(4)

        // THEN
        assertTrue(values3.contains(1))
        assertTrue(values3.contains(2))
        assertTrue(values3.contains(3))
        assertTrue(values3.contains(4))

        assertFalse(values4.contains(1))
        assertFalse(values4.contains(2))
        assertFalse(values4.contains(3))
        assertTrue(values4.contains(4))
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
