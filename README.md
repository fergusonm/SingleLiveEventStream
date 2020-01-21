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


Download
---
Download via gradle
```groovy
implementation "com.mostadequate.liveeventstream:singleliveeventstream:0.10"
```
