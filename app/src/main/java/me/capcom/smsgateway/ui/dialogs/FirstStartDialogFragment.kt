package me.capcom.smsgateway.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.DialogFirstStartBinding

/**
 * MD SMS: link the phone to the merchant's MD Store account with the one-time code
 * shown in the dashboard. Sign-up and username/password sign-in were removed.
 */
class FirstStartDialogFragment : DialogFragment() {

    private var _binding: DialogFirstStartBinding? = null
    private val binding get() = _binding!!

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        _binding = DialogFirstStartBinding.inflate(layoutInflater)

        binding.buttonContinue.setOnClickListener { actionLink() }
        binding.buttonCancel.setOnClickListener { actionCancel() }

        return AlertDialog.Builder(requireActivity())
            .apply {
                setView(binding.root)
                setCancelable(false)
            }
            .create()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun actionLink() {
        val code = binding.editCode.text.toString().trim().lowercase()

        binding.editCodeLayout.error = null
        if (code.isEmpty()) {
            binding.editCodeLayout.error = getString(R.string.mdsms_code_required)
            return
        }

        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply {
                putString(KEY_RESULT_CODE, Result.SignInByCode.name)
                putString(KEY_CODE, code)
            }
        )
        dismiss()
    }

    private fun actionCancel() {
        setFragmentResult(
            REQUEST_KEY,
            Bundle().apply { putString(KEY_RESULT_CODE, Result.Canceled.name) }
        )
        dismiss()
    }

    enum class Result {
        Canceled,
        SignInByCode,
    }

    companion object {
        const val REQUEST_KEY = "FirstStartDialogFragment"

        private const val KEY_RESULT_CODE = "result_code"
        private const val KEY_CODE = "code"

        fun newInstance(): FirstStartDialogFragment = FirstStartDialogFragment()

        fun getResult(data: Bundle): Result =
            runCatching { Result.valueOf(data.getString(KEY_RESULT_CODE) ?: "") }
                .getOrDefault(Result.Canceled)

        fun getCode(data: Bundle): String = requireNotNull(data.getString(KEY_CODE))
    }
}
