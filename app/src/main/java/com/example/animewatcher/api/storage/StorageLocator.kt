package com.example.animewatcher.api.storage

import com.example.animewatcher.api.Storage
import java.net.URI

class StorageLocator {
    companion object {
        fun locate(url: String): Storage? {
            val host = getHost(url) ?: return null
            return when (host) {
                "kwik.cx" -> KwikStorage.instance
                else -> null
            }
        }

        private fun getHost(url: String): String? {
            val uri = URI(url)
            val hostname = uri.host
            if (hostname != null) {
                return if (hostname.startsWith("www.")) hostname.substring(4) else hostname
            }
            return null
        }
    }
}