// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.ui.firstuse

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import nl.pe1mew.zangbundel.app.R
import nl.pe1mew.zangbundel.app.ZangbundelApp
import nl.pe1mew.zangbundel.app.ui.ConfigurationScreen
import nl.pe1mew.zangbundel.app.ui.textPanel

/**
 * The welcome screen (guiDesign D-04 to D-06): the icon artwork, the name and the version, nothing
 * else; for at most 1.5 s or until tapped, once per process. Name and version fade in over 300 ms.
 */
@Composable
fun WelcomeScreen(onDone: (firstUse: Boolean) -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val scope = rememberCoroutineScope()
    val fade = remember { Animatable(0f) }
    val left = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    fun done() {
        if (!left.compareAndSet(false, true)) return
        scope.launch { container.welcomed = true; onDone(!container.storage.settings.firstUseDone.first()) }
    }
    LaunchedEffect(Unit) {
        fade.animateTo(1f, tween(FADE_MS))
        delay(SHOWN_MS - FADE_MS)
        done()
    }
    ConfigurationScreen {
        Column(
            Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { done() },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // The launcher foreground layer keeps its artwork small in the middle: 220 dp draws it at about 96 dp (D-04).
            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, modifier = Modifier.size(220.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall, modifier = Modifier.alpha(fade.value))
            Text(stringResource(R.string.welcome_version, container.versionName), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.alpha(fade.value))
        }
    }
}

/**
 * The first-use introduction (guiDesign D-07, FR-GEN-06): leader and singer, how to join, where songs
 * come from; the privacy screen is reachable from here before any permission is asked (FR-GEN-08).
 */
@Composable
fun IntroScreen(onPrivacy: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    val container = remember { (context.applicationContext as ZangbundelApp).container }
    val scope = rememberCoroutineScope()
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.intro_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for (text in listOf(R.string.intro_leader, R.string.intro_singer, R.string.intro_songs)) {
                    Text(stringResource(text), modifier = Modifier.fillMaxWidth().textPanel().padding(16.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onPrivacy, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.intro_privacy)) }
            Spacer(Modifier.height(8.dp))
            Button(onClick = { scope.launch { container.storage.settings.setFirstUseDone(); onDone() } }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text(stringResource(R.string.intro_start))
            }
        }
    }
}

/**
 * The privacy screen (FR-GEN-08, assessment SP-07): in plain words, readable by a 12-year-old, what
 * the app sends, what it never sends, what it keeps and how to delete it. It says what
 * `docs/privacy-policy.md` says; change both together.
 */
@Composable
fun PrivacyScreen() {
    ConfigurationScreen {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Text(stringResource(R.string.privacy_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))
            Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                for ((head, body) in listOf(
                    R.string.privacy_sends_title to R.string.privacy_sends,
                    R.string.privacy_never_title to R.string.privacy_never,
                    R.string.privacy_internet_title to R.string.privacy_internet,
                    R.string.privacy_keeps_title to R.string.privacy_keeps,
                    R.string.privacy_delete_title to R.string.privacy_delete,
                    R.string.privacy_location_title to R.string.privacy_location,
                )) {
                    Column(Modifier.fillMaxWidth().textPanel().padding(16.dp)) {
                        Text(stringResource(head), style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(stringResource(body))
                    }
                }
            }
        }
    }
}

private const val SHOWN_MS = 1_500L
private const val FADE_MS = 300
