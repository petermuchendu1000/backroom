package com.ongero.app.ui.redesign.flow

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import com.ongero.app.BuildConfig
import com.ongero.app.data.PreferencesManager
import com.ongero.app.ui.redesign.screens.HelpScreen
import com.ongero.app.ui.redesign.screens.SettingsScreen
import com.ongero.app.ui.screens.help.OngeroFaq
import com.ongero.app.ui.screens.help.KenyaCrisisResources

/**
 * Settings, bound to [PreferencesManager].
 *
 * Export and delete keep the confirm-then-act shape of the original, including the
 * toast that reports what happened; the dialog copy is the screen's own.
 */
@Composable
fun RedesignSettings(
    preferencesManager: PreferencesManager,
    onBack: () -> Unit,
    onBoundaries: () -> Unit,
    onBlockedUsers: () -> Unit,
    onSubscription: () -> Unit,
) {
    val context = LocalContext.current
    val previews by preferencesManager.incomingPreviewsFlow.collectAsState()
    val tips by preferencesManager.tipsRemindersFlow.collectAsState()

    SettingsScreen(
        blockedCount = preferencesManager.blockedUsers.size,
        previewsEnabled = previews,
        tipsEnabled = tips,
        subscriptionStatus = if (preferencesManager.isSubscriptionActive()) {
            preferencesManager.subscriptionTier
        } else {
            null
        },
        versionName = "Version ${BuildConfig.VERSION_NAME}",
        onBack = onBack,
        onBoundaries = onBoundaries,
        onBlockedUsers = onBlockedUsers,
        onSubscription = onSubscription,
        onPreviewsChanged = { preferencesManager.incomingPreviewsEnabled = it },
        onTipsChanged = { preferencesManager.tipsRemindersEnabled = it },
        onExportData = {
            val data = preferencesManager.exportUserData()
            Toast.makeText(context, "Exported ${data.size} items", Toast.LENGTH_LONG).show()
        },
        onDeleteAccount = {
            preferencesManager.clear()
            Toast.makeText(context, "Account deleted", Toast.LENGTH_LONG).show()
        },
    )
}

/**
 * Help, reading the same verified resource list as the original screen.
 *
 * ACTION_DIAL rather than ACTION_CALL, as before: it opens the dialer with the number
 * filled in, needs no CALL_PHONE permission, and leaves the last step to the person.
 */
@Composable
fun RedesignHelp(onBack: () -> Unit) {
    val context = LocalContext.current

    HelpScreen(
        crisis = KenyaCrisisResources.primaryResources,
        more = KenyaCrisisResources.specializedResources,
        faqs = OngeroFaq.allFaqs,
        onCall = { phone ->
            context.startActivity(
                Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") },
            )
        },
        onBack = onBack,
    )
}
