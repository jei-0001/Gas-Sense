package com.gassense.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.adapters.AdminAdapter
import com.gassense.databinding.FragmentAdminBinding
import com.gassense.viewmodels.AdminViewModel
import kotlinx.coroutines.launch

class AdminFragment : Fragment() {

    private var _binding: FragmentAdminBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AdminViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = AdminAdapter { device ->
            viewModel.toggleShutoff(device.id, device.autoShutoff)
            val action = if (device.autoShutoff) "opening valve" else "triggering shutoff"
            Toast.makeText(context, "Command sent: $action for ${device.name}", Toast.LENGTH_SHORT).show()
        }

        binding.rvFleet.layoutManager = LinearLayoutManager(context)
        binding.rvFleet.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.fleetStatus.collect { devices ->
                    adapter.submitList(devices)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
