package com.parsa.nexora

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.net.URLDecoder

object VlessConfigParser {

    fun convert(link: String): String {

        require(link.startsWith("vless://", ignoreCase = true)) {
            "این لینک از نوع VLESS نیست."
        }

        val uri = URI(link.trim())

        val uuid = uri.rawUserInfo
            ?: throw IllegalArgumentException("شناسه UUID وجود ندارد.")

        require(uuid.isNotBlank()) {
            "شناسه UUID خالی است."
        }

        val host = uri.host
            ?: throw IllegalArgumentException("آدرس سرور نامعتبر است.")

        val port = uri.port

        require(port in 1..65535) {
            "پورت سرور معتبر نیست."
        }

        val params = parseQuery(uri.rawQuery)

        val network = params["type"]
            ?.lowercase() ?: "tcp"

        val security = params["security"]
            ?.lowercase() ?: "none"

        require(network in listOf("tcp", "ws", "grpc")) {
            "نوع اتصال فعلاً پشتیبانی نمی‌شود: $network"
        }

        require(security in listOf("none", "tls", "reality")) {
            "نوع امنیت پشتیبانی نمی‌شود: $security"
        }

        val user = JSONObject().apply {
            put("id", uuid)
            put("encryption", params["encryption"] ?: "none")

            params["flow"]?.takeIf { it.isNotBlank() }?.let {
                put("flow", it)
            }
        }

        val outboundSettings = JSONObject().apply {
            put(
                "vnext",
                JSONArray().put(
                    JSONObject().apply {
                        put("address", host)
                        put("port", port)
                        put("users", JSONArray().put(user))
                    }
                )
            )
        }

        val streamSettings = JSONObject().apply {

            put("network", network)
            put("security", security)

            when (security) {

                "tls" -> {
                    put(
                        "tlsSettings",
                        JSONObject().apply {
                            put(
                                "serverName",
                                params["sni"] ?: host
                            )

                            put(
                                "fingerprint",
                                params["fp"] ?: "chrome"
                            )

                            put("allowInsecure", false)

                            params["alpn"]?.let {
                                put(
                                    "alpn",
                                    JSONArray().put(it)
                                )
                            }
                        }
                    )
                }

                "reality" -> {
                    val publicKey =
                        params["pbk"] ?: params["publicKey"]

                    require(!publicKey.isNullOrBlank()) {
                        "کلید عمومی Reality یعنی pbk وجود ندارد."
                    }

                    put(
                        "realitySettings",
                        JSONObject().apply {
                            put(
                                "serverName",
                                params["sni"] ?: host
                            )

                            put(
                                "fingerprint",
                                params["fp"] ?: "chrome"
                            )

                            put("publicKey", publicKey)

                            put(
                                "shortId",
                                params["sid"] ?: ""
                            )

                            put(
                                "spiderX",
                                params["spx"] ?: "/"
                            )
                        }
                    )
                }
            }

            when (network) {

                "ws" -> {
                    put(
                        "wsSettings",
                        JSONObject().apply {

                            put(
                                "path",
                                params["path"] ?: "/"
                            )

                            put(
                                "headers",
                                JSONObject().apply {
                                    params["host"]?.let {
                                        put("Host", it)
                                    }
                                }
                            )
                        }
                    )
                }

                "grpc" -> {
                    put(
                        "grpcSettings",
                        JSONObject().apply {
                            put(
                                "serviceName",
                                params["serviceName"]
                                    ?: params["path"]
                                    ?: ""
                            )

                            put("multiMode", false)
                        }
                    )
                }
            }
        }

        val tunInbound = JSONObject().apply {

    put("tag", "tun")
    put("protocol", "tun")

    put(
        "settings",
        JSONObject().apply {
            put("name", "xray0")
            put("MTU", 1500)
            put("userLevel", 8)
            put("autoOutboundsInterface", "auto")
        }
    )
}
        val proxyOutbound = JSONObject().apply {

            put("tag", "proxy")
            put("protocol", "vless")
            put("settings", outboundSettings)
            put("streamSettings", streamSettings)
        }

        val directOutbound = JSONObject().apply {
            put("tag", "direct")
            put("protocol", "freedom")
        }

        val blockOutbound = JSONObject().apply {
            put("tag", "block")
            put("protocol", "blackhole")
        }

        val config = JSONObject().apply {

            put(
                "log",
                JSONObject().put("loglevel", "warning")
            )

            put(
                "inbounds",
                JSONArray().put(tunInbound)
            )

            put(
                "outbounds",
                JSONArray()
                    .put(proxyOutbound)
                    .put(directOutbound)
                    .put(blockOutbound)
            )

            put(
                "routing",
                JSONObject().apply {
                    put("domainStrategy", "AsIs")

                    put(
                        "rules",
                        JSONArray().put(
                            JSONObject().apply {
                                put("type", "field")
                                put("inboundTag", JSONArray().put("tun"))
                                put("outboundTag", "proxy")
                            }
                        )
                    )
                }
            )
        }

        return config.toString(2)
    }

    private fun parseQuery(query: String?): Map<String, String> {

        if (query.isNullOrBlank()) {
            return emptyMap()
        }

        return query.split("&").mapNotNull { item ->

            val parts = item.split("=", limit = 2)

            if (parts.isEmpty()) {
                return@mapNotNull null
            }

            val key = URLDecoder.decode(
                parts[0],
                "UTF-8"
            )

            val value = URLDecoder.decode(
                parts.getOrElse(1) { "" },
                "UTF-8"
            )

            key to value
        }.toMap()
    }
}
