package dji.sampleV5.aircraft

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import dji.sampleV5.aircraft.databinding.ActivityMainBinding
import dji.sampleV5.aircraft.models.BaseMainActivityVm
import dji.sampleV5.aircraft.models.MSDKInfoVm
import dji.sampleV5.aircraft.models.MSDKManagerVM
import dji.sampleV5.aircraft.models.globalViewModels
import dji.sampleV5.aircraft.tests.General
import dji.sampleV5.aircraft.util.ToastUtils
import dji.v5.utils.common.LogUtils
import dji.v5.utils.common.PermissionUtil
import dji.v5.utils.common.StringUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable

import dji.sampleV5.aircraft.models.BasicAircraftControlVM
import dji.sampleV5.aircraft.models.VirtualStickVM
import dji.sampleV5.aircraft.models.SimulatorVM
import dji.sampleV5.aircraft.tests.config.MqttConfig.setHost
import dji.v5.utils.common.ThreadUtil.runOnUiThread

/**
 * Class Description
 *
 * @author Hoker
 * @date 2022/2/10
 *
 * Copyright (c) 2022, DJI All Rights Reserved.
 */
abstract class DJIMainActivity : AppCompatActivity() {

    val tag: String = LogUtils.getTag(this)
    private val permissionArray = arrayListOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    init {
        permissionArray.apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.READ_MEDIA_AUDIO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
                add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private val baseMainActivityVm: BaseMainActivityVm by viewModels()
    private val msdkInfoVm: MSDKInfoVm by viewModels()
    private val msdkManagerVM: MSDKManagerVM by globalViewModels()
    private lateinit var binding: ActivityMainBinding
    private val handler: Handler = Handler(Looper.getMainLooper())
    private val disposable = CompositeDisposable()

    private var general: General? = null

    abstract fun prepareUxActivity()
    abstract fun prepareTestingToolsActivity()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Some mobile phones may restart the main type activity when entering from the system desktop
        // Need to check this situation, basic app standard practice
        if (!isTaskRoot && intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
            finish()
            return
        }

        window.decorView.apply {
            systemUiVisibility =
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        }

        setupClickListeners()
        initMSDKInfoView()
        observeSDKManager()
        checkPermissionAndRequest()
    }

