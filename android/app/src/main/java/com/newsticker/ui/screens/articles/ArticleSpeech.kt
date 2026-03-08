package com.newsticker.ui.screens.articles

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.text.Html
import com.newsticker.data.model.Article
import java.util.Locale
import java.util.UUID

internal class ArticleSpeaker(
    context: Context,
    private val onSpeakingChanged: (Boolean) -> Unit
) : TextToSpeech.OnInitListener {

    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var textToSpeech: TextToSpeech? = TextToSpeech(appContext, this)
    private var ready = false
    private var pendingText: String? = null
    private var currentUtteranceId: String? = null

    init {
        textToSpeech?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                mainHandler.post { onSpeakingChanged(true) }
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId == currentUtteranceId) {
                    mainHandler.post { onSpeakingChanged(false) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId == currentUtteranceId) {
                    mainHandler.post { onSpeakingChanged(false) }
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId == currentUtteranceId) {
                    mainHandler.post { onSpeakingChanged(false) }
                }
            }

            override fun onStop(utteranceId: String?, interrupted: Boolean) {
                if (utteranceId == currentUtteranceId) {
                    mainHandler.post { onSpeakingChanged(false) }
                }
            }
        })
    }

    override fun onInit(status: Int) {
        val tts = textToSpeech ?: return
        if (status != TextToSpeech.SUCCESS) return

        val localeResult = tts.setLanguage(Locale.GERMAN)
        ready = localeResult != TextToSpeech.LANG_MISSING_DATA &&
            localeResult != TextToSpeech.LANG_NOT_SUPPORTED
        if (!ready) return

        pendingText?.let {
            pendingText = null
            speak(it)
        }
    }

    fun speak(text: String) {
        val normalized = text.trim()
        if (normalized.isBlank()) return

        if (!ready) {
            pendingText = normalized
            return
        }

        val utteranceId = UUID.randomUUID().toString()
        currentUtteranceId = utteranceId
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }
        textToSpeech?.speak(normalized, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        pendingText = null
        currentUtteranceId = null
        textToSpeech?.stop()
        onSpeakingChanged(false)
    }

    fun shutdown() {
        stop()
        textToSpeech?.shutdown()
        textToSpeech = null
    }
}

internal fun buildSpeechText(article: Article, contentState: ContentState?): String {
    val spokenBody = when (contentState) {
        is ContentState.Html -> htmlToSpeechText(contentState.html)
        is ContentState.DirectUrl, null -> article.description.cleanForSpeech()
    }

    return listOf(article.title.cleanForSpeech(), spokenBody)
        .filter { it.isNotBlank() }
        .joinToString("\n\n")
}

private fun htmlToSpeechText(html: String): String {
    val sanitized = html
        .replace(
            Regex("""<div[^>]*class="bottom-actions"[^>]*>[\s\S]*?</div>""", RegexOption.IGNORE_CASE),
            ""
        )
        .replace(Regex("""<style[\s\S]*?</style>""", RegexOption.IGNORE_CASE), "")
        .replace(Regex("""<script[\s\S]*?</script>""", RegexOption.IGNORE_CASE), "")

    val plainText = Html.fromHtml(sanitized, Html.FROM_HTML_MODE_LEGACY).toString()
    return plainText.cleanForSpeech()
}

private fun String.cleanForSpeech(): String {
    return replace('\u00A0', ' ')
        .replace(Regex("""\s+"""), " ")
        .trim()
}
