package com.example.campusexplorer.ui.account

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.campusexplorer.data.AccountRepository
import com.example.campusexplorer.data.FeedPost
import com.example.campusexplorer.data.PhotoRepository
import com.example.campusexplorer.data.USERNAME_MAX
import com.example.campusexplorer.data.USERNAME_MIN
import com.example.campusexplorer.ui.CampusRed
import com.example.campusexplorer.ui.LightGreen
import com.example.campusexplorer.ui.LocalSession
import com.example.campusexplorer.ui.Turquoise
import com.example.campusexplorer.ui.feed.FeedMuted
import com.example.campusexplorer.ui.feed.FeedText
import com.example.campusexplorer.ui.feed.feedFieldColors
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

// Label for this file's messages in Logcat.
private const val TAG = "LoginScreen"

// The Log In / Sign Up square's screen: logging in or signing up for guests, your profile once
// you're logged in, and a username step for an account whose sign-up stopped partway.
@Composable
fun AccountScreen(
    onMessage: (String) -> Unit,

    // Opens one of your posts in the feed (from the grid on your profile).
    onOpenPost: (FeedPost) -> Unit,
    modifier: Modifier = Modifier,
) {
    val session = LocalSession.current
    val uid = session.uid
    when {
        uid == null || (session.isLoggedIn && !session.profileChecked) -> Box(
            modifier.fillMaxSize().background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Turquoise)
        }
        session.isGuest -> LoginScreen(modifier)
        session.needsUsername -> FinishSignUp(modifier)
        else -> ProfileScreen(uid = uid, onMessage = onMessage, onOpenPost = onOpenPost, modifier = modifier)
    }
}

