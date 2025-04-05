package com.flowfoundation.wallet

import com.flowfoundation.wallet.manager.flow.FlowCadenceApi
import kotlinx.coroutines.runBlocking
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
    fun testScript() = runBlocking {
        println("===========> method: testScript()")
        val response = FlowCadenceApi.executeCadenceScript {
            script {
                """
                pub fun main(): String {
                    return "Hello World"
                }
            """
            }
        }
        println("===========> response: $response")
    }

}