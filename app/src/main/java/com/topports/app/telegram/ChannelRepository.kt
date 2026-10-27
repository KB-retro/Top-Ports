package com.topports.app.telegram

import com.topports.app.BuildConfig
import com.topports.core.CatalogBuilder
import com.topports.core.ChannelLink
import com.topports.core.ChannelMessage
import com.topports.core.Game
import com.topports.core.PhotoRef
import com.topports.core.RemoteFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi

data class CatalogState(
    val games: List<Game> = emptyList(),
    val loading: Boolean = false,
    val scannedMessages: Int = 0,
    val channelTitle: String = "",
    val chatId: Long = 0,
    val error: String? = null,
)

class ChannelNotAccessibleException : Exception(
    "No se encuentra el canal. Únete al canal desde Telegram con esta misma cuenta y pulsa Reintentar.",
)

/** Lee el canal de ports y lo convierte en catálogo. */
class ChannelRepository(
    private val tg: TelegramClient,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(CatalogState())
    val state: StateFlow<CatalogState> = _state.asStateFlow()

    private val messages = LinkedHashMap<Long, ChannelMessage>()
    private var loadJob: Job? = null
    private var listening = false

    fun refresh() {
        if (loadJob?.isActive == true) return
        loadJob = scope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val chat = resolveChat()
                _state.update { it.copy(channelTitle = chat.title, chatId = chat.id) }
                runCatching { tg.send(TdApi.OpenChat(chat.id)) }
                listenForNewPosts(chat.id)
                loadHistory(chat.id)
                _state.update { it.copy(loading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: e.toString()) }
            }
        }
    }

    fun game(id: Long): Game? = _state.value.games.firstOrNull { it.id == id }

    private suspend fun loadHistory(chatId: Long) {
        var fromId = 0L
        var emptyPages = 0
        while (messages.size < MAX_MESSAGES) {
            val page = tg.send(TdApi.GetChatHistory(chatId, fromId, 0, 100, false))
            if (page.messages.isEmpty()) {
                // TDLib a veces devuelve una página vacía mientras sincroniza.
                if (++emptyPages >= 3) break
                delay(700)
                continue
            }
            emptyPages = 0
            page.messages.forEach { add(it) }
            fromId = page.messages.last().id
            publish()
        }
        publish()
    }

    private fun listenForNewPosts(chatId: Long) {
        if (listening) return
        listening = true
        scope.launch {
            tg.newMessages.collect { message ->
                if (message.chatId == chatId) {
                    add(message)
                    publish()
                }
            }
        }
    }

    private fun add(message: TdApi.Message) {
        val mapped = message.toChannelMessage() ?: return
        synchronized(messages) { messages[mapped.id] = mapped }
    }

    private fun publish() {
        val snapshot = synchronized(messages) { messages.values.toList() }
        val games = CatalogBuilder.build(snapshot)
        _state.update { it.copy(games = games, scannedMessages = snapshot.size) }
    }

    private suspend fun resolveChat(): TdApi.Chat {
        val supergroupId = BuildConfig.CHANNEL_ID
        val chatId = -1_000_000_000_000L - supergroupId

        attempt { tg.send(TdApi.GetChat(chatId)) }?.let { return it }
        attempt { tg.send(TdApi.CreateSupergroupChat(supergroupId, false)) }?.let { return it }

        // Si el usuario es miembro, el canal aparece al cargar su lista de chats.
        for (i in 0 until 30) {
            val more = attempt { tg.send(TdApi.LoadChats(TdApi.ChatListMain(), 100)) } != null
            attempt { tg.send(TdApi.GetChat(chatId)) }?.let { return it }
            if (!more) break
        }

        val username = channelUsername()
        if (username != null) {
            attempt { tg.send(TdApi.SearchPublicChat(username)) }?.let { return it }
        }

        val invite = (ChannelLink.parse(BuildConfig.CHANNEL_INVITE_LINK) as? ChannelLink.Invite)?.url
        if (invite != null) {
            val info = attempt { tg.send(TdApi.CheckChatInviteLink(invite)) }
            if (info != null && info.chatId != 0L) {
                attempt { tg.send(TdApi.GetChat(info.chatId)) }?.let { return it }
            }
            val joined = attempt { tg.send(TdApi.JoinChatByInviteLink(invite)) }
            if (joined is TdApi.ChatJoinResultSuccess) return tg.send(TdApi.GetChat(joined.chatId))
        }

        throw ChannelNotAccessibleException()
    }

    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (e: TdException) {
        null
    }

    companion object {
        const val MAX_MESSAGES = 20_000

        /**
         * @usuario del canal público. Vale tanto TOPPORTS_CHANNEL_USERNAME como un enlace
         * `t.me/usuario` puesto en TOPPORTS_CHANNEL_INVITE_LINK.
         */
        fun channelUsername(): String? =
            listOf(BuildConfig.CHANNEL_USERNAME, BuildConfig.CHANNEL_INVITE_LINK)
                .firstNotNullOfOrNull { (ChannelLink.parse(it) as? ChannelLink.Username)?.name }

        /** Enlace para abrir el canal (o un mensaje) en la app de Telegram. */
        fun telegramLink(messageId: Long? = null): String {
            val username = channelUsername()
            // Los ids de mensaje de TDLib son el id de Telegram desplazado 20 bits.
            val post = messageId?.let { "/" + (it shr 20) } ?: ""
            return when {
                username != null -> "https://t.me/$username$post"
                messageId == null && BuildConfig.CHANNEL_INVITE_LINK.isNotBlank() -> BuildConfig.CHANNEL_INVITE_LINK
                else -> "https://t.me/c/${BuildConfig.CHANNEL_ID}${post.ifEmpty { "/1" }}"
            }
        }
    }
}

internal fun TdApi.Message.toChannelMessage(): ChannelMessage? {
    var text = ""
    var photo: PhotoRef? = null
    var document: RemoteFile? = null
    when (val c = content) {
        is TdApi.MessageText -> text = c.text.text
        is TdApi.MessagePhoto -> {
            text = c.caption.text
            photo = c.photo.toRef()
        }
        is TdApi.MessageDocument -> {
            text = c.caption.text
            val file = c.document.document
            document = RemoteFile(
                id = file.id,
                name = c.document.fileName.ifBlank { "archivo_${file.id}" },
                size = if (file.size > 0) file.size else file.expectedSize,
            )
        }
        is TdApi.MessageVideo -> text = c.caption.text
        is TdApi.MessageAnimation -> text = c.caption.text
        else -> return null
    }
    return ChannelMessage(
        id = id,
        date = date,
        albumId = mediaAlbumId,
        text = text,
        photo = photo,
        document = document,
    )
}

private fun TdApi.Photo.toRef(): PhotoRef? {
    val sorted = sizes.sortedBy { it.width }
    if (sorted.isEmpty()) return null
    val thumb = sorted.firstOrNull { it.width >= 320 } ?: sorted.last()
    return PhotoRef(thumbFileId = thumb.photo.id, fullFileId = sorted.last().photo.id)
}
