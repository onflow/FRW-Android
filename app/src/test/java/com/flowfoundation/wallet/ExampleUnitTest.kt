package com.flowfoundation.wallet

import kotlinx.coroutines.runBlocking
import org.onflow.flow.ChainId
import org.onflow.flow.FlowApi
import org.onflow.flow.infrastructure.Cadence
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun testScript() {
        println("===========> method: testScript()")
        val api = FlowApi(ChainId.Testnet)
        println("===========> start script execution")
        
        runBlocking {
            val response = api.executeScript(
                script = """
                    access(all) fun main(): String {
                        return "Hello World"
                    }
                """.trimIndent()
            )
            println("===========> response: ${response}")
            
            // The response should be a Cadence String value
            val stringValue = response as Cadence.Value.StringValue
            assertEquals("Hello World", stringValue.value)
        }
    }
}