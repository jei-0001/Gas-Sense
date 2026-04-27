package com.gassense.fragments

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.gassense.R
import com.gassense.activities.MainActivity
import com.gassense.databinding.FragmentLoginBinding

class LoginFragment : Fragment() {

    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnLogin.setOnClickListener {
            val email = binding.etEmail.text.toString()
            val password = binding.etPassword.text.toString()

            if (email.isEmpty()) {
                binding.tilEmail.error = "Email is required"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                binding.tilPassword.error = "Password is required"
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            binding.tilPassword.error = null

            // Hardcoded admin account for testing
            if (email == "admin@gassense.com" && password == "admin123") {
                requireActivity().apply {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                }
            } else {
                binding.tilEmail.error = "Invalid credentials"
                binding.tilPassword.error = "Invalid credentials"
            }
        }

        binding.tvForgotPassword.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_forgot)
        }

        binding.tvRegister.setOnClickListener {
            findNavController().navigate(R.id.action_login_to_register)
        }

        binding.btnGoogle.setOnClickListener {
            // Google Sign-In would be initialized here
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
