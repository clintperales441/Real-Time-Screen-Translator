package com.mangalens.ui.navigation

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mangalens.feature.screencapture.data.MediaProjectionDataSource
import com.mangalens.feature.screencapture.data.CapturePrefs
import com.mangalens.feature.screencapture.data.ScreenCaptureRepositoryImpl
import com.mangalens.feature.screencapture.data.SharedCaptureState
import com.mangalens.feature.screencapture.domain.StartCaptureUseCase
import com.mangalens.feature.screencapture.ui.ScreenCaptureViewModel
import com.mangalens.feature.ocr.data.MlKitOcrDataSource
import com.mangalens.feature.ocr.data.OcrRepositoryImpl
import com.mangalens.feature.ocr.domain.ProcessFrameUseCase
import com.mangalens.feature.ocr.ui.OcrViewModel
import com.mangalens.feature.translator.data.MlKitTranslationSource
import com.mangalens.feature.translator.data.TranslationRepositoryImpl
import com.mangalens.feature.translator.domain.TranslateTextUseCase
import com.mangalens.feature.translator.ui.TranslationViewModel
import com.mangalens.feature.overlay.data.OverlayRepositoryImpl
import com.mangalens.feature.overlay.domain.OverlayItem
import com.mangalens.feature.overlay.ui.OverlayViewModel
import com.mangalens.feature.history.data.HistoryRepositoryImpl
import com.mangalens.feature.history.data.TranslationDao
import com.mangalens.feature.history.domain.GetHistoryUseCase
import com.mangalens.feature.history.ui.HistoryViewModel
import com.mangalens.ui.screens.HomeScreen
import com.mangalens.ui.screens.PermissionScreen

@Composable
fun NavGraph() {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	var isOverlayGranted by rememberSaveable { mutableStateOf(false) }
	var isCaptureGranted by rememberSaveable { mutableStateOf(false) }
	var hasCapturePrompted by rememberSaveable { mutableStateOf(false) }

	val navController = rememberNavController()
	val dataSource = remember { MediaProjectionDataSource() }
	val repository = remember { ScreenCaptureRepositoryImpl(dataSource) }
	val captureViewModel = remember { ScreenCaptureViewModel(StartCaptureUseCase(repository), repository) }
	val ocrRepository = remember { OcrRepositoryImpl(MlKitOcrDataSource()) }
	val ocrViewModel = remember { OcrViewModel(ProcessFrameUseCase(ocrRepository)) }
	val translationRepository = remember { TranslationRepositoryImpl(MlKitTranslationSource()) }
	val translationViewModel = remember { TranslationViewModel(TranslateTextUseCase(translationRepository)) }
	val overlayRepository = remember { OverlayRepositoryImpl(context) }
	val overlayViewModel = remember { OverlayViewModel(overlayRepository) }
	val historyDao = remember { object : TranslationDao {
		private val storage = mutableListOf<com.mangalens.feature.history.data.TranslationEntity>()

		override fun getAll(): List<com.mangalens.feature.history.data.TranslationEntity> {
			return storage.toList()
		}

		override fun insert(entity: com.mangalens.feature.history.data.TranslationEntity) {
			storage.add(entity)
		}
	} }
	val historyRepository = remember { HistoryRepositoryImpl(historyDao) }
	val historyViewModel = remember { HistoryViewModel(GetHistoryUseCase(historyRepository)) }

	val overlayLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.StartActivityForResult()
	) {
		isOverlayGranted = Settings.canDrawOverlays(context)
	}

	val captureLauncher = rememberLauncherForActivityResult(
		contract = ActivityResultContracts.StartActivityForResult()
	) { result ->
		isCaptureGranted = result.resultCode == Activity.RESULT_OK
		if (isCaptureGranted && result.data != null) {
			captureViewModel.onStartCapture(context, result.resultCode, result.data!!)
			hasCapturePrompted = false
		} else {
			CapturePrefs.setWantsCapture(context, false)
			hasCapturePrompted = false
		}
	}

	DisposableEffect(lifecycleOwner) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_RESUME) {
				isOverlayGranted = Settings.canDrawOverlays(context)
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	NavHost(
		navController = navController,
		startDestination = Screen.Home.route
	) {
		composable(Screen.Home.route) {
			val isCapturing by captureViewModel.isCapturing
			val ocrState by ocrViewModel.state
			val translationState by translationViewModel.state
			val overlayState by overlayViewModel.state
			val historyState by historyViewModel.state
			HomeScreen(
				isCaptureGranted = isCaptureGranted,
				isCapturing = isCapturing,
				isOcrProcessing = ocrState.isProcessing,
				ocrResults = ocrState.results,
				isTranslating = translationState.isTranslating,
				translation = translationState.result,
				isOverlayVisible = overlayState.isVisible,
				overlayItems = overlayState.items,
				historyState = historyState,
				onStartCapture = {
					val canDrawOverlays = Settings.canDrawOverlays(context)
					if (!canDrawOverlays) {
						navController.navigate(Screen.Permissions.route)
						return@HomeScreen
					}
					
					val savedIntent = SharedCaptureState.captureIntent
					if (savedIntent != null && SharedCaptureState.resultCode != -1) {
						hasCapturePrompted = false
						captureViewModel.onStartCapture(context, SharedCaptureState.resultCode, savedIntent)
					} else {
						val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
						hasCapturePrompted = true
						captureLauncher.launch(manager.createScreenCaptureIntent())
					}
				},
				onStopCapture = {
					captureViewModel.onStopCapture(context)
				},
				onRunOcr = {},
				onRunTranslation = {},
				onShowOverlay = {
					val item = OverlayItem(
						id = "sample",
						text = "Good morning",
						x = 100,
						y = 120
					)
					overlayViewModel.show(listOf(item))
				},
				onHideOverlay = {
					overlayViewModel.hide()
				},
				onLoadHistory = {
					historyViewModel.load()
				},
				onOpenPermissions = {
					navController.navigate(Screen.Permissions.route)
				}
			)
		}
		composable(Screen.Permissions.route) {
			PermissionScreen(
				isOverlayGranted = isOverlayGranted,
				isCaptureGranted = isCaptureGranted,
				onRequestOverlay = {
					val uri = Uri.parse("package:${context.packageName}")
					val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, uri)
					overlayLauncher.launch(intent)
				},
				onRequestCapture = {
					val manager = context.getSystemService(MediaProjectionManager::class.java)
					val intent = manager?.createScreenCaptureIntent()
					if (intent != null) {
						captureLauncher.launch(intent)
					}
				},
				onBack = {
					navController.popBackStack()
				}
			)
		}
	}
}
