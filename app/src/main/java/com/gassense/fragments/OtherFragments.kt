package com.gassense.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.gassense.R
import com.gassense.activities.MainActivity
import com.gassense.databinding.FragmentRegisterBinding
import com.gassense.databinding.FragmentForgotPasswordBinding
import com.gassense.viewmodels.AuthState
import com.gassense.viewmodels.AuthViewModel
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val viewModel: AuthViewModel by activityViewModels()

    // This is a single-prototype-device build — there's no device management/QR flow to
    // supply a Device ID, so registration links every account to the one physical unit.
    // Matches the ESP32 firmware's own DEVICE_ID constant and the "DEV-TEST-0001" fallback
    // used elsewhere in the app (MainActivity, DashboardFragment) when no profile is loaded yet.
    private val prototypeDeviceId = "DEV-TEST-0001"

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
            }
        }

        binding.btnCreateAccount.setOnClickListener {
            val name = binding.etFullName.text.toString().trim()
            val email = binding.etEmail.text.toString().trim()
            val mobile = binding.etMobile.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (listOf(name, email, mobile, password, confirmPassword).any { it.isEmpty() }) {
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

            viewModel.register(name, email, mobile, prototypeDeviceId, password)
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
                Snackbar.make(binding.root, state.message, Snackbar.LENGTH_LONG).show()
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
