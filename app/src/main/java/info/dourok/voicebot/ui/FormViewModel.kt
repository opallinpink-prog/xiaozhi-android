package info.dourok.voicebot.ui

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import info.dourok.voicebot.NavigationEvents
import info.dourok.voicebot.R
import info.dourok.voicebot.UiState
import info.dourok.voicebot.data.FormRepository
import info.dourok.voicebot.data.FormResult
import info.dourok.voicebot.data.model.SelfHostConfig
import info.dourok.voicebot.data.model.ServerFormData
import info.dourok.voicebot.data.model.ServerType
import info.dourok.voicebot.data.model.TransportType
import info.dourok.voicebot.data.model.ValidationResult
import info.dourok.voicebot.data.model.XiaoZhiConfig
import info.dourok.voicebot.domain.SubmitFormUseCase
import info.dourok.voicebot.domain.ValidateFormUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val PREFS_NAME = "companionai_prefs"
private const val KEY_SERVER_TYPE = "server_type"
private const val KEY_XIAOZHI_WS_URL = "xiaozhi_ws_url"
private const val KEY_XIAOZHI_QTA_URL = "xiaozhi_qta_url"
private const val KEY_XIAOZHI_TRANSPORT = "xiaozhi_transport"
private const val KEY_SELFHOST_WS_URL = "selfhost_ws_url"

// Nota: cambiato da ViewModel a AndroidViewModel per accedere al Context
// senza dipendenze extra, così da leggere/scrivere SharedPreferences.
@HiltViewModel
class FormViewModel @Inject constructor(
    application: Application,
    private val validateFormUseCase: ValidateFormUseCase,
    private val submitFormUseCase: SubmitFormUseCase,
    private val repository: FormRepository,
    @NavigationEvents private val navigationEvents: MutableSharedFlow<String>
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _formState = MutableStateFlow(loadSavedFormData())
    val formState = _formState.asStateFlow()

    private val _validationResult = MutableStateFlow(ValidationResult(true))
    val validationResult = _validationResult.asStateFlow()

    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.resultFlow.collect {
                when (it) {
                    FormResult.SelfHostResult -> navigationEvents.emit("chat")
                    is FormResult.XiaoZhiResult -> it.otaResult?.let { ota ->
                        if (ota.activation != null) {
                            Log.d("FormViewModel", "activationFlow: $ota")
                            navigationEvents.emit("activation")
                        } else {
                            navigationEvents.emit("chat")
                        }
                    }
                    null -> {}
                }
            }
        }
    }

    // Legge le impostazioni salvate; se non esistono usa i default di ServerFormData
    private fun loadSavedFormData(): ServerFormData {
        if (!prefs.contains(KEY_SERVER_TYPE)) return ServerFormData()

        val serverType = ServerType.valueOf(
            prefs.getString(KEY_SERVER_TYPE, ServerType.XiaoZhi.name) ?: ServerType.XiaoZhi.name
        )
        val defaults = ServerFormData()
        val xiaoZhiConfig = XiaoZhiConfig(
            webSocketUrl = prefs.getString(KEY_XIAOZHI_WS_URL, defaults.xiaoZhiConfig.webSocketUrl) ?: defaults.xiaoZhiConfig.webSocketUrl,
            qtaUrl = prefs.getString(KEY_XIAOZHI_QTA_URL, defaults.xiaoZhiConfig.qtaUrl) ?: defaults.xiaoZhiConfig.qtaUrl,
            transportType = TransportType.valueOf(
                prefs.getString(KEY_XIAOZHI_TRANSPORT, defaults.xiaoZhiConfig.transportType.name) ?: defaults.xiaoZhiConfig.transportType.name
            )
        )
        val selfHostConfig = SelfHostConfig(
            webSocketUrl = prefs.getString(KEY_SELFHOST_WS_URL, defaults.selfHostConfig.webSocketUrl) ?: defaults.selfHostConfig.webSocketUrl
        )
        return ServerFormData(
            serverType = serverType,
            xiaoZhiConfig = xiaoZhiConfig,
            selfHostConfig = selfHostConfig
        )
    }

    // Salva le impostazioni correnti nelle SharedPreferences
    private fun saveFormData(data: ServerFormData) {
        prefs.edit()
            .putString(KEY_SERVER_TYPE, data.serverType.name)
            .putString(KEY_XIAOZHI_WS_URL, data.xiaoZhiConfig.webSocketUrl)
            .putString(KEY_XIAOZHI_QTA_URL, data.xiaoZhiConfig.qtaUrl)
            .putString(KEY_XIAOZHI_TRANSPORT, data.xiaoZhiConfig.transportType.name)
            .putString(KEY_SELFHOST_WS_URL, data.selfHostConfig.webSocketUrl)
            .apply()
    }

    fun updateServerType(serverType: ServerType) {
        _formState.update { it.copy(serverType = serverType) }
        validateForm()
    }

    fun updateXiaoZhiConfig(updater: XiaoZhiConfig) {
        _formState.update { it.copy(xiaoZhiConfig = updater) }
        validateForm()
    }

    fun updateSelfHostConfig(updater: SelfHostConfig) {
        _formState.update { it.copy(selfHostConfig = updater) }
        validateForm()
    }

    private fun validateForm() {
        _validationResult.value = validateFormUseCase(_formState.value)
    }

    fun submitForm() {
        viewModelScope.launch {
            val validation = validateFormUseCase(_formState.value)
            _validationResult.value = validation

            if (validation.isValid) {
                _uiState.value = UiState.Loading
                // Salva prima di connettersi, così le prefs sono sempre aggiornate
                saveFormData(_formState.value)
                val result = submitFormUseCase(_formState.value)
                _uiState.value = if (result.isSuccess) {
                    UiState.Success(getApplication<Application>().getString(R.string.msg_submit_success))
                } else {
                    UiState.Error(getApplication<Application>().getString(R.string.msg_submit_error))
                }
            }
        }
    }
}
