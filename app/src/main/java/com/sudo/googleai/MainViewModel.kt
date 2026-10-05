package com.sudo.googleai

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.MessageCallback
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File



class MainViewModel : ViewModel() {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady.asStateFlow()

    fun initializeModel(context: Context) {
        if (_isModelReady.value) return // idempotent
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { buildEngine(context) }
                .onSuccess {
                    engine = it
                    conversation = it.createConversation(defaultConversationConfig())
                    _isModelReady.value = true
                    Log.d(TAG, "Model ready")
                }
                .onFailure {
                    Log.e(TAG, "Model init failed", it)
                    _isModelReady.value = false
                }
        }
    }

    private fun buildEngine(context: Context): Engine {
        val modelFile = File(context.filesDir, MODEL_NAME)
        if (!modelFile.exists()) {
            context.assets.open(MODEL_NAME).use { input ->
                modelFile.outputStream().use { input.copyTo(it) }
            }
        }
        return Engine(
            EngineConfig(
                modelPath = modelFile.absolutePath,
                backend = Backend.CPU()
            )
        ).also { it.initialize() }
    }

    private fun defaultConversationConfig() = ConversationConfig(
        samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.8)
    )

    fun sendMessage(text: String) {
        val convo = conversation ?: return
        if (text.isBlank() || _isLoading.value) return

        _messages.update { it + ChatMessage(Role.USER, text) }
        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            val history = _messages.value
                .filterNot { it.isError }
                .map { Content.Text(it.content) }

            convo.sendMessageAsync(
                Contents.of(history),
                object : MessageCallback {
                    override fun onMessage(message: Message) {
                        appendToModelMessage(message.toString())
                    }

                    override fun onDone() {
                        _isLoading.value = false
                    }

                    override fun onError(throwable: Throwable) {
                        Log.e(TAG, "Inference error", throwable)
                        replaceOrAppendModelMessage(
                            ChatMessage(
                                role = Role.MODEL,
                                content = "Something went wrong: ${throwable.message}",
                                isError = true
                            )
                        )
                        _isLoading.value = false
                    }
                }
            )
        }
    }

    private fun appendToModelMessage(chunk: String) {
        _messages.update { current ->
            val last = current.lastOrNull()
            if (last?.role == Role.MODEL && !last.isError) {
                current.dropLast(1) + last.copy(content = last.content + chunk)
            } else {
                current + ChatMessage(Role.MODEL, chunk)
            }
        }
    }

    private fun replaceOrAppendModelMessage(message: ChatMessage) {
        _messages.update { current ->
            if (current.lastOrNull()?.role == Role.MODEL) {
                current.dropLast(1) + message
            } else {
                current + message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        conversation?.close()
        engine?.close()
        conversation = null
        engine = null
    }

    companion object {
        private const val TAG = "LLM2"
        private const val MODEL_NAME = "gemma3.litertlm"
    }
}

/*class MainViewModel : ViewModel() {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isModelReady = MutableStateFlow(false)
    val isModelReady: StateFlow<Boolean> = _isModelReady

    fun initializeModel(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val modelFile = File(context.filesDir, "gemma3.litertlm")

                // Copy model from assets to internal storage
                if (!modelFile.exists()) {
                    context.assets.open("gemma3.litertlm").use { input ->
                        modelFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                }

                // Create LiteRT-LM engine
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.CPU()
                )

                engine = Engine(config)
                engine?.initialize()

                conversation = engine?.createConversation(
                    ConversationConfig(
                        samplerConfig = SamplerConfig(
                            topK = 40,
                            topP = 0.95,
                            temperature = 0.8
                        )
                    )
                )

                _isModelReady.value = true
                Log.d("LLM2", "Model initialized successfully")

            } catch (e: Exception) {
                Log.e("LLM2", "Model initialization failed", e)
                _isModelReady.value = false
            }
        }
    }

    fun sendMessage(message: String) {
        val currentConversation = conversation
        if (currentConversation == null) {
            Log.e("LLM2", "Cannot send message: conversation is null (model not ready)")
            return
        }

        _isLoading.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("LLM2", "Sending: $message")

                _messages.value += ChatMessage(role = Role.USER, content = message)

                val history = _messages.value.map { Content.Text(it.content) }

                currentConversation.sendMessageAsync(
                    Contents.of(history),
                    object : MessageCallback {
                        override fun onMessage(message: Message) {
                            val chunk = message.toString()
                            Log.d("LLM2", "Chunk: $chunk")

                            val currentMessages = _messages.value.toMutableList()

                            if (currentMessages.lastOrNull()?.role != Role.MODEL) {
                                currentMessages.add(ChatMessage(role = Role.MODEL, content = chunk))
                            } else {
                                val lastIndex = currentMessages.lastIndex
                                currentMessages[lastIndex] = ChatMessage(
                                    role = Role.MODEL,
                                    content = currentMessages[lastIndex].content + chunk
                                )
                            }

                            _messages.value = currentMessages
                        }

                        override fun onDone() {
                            Log.d("LLM2", "Response complete")
                            _isLoading.value = false
                        }

                        override fun onError(throwable: Throwable) {
                            Log.e("LLM2", "Inference error", throwable)

                            val currentMessages = _messages.value.toMutableList()
                            if (currentMessages.lastOrNull()?.role != Role.MODEL) {
                                currentMessages.add(ChatMessage(role = Role.MODEL, content = "Error: ${throwable.message}"))
                            } else {
                                val lastIndex = currentMessages.lastIndex
                                currentMessages[lastIndex] = ChatMessage(
                                    role = Role.MODEL,
                                    content = "Error: ${throwable.message}"
                                )
                            }

                            _messages.value = currentMessages
                            _isLoading.value = false
                        }
                    }
                )

            } catch (e: Exception) {
                Log.e("LLM2", "ERROR", e)
                _isLoading.value = false
            }
        }
    }
}*/

