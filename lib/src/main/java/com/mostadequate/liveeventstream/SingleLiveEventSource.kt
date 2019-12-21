package com.mostadequate.liveeventstream

import androidx.lifecycle.LifecycleOwner

interface SingleLiveEventSource<T> {
    fun observe(lifecycleOwner: LifecycleOwner, observer: androidx.lifecycle.Observer<T>)
    fun observe(lifecycleOwner: LifecycleOwner, observer: (T) -> Unit)
}