package com.gassense.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.R
import com.gassense.activities.MainActivity
import com.gassense.adapters.DevicesAdapter
import com.gassense.databinding.FragmentRegisterBinding
import com.gassense.databinding.FragmentForgotPasswordBinding
import com.gassense.databinding.FragmentDeviceManagementBinding
import com.gassense.databinding.FragmentAddDeviceBinding
import com.gassense.databinding.FragmentQrScannerBinding
import com.gassense.models.DeviceItem
import com.gassense.viewmodels.AuthState
import com.gassense.viewmodels.AuthViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.authState.collect { state ->
                        handleAuthState(state)
                    }
                }
                launch {
                    viewModel.scannedDeviceId.collect { id ->
                        id?.let { binding.etDeviceId.setText(it) }
                    }
                }
            }
        }

        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()
            val deviceId = binding.etDeviceId.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (listOf(name, email, mobile, deviceId, password, confirmPassword).any { it.isEmpty() }) {
                Snackbar.make(binding.root, "Please fill in all fields", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                binding.tilConfirmPassword.error = "Passwords do not match"
                return@setOnClickListener
            }

            if (!binding.cbTerms.isChecked) {
                Snackbar.make(binding.root, "Please agree to Terms and Privacy Policy", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewModel.register(name, email, mobile, deviceId, password)
        }
        binding.tvScanQr.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_qr)
        }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    private fun handleAuthState(state: AuthState) {
        when (state) {
            is AuthState.Loading -> binding.btnCreateAccount.isEnabled = false
            is AuthState.Authenticated -> {
                requireActivity().apply {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            }
            is AuthState.Error -> {
                binding.btnCreateAccount.isEnabled = true
                if (state.isFieldSpecific) {
                    binding.tilDeviceId.error = state.message
                } else {
                    Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
                }
            }
            else -> binding.btnCreateAccount.isEnabled = true
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ForgotPasswordFragment : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.authState.collect { state ->
                    (state as? AuthState.Error)?.let {
                        Snackbar.make(binding.root, it.message, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }

        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnSendReset.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilEmail.error = "Please enter your email"
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            viewModel.resetPassword(email)
            Snackbar.make(binding.root, "If an account exists, a reset link has been sent.", Snackbar.LENGTH_LONG).show()
        }
        binding.tvBackToLogin.setOnClickListener {
            findNavController().navigate(R.id.action_forgot_to_login)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class DeviceManagementFragment : Fragment() {
    private var _binding: FragmentDeviceManagementBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentDeviceManagementBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val devices = listOf(
            DeviceItem(name = "Kitchen Tank", deviceId = "GS-2024-A1B2C3", isOnline = true, lastSynced = "2 minutes ago"),
            DeviceItem(name = "Back Stall", deviceId = "GS-2024-D4E5F6", isOnline = false, lastSynced = "3 hours ago"),
        )
        binding.rvDevices.apply {
            layoutManager = LinearLayoutManager(context)
            adapter = DevicesAdapter(devices)
        }
        binding.btnAddDevice.setOnClickListener {
            findNavController().navigate(R.id.action_devices_to_add)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class AddDeviceFragment : Fragment() {
    private var _binding: FragmentAddDeviceBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentAddDeviceBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.cardScanQr.setOnClickListener {
            findNavController().navigate(R.id.action_add_to_qr)
        }
        binding.cardEnterManually.setOnClickListener {
            binding.tilDeviceName.requestFocus()
        }
        binding.btnRegisterDevice.setOnClickListener {
            val name = binding.etDeviceName.text.toString()
            if (name.isEmpty()) {
                binding.tilDeviceName.error = "Device name is required"
                return@setOnClickListener
            }
            Toast.makeText(context, "Device '$name' registered!", Toast.LENGTH_SHORT).show()
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class QrScannerFragment : Fragment() {
    private var _binding: FragmentQrScannerBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvEnterManually.setOnClickListener { findNavController().navigateUp() }

        // Simulation of QR scan for demonstration
        binding.root.setOnClickListener {
            viewModel.setScannedDeviceId("GS-SCAN-123")
            findNavController().navigateUp()
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
