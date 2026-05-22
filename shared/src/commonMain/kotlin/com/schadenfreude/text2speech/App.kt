package com.schadenfreude.text2speech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.schadenfreude.text2speech.domain.Language
import com.schadenfreude.text2speech.platform.getPermissionManager
import com.schadenfreude.text2speech.presentation.MainViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App(viewModel: MainViewModel = viewModel { MainViewModel() }) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val permissionManager = remember { getPermissionManager() }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    MaterialTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Safety Inspection Dictation") },
                    navigationIcon = {
                        if (uiState.showSettings) {
                            TextButton(onClick = { viewModel.toggleSettings() }) {
                                Text("Back")
                            }
                        }
                    },
                    actions = {
                        if (!uiState.showSettings) {
                            TextButton(onClick = { viewModel.toggleSettings() }) {
                                Text("Settings")
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { paddingValues ->
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                color = MaterialTheme.colorScheme.background
            ) {
                if (uiState.showSettings) {
                    SettingsScreen(
                        manualToken = uiState.manualToken,
                        onTokenChanged = { viewModel.updateManualToken(it) },
                        onBack = { viewModel.toggleSettings() }
                    )
                } else {
                    Column(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // 1. Input Toggle
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("File Upload")
                            Switch(
                                checked = uiState.isLiveStream,
                                onCheckedChange = { viewModel.toggleMode(it) }
                            )
                            Text("Live Stream")
                        }

                        // 2. Language Selector
                        LanguageSelector(
                            selectedLanguage = uiState.selectedLanguage,
                            onLanguageSelected = { viewModel.selectLanguage(it) }
                        )

                        HorizontalDivider()

                        // 3. Action Area
                        ActionArea(
                            isLiveStream = uiState.isLiveStream,
                            isRecording = uiState.isRecording,
                            isLoading = uiState.isLoading,
                            onToggleRecording = {
                                if (!uiState.isRecording) {
                                    permissionManager.requestMicrophonePermission(
                                        onGranted = { viewModel.toggleRecording() },
                                        onDenied = { viewModel.onError("Microphone permission denied") }
                                    )
                                } else {
                                    viewModel.toggleRecording()
                                }
                            },
                            onPickFile = { viewModel.pickFile() },
                            onUploadAndTranscribe = { viewModel.uploadAndTranscribe() }
                        )

                        HorizontalDivider()

                        // 4. Result Area
                        ResultArea(
                            finalText = uiState.finalText,
                            partialText = uiState.partialText
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(
    selectedLanguage: Language,
    onLanguageSelected: (Language) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedButton(onClick = { expanded = true }) {
            Text("Language: ${selectedLanguage.displayName}")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            Language.entries.forEach { language ->
                DropdownMenuItem(
                    text = { Text(language.displayName) },
                    onClick = {
                        onLanguageSelected(language)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun ActionArea(
    isLiveStream: Boolean,
    isRecording: Boolean,
    isLoading: Boolean,
    onToggleRecording: () -> Unit,
    onPickFile: () -> Unit,
    onUploadAndTranscribe: () -> Unit
) {
    if (isLiveStream) {
        Button(
            onClick = onToggleRecording,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isRecording) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Text(if (isRecording) "Stop Recording" else "Start Recording")
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = onPickFile,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Pick Audio File")
            }
            Button(
                onClick = onUploadAndTranscribe,
                enabled = !isLoading,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Text("Upload & Transcribe")
                }
            }
        }
    }
}

@Composable
fun ResultArea(finalText: String, partialText: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("Transcription Result:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(8.dp)
        ) {
            if (finalText.isEmpty() && partialText.isEmpty()) {
                Text(
                    "No results yet...",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                Text(buildAnnotatedString {
                    append(finalText)
                    if (partialText.isNotEmpty()) {
                        withStyle(
                            style = SpanStyle(
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            )
                        ) {
                            append(if (finalText.isNotEmpty()) " $partialText" else partialText)
                        }
                    }
                })
            }
        }
    }
}

@Composable
fun SettingsScreen(
    manualToken: String,
    onTokenChanged: (String) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Settings",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = manualToken,
            onValueChange = onTokenChanged,
            label = { Text("Manual Auth Token") },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Enter token or leave blank for default") }
        )

        Text(
            "If provided, this token will be used for STT requests instead of the default one.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.secondary
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
    }
}