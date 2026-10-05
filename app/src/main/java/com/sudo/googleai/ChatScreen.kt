package com.sudo.googleai

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun ChatScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val messages by viewModel.messages.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isModelReady by viewModel.isModelReady.collectAsState()
    val context = LocalContext.current

    var input by rememberSaveable { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Load model once
    LaunchedEffect(Unit) {
        viewModel.initializeModel(context)
    }

    // Auto-scroll to newest message
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .imePadding()
    ) {
        MessageList(
            messages = messages,
            isLoading = isLoading,
            isModelReady = isModelReady,
            listState = listState,
            modifier = Modifier.weight(1f)
        )

        ChatInputBar(
            value = input,
            onValueChange = { input = it },
            enabled = isModelReady && !isLoading,
            onSend = {
                viewModel.sendMessage(input.trim())
                input = ""
            }
        )
    }
}