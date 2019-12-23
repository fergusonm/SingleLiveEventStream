# SingleLiveEventStream

This is my (currently experimental) solution to the Single Live Event problem on Android.  It supports a _stream_ of live events that are only consumed when the observer is in a good lifecycle state.  A live "event" is different than `LiveData` in that the event emitted is only intented to be observed once.  Conversely, a `LiveData` value can be observed multiple times, usually by configuration change.

There have been [several attempts](https://proandroiddev.com/livedata-with-single-events-2395dea972a8) to solve this problem.  However, so far all solutions either require the observer to be aware of prior processing of emissions (eg. Jose Alcérreca's [Single Live Event](https://gist.github.com/JoseAlcerreca/5b661f1800e1e654f07cc54fe87441af#file-event-kt) solution), require the emitter to be semi-lifecycle aware in that it cannot emit before observers have started observing or the data is lost. (eg. Hadi Lashkari Ghouchani's [Live Event](https://github.com/hadilq/LiveEvent) solution.)

With my proposed solution, I provide support for
* Buffered emissions before observers start observing
* Lifecycle aware handling without the observers having to be aware of prior processing
* Multiple observers
* Multiple different lifecycles

It supports multiple observers and ensures the events are only received when the lifecycle is at least in the started state.  If there are multiple lifecycles then the stream does not emit the values until all lifecycles are in a good state.  This is particularily important given that lifecycles that are entirely othorgonal may never receive data.

Usage
---
Usually from your view model:
```
events.emit("Something")
```

And within the "view", be it an activity, fragment, etc:
```
viewModel.events.observe(lifecycleOwner) {
        Log.d("TAG", "I saw $it was emitted!")
}
```

The observer cleans it self up on the appropriate lifecycle state and the emitter is free to emit before any observers are there.  Multiple observers may connect to the emitter before the lifecycle is in a good state but nothing will be emitted until all observers have a lifecycle in a good state.  That is with a state of `ON_START` or `ON_RESUME`.

Download
---
Download via gradle
```groovy
implementation "com.mostadequate.liveeventstream:singleliveeventstream:0.10"
```
Until it's published to jcenter you'll need to add the following to your repositories:
```
maven {
        url  "https://dl.bintray.com/fergusonm/maven" 
    }
```
