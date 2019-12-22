package com.mostadequate.liveeventstream.sample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import com.mostadequate.liveeventstream.sample.R
import kotlinx.android.synthetic.main.main_fragment.*
import kotlinx.android.synthetic.main.main_fragment.view.*

class MainFragment : Fragment() {

    companion object {
        fun newInstance() = MainFragment()
    }

    private lateinit var viewModel: MainViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.main_fragment, container, false)
        viewModel = ViewModelProviders.of(this).get(MainViewModel::class.java)

        view.button.setOnClickListener { viewModel.buttonClicked() }

        // Observe the view state and update the UI as live data emissions are received
        viewModel.viewState.observe(viewLifecycleOwner, Observer {
            if (it != null) {

                if (it.isLoading) {
                    progressBar.visibility = View.VISIBLE
                } else {
                    progressBar.visibility = View.GONE
                }

                message.text = it.message
            }
        })

        // Use the observe function to create a lifecycle aware observer.  Note that
        // unlike the live data observer above, these events are received once and once only.
        // This is most easily demonstrated on a configuration change.
        viewModel.events.observe(viewLifecycleOwner) {
            when (it) {
                Event1 -> Toast.makeText(this.context, "Early Observer: Event 1 received", Toast.LENGTH_LONG).show()
                Event2 -> Toast.makeText(this.context, "Early Observer: Event 2 received", Toast.LENGTH_LONG).show()
                BroadcastToTheWorldEvent -> Toast.makeText(this.context, "Early Observer: Broadcast received", Toast.LENGTH_LONG).show()
                ShowSnackBarEvent -> Toast.makeText(this.context, "Early Observer: Show snackbar event received", Toast.LENGTH_LONG).show()
            }
        }

        return view
    }

    override fun onResume() {
        super.onResume()

        // Since the stream is single live event, once the event has been sent, and consumed, it will not be observed by
        // observers registering late in the lifecycle.  This observer will only see new events that are generated after
        // onResume.
        viewModel.events.observe(viewLifecycleOwner) {
            when (it) {
                Event1 -> Toast.makeText(this.context, "Late Observer: Event 1 received", Toast.LENGTH_LONG).show()
                Event2 -> Toast.makeText(this.context, "Late Observer: Event 2 received", Toast.LENGTH_LONG).show()
                BroadcastToTheWorldEvent -> Toast.makeText(this.context, "Late Observer: Broadcast received", Toast.LENGTH_LONG).show()
                ShowSnackBarEvent -> Toast.makeText(this.context, "Later Observer: Show snackbar event received", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.events.observe(viewLifecycleOwner) {
            Toast.makeText(this.context, "I am a very late subscriber!  I should not receive any events.", Toast.LENGTH_LONG).show()
        }
    }
}
