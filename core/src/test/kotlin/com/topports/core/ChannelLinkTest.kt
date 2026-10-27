package com.topports.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChannelLinkTest {

    @Test
    fun inviteLinks() {
        assertEquals(ChannelLink.Invite("https://t.me/+AbCd_123"), ChannelLink.parse(" https://t.me/+AbCd_123 "))
        assertEquals(ChannelLink.Invite("https://t.me/joinchat/AbCd"), ChannelLink.parse("https://t.me/joinchat/AbCd"))
    }

    @Test
    fun publicLinks() {
        assertEquals(ChannelLink.Username("topportsandroid"), ChannelLink.parse("https://t.me/topportsandroid"))
        assertEquals(ChannelLink.Username("topportsandroid"), ChannelLink.parse("t.me/topportsandroid/"))
        assertEquals(ChannelLink.Username("topportsandroid"), ChannelLink.parse("@topportsandroid"))
    }

    @Test
    fun invalid() {
        assertNull(ChannelLink.parse(""))
        assertNull(ChannelLink.parse("https://example.com/x"))
    }
}
