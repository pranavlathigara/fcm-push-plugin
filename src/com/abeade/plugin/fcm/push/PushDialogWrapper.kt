package com.abeade.plugin.fcm.push

import com.abeade.plugin.fcm.push.SettingsManager.Companion.DEFAULT_PREFERENCE
import com.abeade.plugin.fcm.push.SettingsManager.Companion.PREFERENCE_KEY
import com.abeade.plugin.fcm.push.stetho.StethoPreferenceSearcher
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.layout.panel
import gherkin.deps.com.google.gson.JsonParseException
import gherkin.deps.com.google.gson.JsonParser
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea
import org.fife.ui.rsyntaxtextarea.SyntaxConstants
import org.fife.ui.rtextarea.RTextScrollPane
import java.awt.Dimension
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JTextField


class PushDialogWrapper(private val propertiesComponent: PropertiesComponent) : DialogWrapper(true) {

    companion object {

        private const val FIREBASE_KEY = "#com.abeade.plugin.fcm.push.pushdialog.firebase"
        private const val DATA_KEY = "#com.abeade.plugin.fcm.push.pushdialog.data"
        private const val SAVE_KEY = "#com.abeade.plugin.fcm.push.pushdialog.save"
        private const val DIMENSION_SERVICE_KEY = "#com.abeade.plugin.fcm.push.pushdialog"
    }

    init {
        init()
        title = "FCM push sender"
    }

    val pushData: PushData?
        get() = data

    private var data: PushData? = null

    private lateinit var firebaseIdField: JTextField
    private lateinit var dataField: RSyntaxTextArea
    private lateinit var rememberCheckBox: JCheckBox

    override fun getDimensionServiceKey(): String? = DIMENSION_SERVICE_KEY

    override fun createCenterPanel(): JComponent {
        val firebaseIdFromSharedPreference = discoverFirebaseIdUsingStetho()
        val firebaseId = firebaseIdFromSharedPreference ?: propertiesComponent.getValue(PushDialogWrapper.FIREBASE_KEY).orEmpty()
        val data = propertiesComponent.getValue(PushDialogWrapper.DATA_KEY).orEmpty()
        val saveKey = propertiesComponent.getBoolean(SAVE_KEY)
        firebaseIdField = JTextField(firebaseId)
        dataField = RSyntaxTextArea(data).apply {
            syntaxEditingStyle = SyntaxConstants.SYNTAX_STYLE_JSON
            isCodeFoldingEnabled = true
            isAutoIndentEnabled = true
            tabSize = 2
        }
        rememberCheckBox = JCheckBox().apply { isSelected = saveKey }

        return panel {
            row("Firebase ID") {
                firebaseIdField(pushX)
                button("Reload") { reloadFirebaseIdFromStetho() }
            }
            row {
                cell { JLabel("Data").apply { verticalAlignment = JLabel.TOP }(push, grow) }
                cell { RTextScrollPane(dataField)(grow, grow) }
            }
            row("Remember") { rememberCheckBox() }
        }.apply { minimumSize = Dimension(600, 200) }
    }

    private fun discoverFirebaseIdUsingStetho(): String? {
        val preferenceKey = propertiesComponent.getValue(PREFERENCE_KEY) ?: DEFAULT_PREFERENCE
        return try {
            StethoPreferenceSearcher().getSharedPreference(preferenceKey)
        } catch (e: Exception) {
            Notifications.Bus.notify(
                Notification(
                    "FCM push sender",
                    "FCM push sender",
                    e.toString(),
                    NotificationType.ERROR
                )
            )
            null
        }
    }

    private fun reloadFirebaseIdFromStetho() {
        discoverFirebaseIdUsingStetho()?.let {
            firebaseIdField.text = it
        }
    }

    override fun doOKAction() {
        data = PushData(firebaseIdField.text, JsonParser().parse(dataField.text).toString())
        val remember = rememberCheckBox.isSelected
        propertiesComponent.setValue(FIREBASE_KEY, if (remember) firebaseIdField.text else null)
        propertiesComponent.setValue(DATA_KEY, if (remember) dataField.text else null)
        propertiesComponent.setValue(SAVE_KEY, remember)
        super.doOKAction()
    }

    override fun doValidate(): ValidationInfo? {
        return when {
            firebaseIdField.text.isBlank() -> ValidationInfo("Firebase Id required", firebaseIdField)
            dataField.text.isBlank() -> ValidationInfo("Data field required", dataField)
            !isValidJson() -> ValidationInfo("Data should be a valid JSON", dataField)
            else -> null
        }
    }

    private fun isValidJson() = try {
        JsonParser().parse(dataField.text)
        true
    } catch (e: JsonParseException) {
        false
    }
}
