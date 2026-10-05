package com.sudo.googleai


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp


@Composable
fun MessageList(
    messages: List<ChatMessage>,
    isLoading: Boolean,
    isModelReady: Boolean,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        when {
            !isModelReady -> LoadingModelPlaceholder()

            messages.isEmpty() -> EmptyChatPlaceholder()

            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(16.dp)
            ) {
                items(
                    items = messages,
                    key = { it.hashCode() } // stable keys
                ) { message ->
                    MessageBubble(message)
                }

                if (isLoading) {
                    item { TypingIndicator() }
                }
            }
        }
    }
}