package com.example.helloworld

import android.Manifest
import android.annotation.SuppressLint
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.amap.api.maps.AMap
import com.amap.api.maps.CameraUpdateFactory
import com.amap.api.maps.CoordinateConverter
import com.amap.api.maps.LocationSource
import com.amap.api.maps.MapView
import com.amap.api.maps.MapsInitializer
import com.amap.api.maps.model.LatLng
import com.amap.api.maps.model.MyLocationStyle
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.card.MaterialCardView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MapActivity : AppCompatActivity(), LocationSource {

    private lateinit var mapView: MapView
    private lateinit var aMap: AMap
    private var locationManager: LocationManager? = null
    private var mapLocationChangedListener: LocationSource.OnLocationChangedListener? = null
    private var centerOnNextFix = false

    private lateinit var tvSignalStrength: TextView
    private lateinit var cardSignal: MaterialCardView
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvLocationDetail: TextView
    private lateinit var tvLocationCoordinates: TextView
    private lateinit var tvLocationAccuracy: TextView
    private lateinit var tvLocationProvider: TextView
    private lateinit var tvLocationUpdated: TextView
    private lateinit var tvLocationHint: TextView
    private lateinit var tvHistoryEntryCount: TextView
    private lateinit var tvHistoryEntryHint: TextView
    private lateinit var cardHistoryEntry: MaterialCardView

    private var gnssStatusCallback: GnssStatus.Callback? = null
    private var satelliteDialog: BottomSheetDialog? = null
    private var historyDialog: AlertDialog? = null
    private var historyPanelDialog: AlertDialog? = null
    private var satelliteAdapter: SatelliteAdapter? = null
    private var historyPanelAdapter: HistoryAdapter? = null
    private var currentSatellites = mutableListOf<SatelliteInfo>()

    private var historyPanelCountView: TextView? = null
    private var historyPanelDetailView: TextView? = null
    private var historyPanelHintView: TextView? = null
    private var historyPanelEmptyView: LinearLayout? = null
    private var historyPanelActionView: TextView? = null
    private var historyPanelRecyclerView: RecyclerView? = null

    private var isRecording = false
    private var startTime: Long = 0
    private var totalDistance = 0.0
    private var lastLocation: Location? = null
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val recordingRunnable = object : Runnable {
        override fun run() {
            if (isRecording) {
                recordingHandler.postDelayed(this, 1000)
            }
        }
    }

    private val historyList = mutableListOf<HistoryItem>()
    private val defaultLatLng = LatLng(39.9042, 116.4074)
    private val timeFormatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val freshLocationListener = LocationListener { location ->
        handleLocationUpdate(location)
    }

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

        if (granted) {
            renderLocationPanelState(
                status = "定位中",
                detail = "权限已开启，正在等待下一次有效定位。",
                hint = "稍后会在这里显示你的实时位置。"
            )
            enableNativeLocation()
            centerOnNextFix = true
            requestFreshLocation()
            registerGnssCallback()
        } else {
            disableNativeLocation()
            renderLocationPanelState(
                status = "未授权",
                detail = "需要定位权限后，才能显示当前位置。",
                hint = "请允许 GPS 或网络定位权限后再试。"
            )
            Toast.makeText(this, R.string.location_permission_denied, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        supportActionBar?.hide()

        initializeAmapSdk()
        setContentView(R.layout.activity_map)

        mapView = findViewById(R.id.mapView)
        mapView.onCreate(savedInstanceState)
        aMap = mapView.map
        locationManager = getSystemService(LocationManager::class.java)

        bindViews()
        setupHistoryPanel()
        configureMap()
        initGnssStatusCallback()
        setupInsets()

        cardSignal.setOnClickListener { showSatelliteDialog() }
        findViewById<FloatingActionButton>(R.id.btnLocation).setOnClickListener {
            centerOnCurrentLocation()
        }
        cardHistoryEntry.setOnClickListener {
            showHistoryPanelDialog()
        }

        if (hasLocationPermission()) {
            enableNativeLocation()
            requestFreshLocation()
        } else {
            disableNativeLocation()
            renderLocationPanelState(
                status = "未授权",
                detail = "当前还没有定位权限。",
                hint = "点击定位按钮申请权限后即可显示当前位置。"
            )
        }
    }

    private fun bindViews() {
        tvSignalStrength = findViewById(R.id.tvSignalStrength)
        cardSignal = findViewById(R.id.cardSignal)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        tvLocationDetail = findViewById(R.id.tvLocationDetail)
        tvLocationCoordinates = findViewById(R.id.tvLocationCoordinates)
        tvLocationAccuracy = findViewById(R.id.tvLocationAccuracy)
        tvLocationProvider = findViewById(R.id.tvLocationProvider)
        tvLocationUpdated = findViewById(R.id.tvLocationUpdated)
        tvLocationHint = findViewById(R.id.tvLocationHint)
        tvHistoryEntryCount = findViewById(R.id.tvHistoryEntryCount)
        tvHistoryEntryHint = findViewById(R.id.tvHistoryEntryHint)
        cardHistoryEntry = findViewById(R.id.cardHistoryEntry)

        renderLocationPanelState(
            status = "定位中",
            detail = "正在获取最新的 GPS 或网络定位。",
            hint = "位置发生变化时，这里会自动刷新。"
        )
    }

    private fun setupHistoryPanel() {
        historyList.clear()
        historyList.addAll(loadInitialHistory())
        updateHistoryPanel()
    }

    private fun loadInitialHistory(): List<HistoryItem> {
        val savedItems = HistoryStorage.load(this)
        if (savedItems.isNotEmpty()) {
            return savedItems
        }

        val sampleItems = createSampleHistoryItems()
        HistoryStorage.save(this, sampleItems)
        return sampleItems
    }

    private fun createSampleHistoryItems(): List<HistoryItem> {
        val now = System.currentTimeMillis()
        return listOf(
            HistoryItem(
                id = 3,
                timestamp = now - 45 * 60 * 1000L,
                distanceKm = 3.26,
                durationSeconds = 26 * 60,
                averageSpeedKmh = 7.5,
                title = "滨河步道示例"
            ),
            HistoryItem(
                id = 2,
                timestamp = now - 2 * 60 * 60 * 1000L,
                distanceKm = 6.84,
                durationSeconds = 42 * 60,
                averageSpeedKmh = 9.8,
                title = "午后骑行示例"
            ),
            HistoryItem(
                id = 1,
                timestamp = now - 24 * 60 * 60 * 1000L,
                distanceKm = 1.92,
                durationSeconds = 18 * 60,
                averageSpeedKmh = 6.4,
                title = "回家路线示例"
            )
        ).sortedByDescending { it.timestamp }
    }

    private fun setupInsets() {
        val locationButton: FloatingActionButton = findViewById(R.id.btnLocation)

        ViewCompat.setOnApplyWindowInsetsListener(locationButton) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as MarginLayoutParams
            params.bottomMargin = systemBars.bottom + dpToPx(16)
            params.marginEnd = systemBars.right + dpToPx(16)
            view.layoutParams = params
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(cardSignal) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val params = view.layoutParams as MarginLayoutParams
            params.topMargin = systemBars.top + dpToPx(16)
            params.marginEnd = systemBars.right + dpToPx(16)
            view.layoutParams = params
            insets
        }
    }

    private fun initializeAmapSdk() {
        MapsInitializer.updatePrivacyShow(this, true, true)
        MapsInitializer.updatePrivacyAgree(this, true)
    }

    private fun configureMap() {
        aMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isMyLocationButtonEnabled = false
            isCompassEnabled = true
        }
        aMap.setLocationSource(this)
        aMap.myLocationStyle = MyLocationStyle()
            .myLocationType(MyLocationStyle.LOCATION_TYPE_LOCATION_ROTATE)
            .showMyLocation(true)

        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultLatLng, 11.5f))

        if (getString(R.string.amap_api_key) == "YOUR_AMAP_API_KEY") {
            Toast.makeText(this, R.string.amap_key_missing, Toast.LENGTH_LONG).show()
        }
    }

    private fun initGnssStatusCallback() {
        gnssStatusCallback = object : GnssStatus.Callback() {
            override fun onSatelliteStatusChanged(status: GnssStatus) {
                var usedInFixCount = 0
                var totalCn0DbHz = 0f
                val newSatellites = mutableListOf<SatelliteInfo>()

                for (i in 0 until status.satelliteCount) {
                    val usedInFix = status.usedInFix(i)
                    val cn0DbHz = status.getCn0DbHz(i)

                    newSatellites += SatelliteInfo(
                        svid = status.getSvid(i),
                        constellationType = status.getConstellationType(i),
                        cn0DbHz = cn0DbHz,
                        elevationDegrees = status.getElevationDegrees(i),
                        azimuthDegrees = status.getAzimuthDegrees(i),
                        usedInFix = usedInFix
                    )

                    if (usedInFix) {
                        usedInFixCount++
                        totalCn0DbHz += cn0DbHz
                    }
                }

                newSatellites.sortByDescending { it.cn0DbHz }
                currentSatellites = newSatellites
                satelliteAdapter?.updateData(currentSatellites)

                val averageCn0 = if (usedInFixCount > 0) totalCn0DbHz / usedInFixCount else 0f
                tvSignalStrength.text = when {
                    usedInFixCount == 0 -> "信号：未定位"
                    averageCn0 > 30f -> "信号：强（$usedInFixCount）"
                    averageCn0 > 20f -> "信号：中（$usedInFixCount）"
                    else -> "信号：弱（$usedInFixCount）"
                }
            }

            override fun onStarted() {
                tvSignalStrength.text = "信号：搜索中..."
            }

            override fun onStopped() {
                tvSignalStrength.text = "信号：已停止"
                currentSatellites.clear()
                satelliteAdapter?.updateData(currentSatellites)
            }
        }
    }

    private fun showSatelliteDialog() {
        if (satelliteDialog == null) {
            satelliteDialog = BottomSheetDialog(this)
            val view = layoutInflater.inflate(R.layout.dialog_satellite_info, null)
            val rvSatellites = view.findViewById<RecyclerView>(R.id.rvSatellites)
            rvSatellites.layoutManager = LinearLayoutManager(this)

            satelliteAdapter = SatelliteAdapter(currentSatellites)
            rvSatellites.adapter = satelliteAdapter
            satelliteDialog?.setContentView(view)
        }

        satelliteAdapter?.updateData(currentSatellites)
        satelliteDialog?.show()
    }

    private fun showHistoryPanelDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_history_panel, null)
        val recyclerView = view.findViewById<RecyclerView>(R.id.rvHistory)
        val countView = view.findViewById<TextView>(R.id.tvHistoryCount)
        val detailView = view.findViewById<TextView>(R.id.tvHistoryPanelDetail)
        val hintView = view.findViewById<TextView>(R.id.tvHistoryHint)
        val emptyView = view.findViewById<LinearLayout>(R.id.layoutHistoryEmpty)
        val actionView = view.findViewById<TextView>(R.id.btnHistoryEmptyAction)

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.setHasFixedSize(false)
        recyclerView.itemAnimator = null
        historyPanelAdapter = HistoryAdapter(historyList) { item, position ->
            Toast.makeText(this, "已打开：${item.displayTitle}", Toast.LENGTH_SHORT).show()
            showHistoryDetailDialog(item, position)
        }
        recyclerView.adapter = historyPanelAdapter

        historyPanelCountView = countView
        historyPanelDetailView = detailView
        historyPanelHintView = hintView
        historyPanelEmptyView = emptyView
        historyPanelActionView = actionView
        historyPanelRecyclerView = recyclerView

        actionView.setOnClickListener {
            Toast.makeText(
                this,
                "先在地图页移动一段时间，再返回或离开当前页面，系统就会自动保存新的轨迹记录。",
                Toast.LENGTH_LONG
            ).show()
        }

        historyPanelDialog?.dismiss()
        historyPanelDialog = MaterialAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton("关闭", null)
            .create().apply {
                setOnDismissListener { clearHistoryPanelDialogRefs() }
                setOnShowListener {
                    recyclerView.post {
                        updateHistoryPanel()
                        historyPanelAdapter?.notifyDataSetChanged()
                        recyclerView.scrollToPosition(0)
                        recyclerView.requestLayout()
                    }
                }
            }

        historyPanelDialog?.show()
    }

    private fun clearHistoryPanelDialogRefs() {
        historyPanelCountView = null
        historyPanelDetailView = null
        historyPanelHintView = null
        historyPanelEmptyView = null
        historyPanelActionView = null
        historyPanelRecyclerView = null
        historyPanelAdapter = null
        historyPanelDialog = null
    }

    private fun showHistoryDetailDialog(item: HistoryItem, position: Int) {
        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(item.displayTitle)
            .setMessage(
                buildString {
                    append(if (position == 0) "最近轨迹\n\n" else "历史轨迹\n\n")
                    append("时间：${item.formattedDateDetail}\n")
                    append("总里程：${item.formattedDistance}\n")
                    append("总时长：${item.formattedDurationDetail}\n")
                    append("平均速度：${item.formattedSpeed}\n\n")
                    append(
                        if (position == 0) {
                            "这是当前列表中最新完成的一条轨迹记录。"
                        } else {
                            "这是较早保存的一条轨迹记录。"
                        }
                    )
                }
            )
            .setPositiveButton("关闭", null)
            .setNeutralButton("重命名") { _, _ ->
                showRenameHistoryDialog(item)
            }
            .setNegativeButton("删除") { _, _ ->
                confirmDeleteHistory(item)
            }
            .create()

        historyDialog?.dismiss()
        historyDialog = dialog
        dialog.show()
    }

    private fun showRenameHistoryDialog(item: HistoryItem) {
        val input = EditText(this).apply {
            setText(item.title ?: item.displayTitle)
            setSelection(text.length)
            hint = "请输入轨迹名称"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("重命名轨迹")
            .setView(input)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ ->
                renameHistory(item, input.text?.toString().orEmpty().trim())
            }
            .show()
    }

    private fun confirmDeleteHistory(item: HistoryItem) {
        MaterialAlertDialogBuilder(this)
            .setTitle("删除轨迹")
            .setMessage("删除后将无法恢复，确定要删除“${item.displayTitle}”吗？")
            .setNegativeButton("取消", null)
            .setPositiveButton("删除") { _, _ ->
                deleteHistory(item)
            }
            .show()
    }

    private fun renameHistory(item: HistoryItem, newTitle: String) {
        val index = historyList.indexOfFirst { it.id == item.id }
        if (index == -1) return

        historyList[index] = historyList[index].copy(title = newTitle.ifBlank { null })
        saveHistoryData()
        updateHistoryPanel()
        Toast.makeText(this, "轨迹名称已更新", Toast.LENGTH_SHORT).show()
    }

    private fun deleteHistory(item: HistoryItem) {
        val index = historyList.indexOfFirst { it.id == item.id }
        if (index == -1) return

        historyList.removeAt(index)
        saveHistoryData()
        updateHistoryPanel()
        Toast.makeText(this, "轨迹已删除", Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssCallback() {
        if (hasLocationPermission()) {
            gnssStatusCallback?.let {
                locationManager?.registerGnssStatusCallback(it, Handler(Looper.getMainLooper()))
            }
        }
    }

    private fun unregisterGnssCallback() {
        gnssStatusCallback?.let {
            locationManager?.unregisterGnssStatusCallback(it)
        }
    }

    private fun centerOnCurrentLocation() {
        if (hasLocationPermission()) {
            renderLocationPanelState(
                status = "定位中",
                detail = "正在刷新当前位置。",
                hint = "获取到下一次有效定位后，地图会自动移动过去。"
            )
            enableNativeLocation()
            centerOnNextFix = true
            requestFreshLocation()
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        return fineGranted || coarseGranted
    }

    @SuppressLint("MissingPermission")
    private fun enableNativeLocation() {
        aMap.isMyLocationEnabled = true
    }

    private fun disableNativeLocation() {
        aMap.isMyLocationEnabled = false
        stopLocationUpdates()
    }

    @SuppressLint("MissingPermission")
    private fun requestFreshLocation() {
        val manager = locationManager ?: return
        val providers = collectEnabledProviders(manager)

        if (providers.isEmpty()) {
            renderLocationPanelState(
                status = "未开启",
                detail = "系统定位服务当前未开启。",
                hint = "请先打开 GPS 或网络定位后再试。"
            )
            Toast.makeText(this, R.string.location_service_disabled, Toast.LENGTH_SHORT).show()
            return
        }

        stopLocationUpdates()

        providers.forEach { provider ->
            manager.requestLocationUpdates(
                provider,
                1000L,
                1f,
                freshLocationListener,
                Looper.getMainLooper()
            )
        }
    }

    private fun collectEnabledProviders(manager: LocationManager): List<String> {
        val providers = mutableListOf<String>()
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        if (hasFineLocation && manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers += LocationManager.GPS_PROVIDER
        }
        if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers += LocationManager.NETWORK_PROVIDER
        }
        if (providers.isEmpty() && manager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)) {
            providers += LocationManager.PASSIVE_PROVIDER
        }

        return providers
    }

    private fun handleLocationUpdate(location: Location) {
        val converter = CoordinateConverter(this)
        converter.from(CoordinateConverter.CoordType.GPS)
        converter.coord(LatLng(location.latitude, location.longitude))
        val gcj02LatLng = converter.convert()

        val gcj02Location = Location(location).apply {
            latitude = gcj02LatLng.latitude
            longitude = gcj02LatLng.longitude
        }

        mapLocationChangedListener?.onLocationChanged(gcj02Location)
        updateLocationPanel(location, gcj02LatLng)

        if (centerOnNextFix) {
            centerOnNextFix = false
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(gcj02LatLng, 16f))
        }

        if (!isRecording) {
            startRecording()
        }
        updateTrack(location)
    }

    private fun updateLocationPanel(location: Location, gcj02LatLng: LatLng) {
        val accuracy = if (location.hasAccuracy()) location.accuracy else null
        val provider = providerLabel(location.provider)
        val status = when {
            accuracy == null -> "实时"
            accuracy <= 10f -> "精准"
            accuracy <= 25f -> "可用"
            else -> "实时"
        }
        val coordinates = String.format(
            Locale.US,
            "%.6f\n%.6f",
            gcj02LatLng.latitude,
            gcj02LatLng.longitude
        )
        val accuracyText = accuracy?.let {
            String.format(Locale.getDefault(), "\u00B1%.0f米", it)
        } ?: "--"
        val updatedAt = timeFormatter.format(
            Date(location.time.takeIf { it > 0 } ?: System.currentTimeMillis())
        )
        val hint = if (location.hasSpeed() && location.speed > 0f) {
            String.format(
                Locale.getDefault(),
                "当前速度 %.1f km/h，收到新定位后这里会自动刷新。",
                location.speed * 3.6f
            )
        } else {
            "每次收到新的定位结果，这里都会自动更新。"
        }

        renderLocationPanelState(
            status = status,
            detail = "$provider 定位，已转换为 GCJ-02 坐标",
            coordinates = coordinates,
            accuracy = accuracyText,
            provider = provider,
            updated = updatedAt,
            hint = hint
        )
    }

    private fun renderLocationPanelState(
        status: String,
        detail: String,
        coordinates: String = "--.------\n--.------",
        accuracy: String = "--",
        provider: String = "--",
        updated: String = "--",
        hint: String
    ) {
        tvLocationStatus.text = status
        tvLocationDetail.text = detail
        tvLocationCoordinates.text = coordinates
        tvLocationAccuracy.text = accuracy
        tvLocationProvider.text = provider
        tvLocationUpdated.text = updated
        tvLocationHint.text = hint
    }

    private fun providerLabel(provider: String?): String {
        return when (provider?.lowercase(Locale.getDefault())) {
            LocationManager.GPS_PROVIDER -> "GPS"
            LocationManager.NETWORK_PROVIDER -> "网络"
            LocationManager.PASSIVE_PROVIDER -> "被动"
            else -> provider?.uppercase(Locale.getDefault()) ?: "--"
        }
    }

    private fun updateTrack(location: Location) {
        val previousLocation = lastLocation
        if (previousLocation != null) {
            totalDistance += previousLocation.distanceTo(location) / 1000.0
        }
        lastLocation = location
    }

    private fun updateHistoryPanel() {
        historyPanelAdapter?.notifyDataSetChanged()

        val count = historyList.size
        val hasItems = count > 0
        val latest = historyList.firstOrNull()

        tvHistoryEntryCount.text = "$count 条记录"
        tvHistoryEntryHint.text = if (latest != null) {
            "最近：${latest.formattedDateTitle}"
        } else {
            "点击查看轨迹列表"
        }

        historyPanelCountView?.text = "$count 条记录"
        historyPanelRecyclerView?.visibility = if (hasItems) View.VISIBLE else View.GONE
        historyPanelEmptyView?.visibility = if (hasItems) View.GONE else View.VISIBLE
        historyPanelDetailView?.text = if (latest != null) {
            "最近一条轨迹完成于 ${latest.formattedDateTitle}"
        } else {
            "暂无轨迹记录，开始移动后会自动生成。"
        }
        historyPanelHintView?.text = if (latest != null) {
            "点击任意轨迹卡片即可查看详情、重命名或删除。当前最近一条：${latest.summary}"
        } else {
            "新的轨迹记录完成后，会自动追加到列表顶部。"
        }
    }

    private fun startRecording() {
        isRecording = true
        startTime = System.currentTimeMillis()
        totalDistance = 0.0
        lastLocation = null
        recordingHandler.post(recordingRunnable)
    }

    private fun stopRecording(saveIfUseful: Boolean) {
        if (!isRecording) return

        isRecording = false
        recordingHandler.removeCallbacks(recordingRunnable)

        val durationSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
        val averageSpeed = if (durationSeconds > 0) {
            totalDistance / (durationSeconds / 3600.0)
        } else {
            0.0
        }
        val shouldSave = saveIfUseful && (durationSeconds >= 15 || totalDistance >= 0.02)

        if (shouldSave) {
            historyList.add(
                0,
                HistoryItem(
                    id = nextHistoryId(),
                    timestamp = System.currentTimeMillis(),
                    distanceKm = totalDistance,
                    durationSeconds = durationSeconds,
                    averageSpeedKmh = averageSpeed
                )
            )
            saveHistoryData()
            updateHistoryPanel()
            historyPanelRecyclerView?.scrollToPosition(0)
        }

        totalDistance = 0.0
        lastLocation = null
    }

    private fun nextHistoryId(): Long {
        return (historyList.maxOfOrNull { it.id } ?: 0L) + 1L
    }

    private fun saveHistoryData() {
        HistoryStorage.save(this, historyList)
    }

    private fun stopLocationUpdates() {
        locationManager?.removeUpdates(freshLocationListener)
    }

    override fun activate(listener: LocationSource.OnLocationChangedListener) {
        mapLocationChangedListener = listener
    }

    override fun deactivate() {
        mapLocationChangedListener = null
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        registerGnssCallback()
    }

    override fun onPause() {
        unregisterGnssCallback()
        stopLocationUpdates()
        stopRecording(saveIfUseful = true)
        mapView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        stopLocationUpdates()
        mapLocationChangedListener = null
        satelliteDialog?.dismiss()
        historyDialog?.dismiss()
        historyPanelDialog?.dismiss()
        mapView.onDestroy()
        super.onDestroy()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
