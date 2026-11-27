package me.capcom.smsgateway.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.RadioButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import me.capcom.smsgateway.R
import me.capcom.smsgateway.databinding.FragmentHomeBinding
import me.capcom.smsgateway.helpers.SettingsHelper
import me.capcom.smsgateway.helpers.SubscriptionsHelper
import me.capcom.smsgateway.modules.connection.ConnectionService
import me.capcom.smsgateway.modules.events.EventBus
import me.capcom.smsgateway.modules.gateway.GatewayService
import me.capcom.smsgateway.modules.gateway.GatewaySettings
import me.capcom.smsgateway.modules.gateway.events.DeviceRegisteredEvent
import me.capcom.smsgateway.modules.messages.MessagesSettings
import me.capcom.smsgateway.modules.orchestrator.OrchestratorService
import me.capcom.smsgateway.ui.dialogs.FirstStartDialogFragment
import org.koin.android.ext.android.inject

/**
 * MD SMS home screen: link the phone with a code, then start/stop sending.
 * The MD Store server is fixed (see [GatewaySettings.PUBLIC_URL]).
 */
class HomeFragment : Fragment() {

    private var registrationInFlight = false

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val settingsHelper: SettingsHelper by inject()
    private val gatewaySettings: GatewaySettings by inject()
    private val messagesSettings: MessagesSettings by inject()
    private val connectionService: ConnectionService by inject()
    private val events: EventBus by inject()
    private val gatewaySvc: GatewayService by inject()
    private val orchestratorSvc: OrchestratorService by inject()

    private val isRunning: Boolean
        get() = gatewaySvc.isActiveLiveData(requireContext()).value ?: false

