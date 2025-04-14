package com.flowfoundation.wallet

import com.nftco.flow.sdk.Flow
import com.nftco.flow.sdk.simpleFlowScript
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testScript() {
        println("===========> method: testScript()")
        val accessApi = Flow.newAccessApi(HOST_TESTNET, 9000)
        println("===========> start ping")
        accessApi.ping()
        println("===========> end ping")
        val response = accessApi.simpleFlowScript {
            script {
                """
                    access(all) fun main(): String {
                        return "Hello World"
                    }
                """
            }
        }
        println("===========> response:${response}")
    }

    companion object{
        const val HOST_TESTNET = "access.devnet.nodes.onflow.org"
    }
}