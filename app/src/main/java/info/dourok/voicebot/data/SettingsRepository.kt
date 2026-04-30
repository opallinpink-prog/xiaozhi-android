package info.dourok.voicebot.data

import android.app.Application
import android.content.Context
import info.dourok.voicebot.data.model.MqttConfig
import info.dourok.voicebot.data.model.TransportType
import javax.inject.Inject
import javax.inject.Singleton

interface SettingsRepository {
    var transportType: TransportType
    var mqttConfig: MqttConfig?
    var webSocketUrl: String?
}

private const val PREFS_NAME = "companionai_prefs"
private const val KEY_SERVER_TYPE = "server_type"
private const val KEY_XIAOZHI_WS_URL = "xiaozhi_ws_url"
private const val KEY_SELFHOST_WS_URL = "selfhost_ws_url"
private const val KEY_XIAOZHI_TRANSPORT = "xiaozhi_transport"

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    application: Application
) : SettingsRepository {

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // transportType: inizializzato dai prefs salvati, default MQTT
    override var transportType: TransportType = run {
        val saved = prefs.getString(KEY_XIAOZHI_TRANSPORT, null)
        if (saved != null) {
            try { TransportType.valueOf(saved) } catch (e: Exception) { TransportType.MQTT }
        } else {
            TransportType.MQTT
        }
    }

    // mqttConfig: viene ancora popolato da FormRepositoryImpl dopo submitForm().
    // Al riavvio senza form, rimane null finché submitForm() non lo aggiorna.
    // ChatViewModel usa !! solo su MQTT — quindi serve che submitForm() venga
    // richiamato. Vedi MainActivity per la gestione del caso cold-start.
    override var mqttConfig: MqttConfig? = null

    // webSocketUrl: inizializzato dai prefs salvati
    override var webSocketUrl: String? = run {
        // Prende l'URL giusto in base al tipo di server salvato
        val serverType = prefs.getString(KEY_SERVER_TYPE, null)
        when (serverType) {
            "SelfHost" -> prefs.getString(KEY_SELFHOST_WS_URL, null)
            else       -> prefs.getString(KEY_XIAOZHI_WS_URL, null)
        }
    }
}
