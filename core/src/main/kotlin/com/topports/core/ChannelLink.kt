package com.topports.core

/** Enlace del canal: `@usuario` / `t.me/usuario` (público) o `t.me/+XXXX` (invitación). */
sealed interface ChannelLink {
    data class Username(val name: String) : ChannelLink
    data class Invite(val url: String) : ChannelLink

    companion object {
        private val publicLink = Regex("""^(?:https?://)?(?:www\.)?(?:t|telegram)\.me/([A-Za-z0-9_]{4,})/?$""")
        private val username = Regex("""^@?([A-Za-z][A-Za-z0-9_]{3,})$""")

        fun parse(raw: String): ChannelLink? {
            val value = raw.trim()
            if (value.isEmpty()) return null
            if ("/+" in value || "joinchat/" in value) return Invite(value)
            publicLink.matchEntire(value)?.let { return Username(it.groupValues[1]) }
            username.matchEntire(value)?.let { return Username(it.groupValues[1]) }
            return null
        }
    }
}
