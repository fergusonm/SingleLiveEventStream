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
    fun `observers after on start`() {
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
    fun `observers after on resume`() {
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
    fun `observers after on pause and then on resume`() {
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
    fun `observers after on stop and then on start`() {
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
    fun `no observers after on destroy`() {
        // GIVEN
        val stream = ValveSubject<Int>()

        // WHEN
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
