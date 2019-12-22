# SingleLiveEventStream

This is my solution to the Single Live Event problem on Android.  It supports a stream of live events that are only consumed
when the observer is in a good lifecycle state.  It supports multiple observers and ensures the events are only received 
when the lifecycle is at least in the started state.