@Composable
private fun LoginScreen(modifier: Modifier) {
    val session = LocalSession.current
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    var signingUp by rememberSaveable { mutableStateOf(false) }
    var username by rememberSaveable { mutableStateOf("") }

    // Passwords aren't kept when the screen is rebuilt (for example, after rotating).
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var bringPins by rememberSaveable { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // How many pins this guest has saved, to say what happens to them.
    val guestPins by remember(session.uid) {
        val uid = session.uid
        if (uid == null) flowOf(0) else PhotoRepository.observeOwnPhotos(uid).map { it.size }.catch { emit(0) }
    }.collectAsStateWithLifecycle(0)

    val nameCheck = rememberNameCheck(username, enabled = signingUp)
    val confirmProblem = if (signingUp && confirm.isNotEmpty() && confirm != password) "Passwords don't match." else null

    fun submit() {
        if (busy) return
        error = when {
            username.isBlank() -> "Enter a username."
            password.isEmpty() -> "Enter a password."
            signingUp && confirm != password -> "Passwords don't match."
            signingUp && nameCheck == NameCheck.Taken -> "“${username.trim()}” is already taken. Try another username."
            else -> null
        }
        if (error != null) return
        focusManager.clearFocus()
        busy = true
        scope.launch {
            try {
                val name = username.trim()
                // Both say how it went with a notice (this screen closes once you're logged in).
                if (signingUp) {
                    AccountRepository.signUp(name, password)
                } else {
                    AccountRepository.logIn(name, password, bringGuestPins = bringPins)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, if (signingUp) "Sign-up failed" else "Log-in failed", e)
                error = AccountRepository.errorMessage(e, if (signingUp) "Couldn't sign up." else "Couldn't log in.")
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            if (signingUp) "Join Nature Network" else "Welcome back",
            color = FeedText,
            fontFamily = FontFamily.Serif,
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold,
        )

        // What an account adds over guest mode.
        Column(
            Modifier
                .fillMaxWidth()
                .background(LightGreen, RoundedCornerShape(14.dp))
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("You're exploring as a guest", color = FeedText, fontWeight = FontWeight.SemiBold)
            Text(
                "Guests can take photos, save pins, and see everyone's posts. With an account you can " +
                    "also post, comment, like, follow people, add friends, and send messages.",
                color = FeedMuted,
                fontSize = 14.sp,
                lineHeight = 19.sp,
            )
        }

        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("Log In", "Sign Up").forEachIndexed { index, label ->
                SegmentedButton(
                    selected = signingUp == (index == 1),
                    onClick = {
                        signingUp = index == 1
                        error = null
                    },
                    enabled = !busy,
                    shape = SegmentedButtonDefaults.itemShape(index, 2),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Turquoise,
                        activeContentColor = Color.White,
                        activeBorderColor = Turquoise,
                        inactiveBorderColor = Turquoise,
                        inactiveContentColor = Turquoise,
                    ),
                ) { Text(label) }
            }
        }

        UsernameField(
            value = username,
            onValueChange = {
                username = it
                error = null
            },
            check = if (signingUp) nameCheck else NameCheck.Idle,
            enabled = !busy,
            imeAction = ImeAction.Next,
            showRules = signingUp,
        )

        PasswordField(
            value = password,
            onValueChange = {
                password = it
                error = null
            },
            label = "Password",
            enabled = !busy,
            supporting = if (signingUp) "At least 6 characters." else null,
            imeAction = if (signingUp) ImeAction.Next else ImeAction.Done,
            onDone = ::submit,
        )

        if (signingUp) {
            PasswordField(
                value = confirm,
                onValueChange = {
                    confirm = it
                    error = null
                },
                label = "Confirm password",
                enabled = !busy,
                problem = confirmProblem,
                imeAction = ImeAction.Done,
                onDone = ::submit,
            )
        }

        // What happens to pins saved as a guest.
        if (guestPins > 0) {
            if (signingUp) {
                Text(
                    "Your $guestPins guest ${if (guestPins == 1) "pin stays" else "pins stay"} with your new account.",
                    color = FeedMuted,
                    fontSize = 14.sp,
                )
            } else {
                Row(
                    Modifier.toggleable(
                        value = bringPins,
                        enabled = !busy,
                        role = Role.Checkbox,
                        onValueChange = { bringPins = it },
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = bringPins,
                        onCheckedChange = null,
                        enabled = !busy,
                        colors = CheckboxDefaults.colors(checkedColor = Turquoise),
                    )
                    Text(
                        "Move my $guestPins guest ${if (guestPins == 1) "pin" else "pins"} to this account",
                        color = FeedText,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }

        Button(
            onClick = ::submit,
            enabled = !busy,
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            if (busy) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            } else {
                Text(if (signingUp) "Create Account" else "Log In", fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        TextButton(
            onClick = {
                signingUp = !signingUp
                error = null
            },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
            Text(
                if (signingUp) "Already have an account? Log in" else "New here? Create an account",
                color = Turquoise,
            )
        }
    }
}

// For an account whose sign-up stopped before a username was saved.
@Composable
private fun FinishSignUp(modifier: Modifier) {
    val scope = rememberCoroutineScope()
    var username by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nameCheck = rememberNameCheck(username, enabled = true)

    fun save() {
        if (busy) return
        busy = true
        error = null
        scope.launch {
            try {
                AccountRepository.finishSignUp(username)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Couldn't save username", e)
                error = AccountRepository.errorMessage(e, "Couldn't save your username.")
            } finally {
                busy = false
            }
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Choose a username", color = FeedText, fontFamily = FontFamily.Serif, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Your account is almost ready. Pick the username people will see on your posts.",
            color = FeedMuted,
        )
        UsernameField(
            value = username,
            onValueChange = {
                username = it
                error = null
            },
            check = nameCheck,
            enabled = !busy,
            imeAction = ImeAction.Done,
            onDone = ::save,
        )
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 14.sp) }
        Button(
            onClick = ::save,
            enabled = !busy && nameCheck != NameCheck.Taken && username.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
            modifier = Modifier.fillMaxWidth().height(50.dp),
        ) {
            Text(if (busy) "Saving…" else "Save username", fontSize = 17.sp)
        }
        TextButton(
            onClick = { scope.launch { AccountRepository.signOut() } },
            enabled = !busy,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        ) { Text("Log out", color = FeedMuted) }
    }
}

// Whether a username can be used, checked as you type.
sealed interface NameCheck {
    data object Idle : NameCheck
    data class Problem(val message: String) : NameCheck
    data object Checking : NameCheck
    data object Available : NameCheck
    data object Taken : NameCheck

    // Couldn't reach the server to check. Saving will check again.
    data object Unknown : NameCheck
}

// Checks a username a moment after typing stops. Your own current username (unchangedFrom) is fine.
@Composable
fun rememberNameCheck(name: String, enabled: Boolean, unchangedFrom: String? = null): NameCheck {
    var check by remember { mutableStateOf<NameCheck>(NameCheck.Idle) }
    LaunchedEffect(name, enabled) {
        val trimmed = name.trim()
        if (!enabled || trimmed.isEmpty() || trimmed.equals(unchangedFrom, ignoreCase = true)) {
            check = NameCheck.Idle
            return@LaunchedEffect
        }
        val problem = AccountRepository.usernameProblem(trimmed)
        if (problem != null) {
            check = NameCheck.Problem(problem)
            return@LaunchedEffect
        }
        check = NameCheck.Checking
        delay(450)
        check = try {
            if (AccountRepository.isUsernameAvailable(trimmed)) NameCheck.Available else NameCheck.Taken
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't check username", e)
            NameCheck.Unknown
        }
    }
    return check
}

@Composable
fun UsernameField(
    value: String,
    onValueChange: (String) -> Unit,
    check: NameCheck,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit = {},

    // Whether to explain what a username can contain (when choosing one, not when logging in).
    showRules: Boolean = true,
) {
    val name = value.trim()
    val (note, color) = when (check) {
        NameCheck.Idle -> null to FeedMuted
        is NameCheck.Problem -> check.message to MaterialTheme.colorScheme.error
        NameCheck.Checking -> "Checking “$name”…" to FeedMuted
        NameCheck.Available -> "“$name” is available." to Turquoise
        NameCheck.Taken -> "“$name” is already taken." to CampusRed
        NameCheck.Unknown -> "Couldn't check that name right now." to FeedMuted
    }
    OutlinedTextField(
        value = value,
        onValueChange = { onValueChange(it.take(USERNAME_MAX + 5).replace(" ", "")) },
        label = { Text("Username") },
        singleLine = true,
        enabled = enabled,
        isError = check is NameCheck.Problem || check == NameCheck.Taken,
        supportingText = if (note == null && !showRules) null else {
            {
                Text(
                    note ?: "$USERNAME_MIN to $USERNAME_MAX letters, numbers, underscores, or periods.",
                    color = color,
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = feedFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    enabled: Boolean,
    imeAction: ImeAction,
    onDone: () -> Unit = {},
    supporting: String? = null,
    problem: String? = null,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = problem != null,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {

            // Skipped by the keyboard's "Next" key, which should go to the next box instead.
            TextButton(onClick = { visible = !visible }, modifier = Modifier.focusProperties { canFocus = false }) {
                Text(if (visible) "Hide" else "Show", color = Turquoise)
            }
        },
        supportingText = (problem ?: supporting)?.let { text ->
            { Text(text, color = if (problem != null) MaterialTheme.colorScheme.error else FeedMuted) }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        colors = feedFieldColors(),
        modifier = Modifier.fillMaxWidth(),
    )
}

// Shown on screens that need an account (like Messages) to guests.
@Composable
fun GuestNotice(title: String, message: String, onLogIn: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().background(Color.White).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, color = FeedText, fontFamily = FontFamily.Serif, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(message, color = FeedMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onLogIn,
            colors = ButtonDefaults.buttonColors(containerColor = Turquoise, contentColor = Color.White),
        ) { Text("Log In / Sign Up") }
    }
}
