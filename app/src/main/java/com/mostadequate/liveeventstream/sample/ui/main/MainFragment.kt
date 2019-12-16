package com.mostadequate.liveeventstream.sample.ui.main

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.lifecycle.Observer
import com.mostadequate.liveeventstream.observe
import com.mostadequate.liveeventstream.sample.R
import io.reactivex.subjects.Subject
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

        // Use the observe extension function to create a lifecycle aware observer.  Note that
        // unlike the live data observer above, these events are received once and once only.
        // This is most easily demonstrated on a configuration change.
        viewModel.events.observe(viewLifecycleOwner) {
            when (it) {
                Event1 -> Toast.makeText(this.context, "Event 1 received", Toast.LENGTH_LONG).show()
                Event2 -> Toast.makeText(this.context, "Event 2 received", Toast.LENGTH_LONG).show()
                BroadcastToTheWorldEvent -> Toast.makeText(this.context, "Broadcast received", Toast.LENGTH_LONG).show()
                ShowSnackBarEvent -> Toast.makeText(this.context, "Show snackbar event received", Toast.LENGTH_LONG).show()
            }
        }

        viewModel.events.observe(viewLifecycleOwner) {
            Toast.makeText(this.context, "Second observer also received event for $it", Toast.LENGTH_LONG).show()
        }

        return view
    }

    override fun onStart() {
        super.onStart()

        viewModel.events.observe(viewLifecycleOwner) {
            Toast.makeText(this.context, "Late third observer only receives new event for $it", Toast.LENGTH_LONG).show()
        }
    }

    override fun onStop() {
        super.onStop()
        viewModel.events.observe(viewLifecycleOwner) {
            Toast.makeText(this.context, "I am a very late subscriber!  I should not receive any events.", Toast.LENGTH_LONG).show()
        }
    }
}