    @SuppressLint("SetTextI18n")
    private fun setupClickListeners() {
        // Handle General Test Button logic
        binding.btnStartGeneralTest.setOnClickListener {
            val g = general
            if (g == null) {
                binding.tvDebug.append("General test not initialized\n")
                return@setOnClickListener
            }

            // Get the IP from the EditText
            val brokerIp = binding.editBrokerIp.text.toString().trim()

            if (g.isRunning()) {
                // If already running, we just stop it
                g.stopTelemetryTest()
                binding.btnStartGeneralTest.text = "START TEST"
                binding.tvDebug.append("Test stopped.\n")
            } else {
                // If starting, validate the IP first using MqttConfig
                if (setHost(brokerIp)) {
                    binding.tvDebug.append("IP Validated: $brokerIp.\n")
                    g.startTelemetryTest()
                    binding.btnStartGeneralTest.text = "STOP TEST"
                } else {
                    // Handle invalid IP format
                    binding.tvDebug.append("ERROR: Invalid IP format ($brokerIp).\n")
                    ToastUtils.showToast("Invalid Broker IP Address")
                }
            }
        }

        // Handle pairing when clicking on base info panel
        binding.viewBaseInfo.setOnClickListener {
            baseMainActivityVm.doPairing {
                showToast(it)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun initMSDKInfoView() {
        msdkInfoVm.msdkInfo.observe(this) {
            binding.textViewVersion.text = StringUtils.getResStr(R.string.sdk_version, it.SDKVersion + " " + it.buildVer)
            binding.textViewProductName.text = StringUtils.getResStr(R.string.product_name, it.productType.name)
            binding.textViewPackageProductCategory.text = StringUtils.getResStr(R.string.package_product_category, it.packageProductCategory)
            binding.textViewIsDebug.text = StringUtils.getResStr(R.string.is_sdk_debug, it.isDebug)
            binding.textCoreInfo.text = it.coreInfo.toString()
        }

        // Removed old icon listeners as they are no longer in activity_main.xml
    }

    private fun observeSDKManager() {
        msdkManagerVM.lvRegisterState.observe(this) { resultPair ->
            val statusText: String?
            if (resultPair.first) {
                ToastUtils.showToast("Register Success")
                statusText = StringUtils.getResStr(this, R.string.registered)
                msdkInfoVm.initListener()

                // Initialize General Test module after successful registration
                initGeneralTestModule()

                handler.postDelayed({
                    prepareUxActivity()
                }, 5000)
            } else {
                showToast("Register Failure: ${resultPair.second}")
                statusText = StringUtils.getResStr(this, R.string.unregistered)
            }
            binding.textViewRegistered.text = StringUtils.getResStr(R.string.registration_status, statusText)
        }

        msdkManagerVM.lvProductConnectionState.observe(this) { resultPair ->
            val connected = resultPair.first
            val productName = resultPair.second
            ToastUtils.showToast("Product: $productName, ConnectionState: $connected")
        }

        msdkManagerVM.lvProductChanges.observe(this) { productId ->
            showToast("Product: $productId Changed")
        }

        msdkManagerVM.lvInitProcess.observe(this) { processPair ->
            showToast("Init Process event: ${processPair.first.name}")
        }

        msdkManagerVM.lvDBDownloadProgress.observe(this) { resultPair ->
            showToast("Database Download Progress current: ${resultPair.first}, total: ${resultPair.second}")
        }
    }

    private fun initGeneralTestModule() {
        val basicVM = ViewModelProvider(this)[BasicAircraftControlVM::class.java]
        val virtualStickVM = ViewModelProvider(this)[VirtualStickVM::class.java]
        val simulatorVM = ViewModelProvider(this)[SimulatorVM::class.java]

        general = General(
            basicAircraftControlVM = basicVM,
            virtualStickVM = virtualStickVM,
            simulatorVM = simulatorVM,
            context = this,
        ) { msg ->
            runOnUiThread {
                binding.tvDebug.append("$msg\n")
                binding.debugScroll.post {
                    binding.debugScroll.fullScroll(View.FOCUS_DOWN)
                }
            }
        }

        binding.btnStartGeneralTest.isEnabled = true
        binding.btnStartGeneralTest.text = "START TEST"
    }

    private fun showToast(content: String) {
        ToastUtils.showToast(content)
    }

    // --- Helper methods for enabling showcase buttons ---

    fun <T> enableDefaultLayout(cl: Class<T>) {
        enableShowCaseButton(binding.defaultLayoutButton, cl)
    }

    fun <T> enableWidgetList(cl: Class<T>) {
        enableShowCaseButton(binding.widgetListButton, cl)
    }

    fun <T> enableTestingTools(cl: Class<T>) {
        enableShowCaseButton(binding.testingToolButton, cl)
    }

    private fun <T> enableShowCaseButton(view: View, cl: Class<T>) {
        view.isEnabled = true
        view.setOnClickListener {
            Intent(this, cl).also {
                startActivity(it)
            }
        }
    }

    // --- Permission Handling ---

    private fun checkPermissionAndRequest() {
        if (!checkPermission()) {
            requestPermission()
        }
    }

    private fun checkPermission(): Boolean {
        for (i in permissionArray.indices) {
            if (!PermissionUtil.isPermissionGranted(this, permissionArray[i])) {
                return false
            }
        }
        return true
    }

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        result?.entries?.forEach {
            if (!it.value) {
                requestPermission()
                return@forEach
            }
        }
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(permissionArray.toArray(arrayOf()))
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    override fun onResume() {
        super.onResume()
        if (checkPermission()) {
            handleAfterPermissionPermitted()
        }
    }

    private fun handleAfterPermissionPermitted() {
        prepareTestingToolsActivity()
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        disposable.dispose()
    }
}
