# SingleLiveEventStream

Note: it's worth noting that this solution is old. I have an updated solution explained here: https://proandroiddev.com/android-singleliveevent-redux-with-kotlin-flow-b755c70bb055 that, while it doesn't contain something as neat as the ValveSubject in this repository, it should solve the use case well.

This is my (currently experimental) solution to the Single Live Event problem on Android.  It supports a _stream_ of live events that are only consumed when the observer is in a good lifecycle state.  A live "event" is different than `LiveData` in that the event emitted is only intented to be observed once.  Conversely, a `LiveData` value can be observed multiple times, usually by configuration change.

There have been [several attempts](https://proandroiddev.com/livedata-with-single-events-2395dea972a8) to solve this problem.  However, so far all solutions either require the observer to be aware of prior processing of emissions (eg. Jose Alcérreca's [Single Live Event](https://gist.github.com/JoseAlcerreca/5b661f1800e1e654f07cc54fe87441af#file-event-kt) solution), require the emitter to be semi-lifecycle aware in that it cannot emit before observers have started observing or the data is lost. (eg. Hadi Lashkari Ghouchani's [Live Event](https://github.com/hadilq/LiveEvent) solution.)

With my proposed solution, I provide support for
* Buffered emissions before observers start observing
* Lifecycle aware handling without the observers having to be aware of prior processing
* Multiple observers
* Multiple different lifecycles

It supports multiple observers and ensures the events are only received when the lifecycle is at least in the started state.  If there are multiple lifecycles then the stream does not emit the values until all lifecycles are in a good state.  This is particularily important given that lifecycles that are entirely othorgonal may never receive data.

## Usage

Define events for your event stream:

```groovy
sealed class Event
object Event1: Event()
object Event2: Event()
object BroadcastToTheWorldEvent: Event()
object ShowSnackBarEvent: Event()
```

Create the event stream (note the TODO that I am still working on)

```groovy
class MainViewModel : ViewModel() {
    // Events are only received once
    val events: SingleLiveEventSource<Event> = eventSource

    init {
        // emit an event right away, to demonstrate the lack of dependence on the lifecycle of the
        // observer
        eventSource.emit(Event1)
        eventSource.emit(Event2)
    }
    
    override fun onCleared() {
        super.onCleared()
        eventSource.shutdown() // TODO: make this automatic by observing the viewModelScope
    }
}
```

Observe the event stream:

```groovy
viewModel.events.observe(viewLifecycleOwner) {

}
```
That's it.

It's experimental, so you've been warned.

## Download
Download via gradle
```groovy
implementation "com.mostadequate.liveeventstream:singleliveeventstream:0.10"
```