    private var hasInternet = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setFragmentResultListener(FirstStartDialogFragment.REQUEST_KEY) { _, data ->
            when (FirstStartDialogFragment.getResult(data)) {
                FirstStartDialogFragment.Result.Canceled -> Unit
                FirstStartDialogFragment.Result.SignInByCode -> registerAndStart(
                    GatewayService.RegistrationMode.WithCode(FirstStartDialogFragment.getCode(data))
                )
            }
        }
    }

    private fun registerAndStart(mode: GatewayService.RegistrationMode) {
        // Each registration creates a separate device, and the last response wins,
        // so a second request must not start while one is still in flight.
        if (registrationInFlight) return
        registrationInFlight = true
        setStartEnabled(false)

        lifecycleScope.launch {
            try {
                val appContext = requireContext().applicationContext
                gatewaySvc.registerDevice(appContext, null, mode)
                requestPermissionsAndStart()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val ctx = context ?: return@launch
                Toast.makeText(
                    ctx,
                    ctx.getString(R.string.mdsms_link_failed, e.message ?: e.toString()),
                    Toast.LENGTH_LONG
                ).show()
            } finally {
                registrationInFlight = false
                setStartEnabled(true)
                renderStatus()
            }
        }
    }

    private fun setStartEnabled(enabled: Boolean) {
        _binding?.buttonStart?.isEnabled = enabled
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchAutostart.isChecked = settingsHelper.autostart
        binding.switchAutostart.setOnCheckedChangeListener { _, isChecked ->
            settingsHelper.autostart = isChecked
        }

        binding.buttonStart.setOnClickListener {
            actionStart(!isRunning)
        }

        binding.buttonUnlink.setOnClickListener { confirmUnlink() }

        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Success> { renderStatus() }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            events.collect<DeviceRegisteredEvent.Failure> { event ->
                renderStatus()
                Toast.makeText(
                    requireContext(),
                    getString(R.string.mdsms_link_failed, event.reason),
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        gatewaySvc.isActiveLiveData(requireContext()).observe(viewLifecycleOwner) { running ->
            binding.buttonStart.apply {
                text = getString(
                    if (running) R.string.button_stop_service else R.string.button_start_service
                )
                backgroundTintList = ColorStateList.valueOf(
                    ContextCompat.getColor(
                        requireContext(),
                        if (running) R.color.red_800 else R.color.green_700
                    )
                )
            }
            renderStatus()
        }

        connectionService.status.observe(viewLifecycleOwner) {
            hasInternet = it
            renderStatus()
        }
    }

    override fun onResume() {
        super.onResume()
        renderStatus()
        renderSims()
    }

    /** SIM picker: phone default + every active SIM. The choice is used for all messages. */
    private fun renderSims() {
        val binding = _binding ?: return
        val group = binding.groupSim
        group.setOnCheckedChangeListener(null)
        group.removeAllViews()

        val sims = SubscriptionsHelper.getActiveSimCards(requireContext())
        binding.textSimHint.isVisible = sims.isEmpty()

        fun option(id: Int, label: String) = RadioButton(requireContext()).apply {
            this.id = id
            text = label
        }.also { group.addView(it) }

        option(SIM_AUTO_ID, getString(R.string.mdsms_sim_auto))
        sims.sortedBy { it.simNumber }.forEach { sim ->
            val name = listOfNotNull(sim.carrierName, sim.phoneNumber?.takeLast(4)?.let { "…$it" })
                .joinToString(" ")
                .ifEmpty { "—" }
            option(sim.simNumber, getString(R.string.mdsms_sim_item, sim.simNumber, name))
        }

        val selected = messagesSettings.preferredSimNumber
            ?.takeIf { n -> sims.any { it.simNumber == n } }
            ?: SIM_AUTO_ID
        group.check(selected)

        group.setOnCheckedChangeListener { _, checkedId ->
            messagesSettings.preferredSimNumber = checkedId.takeIf { it != SIM_AUTO_ID }
        }
    }

    private fun renderStatus() {
        val binding = _binding ?: return
        val info = gatewaySettings.registrationInfo

        binding.cardAccount.isVisible = info != null
        binding.textAccount.text = info?.login ?: getString(R.string.n_a)
        binding.textDeviceId.text = gatewaySettings.deviceId ?: getString(R.string.n_a)

        val (text, color) = when {
            info == null -> R.string.mdsms_status_not_linked to R.color.grey_700
            !hasInternet -> R.string.mdsms_status_no_internet to R.color.red_800
            !isRunning -> R.string.mdsms_status_stopped to R.color.red_800
            else -> R.string.mdsms_status_connected to R.color.green_700
        }
        binding.textStatus.text = getString(text)
        binding.textStatus.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), color))
    }

    private fun confirmUnlink() {
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.mdsms_unlink)
            .setMessage(R.string.mdsms_unlink_confirm)
            .setPositiveButton(R.string.mdsms_unlink) { _, _ -> unlink() }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    /**
     * Local unlink: stop sending and forget this phone's credentials. The server keeps the
     * old device record (no server-side detach); linking again creates a new device.
     */
    private fun unlink() {
        orchestratorSvc.stop(requireContext())
        gatewaySettings.registrationInfo = null
        gatewaySettings.fcmToken = null
        renderStatus()
        Toast.makeText(requireContext(), R.string.mdsms_unlinked, Toast.LENGTH_SHORT).show()
    }

    private fun actionStart(start: Boolean) {
        if (!start) {
            orchestratorSvc.stop(requireContext())
            return
        }

        if (registrationInFlight) return

        if (gatewaySettings.registrationInfo == null) {
            FirstStartDialogFragment.newInstance().show(parentFragmentManager, "signin")
            return
        }

        requestPermissionsAndStart()
    }

    private fun start() {
        orchestratorSvc.start(requireContext().applicationContext, false)
        requestIgnoreBatteryOptimizations()
    }

    private fun requestPermissionsAndStart() {
        val permissionsRequired =
            listOf(
                Manifest.permission.READ_PHONE_STATE,
                // MD SMS: send-only — no READ_SMS / RECEIVE_SMS / RECEIVE_MMS
                // (Play Protect blocks sideloaded apps that request them)
                Manifest.permission.SEND_SMS,
                Manifest.permission.READ_PHONE_NUMBERS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
                Manifest.permission.POST_NOTIFICATIONS.takeIf { Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU },
            )
                .filterNotNull()
                .filter {
                    ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
                }

        if (permissionsRequired.isEmpty()) {
            start()
            return
        }

        permissionsRequest.launch(permissionsRequired.toTypedArray())
    }

    private val permissionsRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result[Manifest.permission.SEND_SMS] == false) {
            Toast.makeText(
                requireContext(),
                R.string.mdsms_permissions_missing,
                Toast.LENGTH_LONG
            ).show()
        } else {
            Log.d(javaClass.name, "Permissions granted")
        }

        start()
        renderSims()
    }

    /** Step 6 of the merchant setup: keep Android from killing the app in the background. */
    @SuppressLint("BatteryLife")
    private fun requestIgnoreBatteryOptimizations() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val ctx = context ?: return
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(ctx.packageName)) return

        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:${ctx.packageName}"))
            )
        } catch (e: Exception) {
            Log.w(javaClass.name, "Battery optimization request not available", e)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val SIM_AUTO_ID = 1000

        @JvmStatic
        fun newInstance() = HomeFragment()
    }
}
