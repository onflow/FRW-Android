package com.flowfoundation.wallet.manager.env

import io.github.cdimascio.dotenv.Dotenv
import io.github.cdimascio.dotenv.dotenv
import com.flowfoundation.wallet.utils.logd

object EnvKey {

    private lateinit var dotEnv: Dotenv

    fun init() {
        dotEnv = dotenv {
            directory = "./assets/env"
            filename = "config"
            ignoreIfMalformed = true
            ignoreIfMissing = true
        }
        logd("xxx", "dotEnv:$dotEnv")
    }

    fun get(key: String): String {
        if (!::dotEnv.isInitialized) {
            init()
        }
        return dotEnv[key]
    }
}