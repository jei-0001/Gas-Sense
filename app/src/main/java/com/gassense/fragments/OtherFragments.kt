package com.gassense.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.gassense.R
import com.gassense.adapters.DevicesAdapter
import com.gassense.databinding.FragmentRegisterBinding
import com.gassense.databinding.FragmentForgotPasswordBinding
import com.gassense.databinding.FragmentDeviceManagementBinding
import com.gassense.databinding.FragmentAddDeviceBinding
import com.gassense.databinding.FragmentQrScannerBinding
import com.gassense.models.DeviceItem

class RegisterFragment : Fragment() {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentRegisterBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.btnCreateAccount.setOnClickListener {
            if (!binding.cbTerms.isChecked) {
                Toast.makeText(context, "Please agree to Terms and Privacy Policy", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            Toast.makeText(context, "Account created!", Toast.LENGTH_SHORT).show()
        }
        binding.tvScanQr.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_qr)
        }
        binding.tvLogin.setOnClickListener {
            findNavController().navigate(R.id.action_register_to_login)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}

class ForgotPasswordFragment : Fragment() {
    private var _binding: FragmentForgotPasswordBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentForgotPasswordBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.btnSendReset.setOnClickListener {
            val email = binding.etEmail.text.toString()
            if (email.isEmpty()) {
                binding.tilEmail.error = "Please enter your email"
                return@setOnClickListener
            }
            binding.tilEmail.error = null
            Toast.makeText(context, "Reset link sent to $email", Toast.LENGTH_SHORT).show()
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
            DeviceItem("Kitchen Tank", "GS-2024-A1B2C3", true, "2 minutes ago"),
            DeviceItem("Back Stall", "GS-2024-D4E5F6", false, "3 hours ago")
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

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, s: Bundle?): View {
        _binding = FragmentQrScannerBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.toolbar.setNavigationOnClickListener { findNavController().navigateUp() }
        binding.tvEnterManually.setOnClickListener { findNavController().navigateUp() }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
