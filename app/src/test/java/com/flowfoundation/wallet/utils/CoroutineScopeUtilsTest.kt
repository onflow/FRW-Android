package com.flowfoundation.wallet.utils

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.mock
import org.robolectric.RobolectricTestRunner
import org.assertj.core.api.Assertions.assertThat
import kotlin.coroutines.ContinuationInterceptor

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class CoroutineScopeUtilsTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test ioScope uses IO dispatcher`() = testScope.run {
        var executedOnIO = false
        val job = ioScope {
            executedOnIO = coroutineContext[ContinuationInterceptor] == Dispatchers.IO
        }
        job.join()
        assertThat(executedOnIO).isTrue()
    }

    @Test
    fun `test uiScope uses Main dispatcher`() = testScope.run {
        var executedOnMain = false
        val job = uiScope {
            executedOnMain = coroutineContext[ContinuationInterceptor] == Dispatchers.Main
        }
        job.join()
        assertThat(executedOnMain).isTrue()
    }

    @Test
    fun `test cpuScope uses Default dispatcher`() = testScope.run {
        var executedOnDefault = false
        val job = cpuScope {
            executedOnDefault = coroutineContext[ContinuationInterceptor] == Dispatchers.Default
        }
        job.join()
        assertThat(executedOnDefault).isTrue()
    }

    @Test
    fun `test viewModelIOScope uses IO dispatcher`() = testScope.run {
        val viewModel = mock<ViewModel>()
        var executedOnIO = false
        val job = viewModelIOScope(viewModel) {
            executedOnIO = coroutineContext[ContinuationInterceptor] == Dispatchers.IO
        }
        job.join()
        assertThat(executedOnIO).isTrue()
    }

    @Test
    fun `test error handling in ioScope`() = testScope.run {
        var exceptionCaught = false
        val job = ioScope {
            throw RuntimeException("Test exception")
        }
        job.invokeOnCompletion { throwable ->
            exceptionCaught = throwable != null
        }
        job.join()
        assertThat(exceptionCaught).isTrue()
    }

    @Test
    fun `test error handling in uiScope`() = testScope.run {
        var exceptionCaught = false
        val job = uiScope {
            throw RuntimeException("Test exception")
        }
        job.invokeOnCompletion { throwable ->
            exceptionCaught = throwable != null
        }
        job.join()
        assertThat(exceptionCaught).isTrue()
    }

    @Test
    fun `test error handling in cpuScope`() = testScope.run {
        var exceptionCaught = false
        val job = cpuScope {
            throw RuntimeException("Test exception")
        }
        job.invokeOnCompletion { throwable ->
            exceptionCaught = throwable != null
        }
        job.join()
        assertThat(exceptionCaught).isTrue()
    }

    @Test
    fun `test error handling in viewModelIOScope`() = testScope.run {
        val viewModel = mock<ViewModel>()
        var exceptionCaught = false
        val job = viewModelIOScope(viewModel) {
            throw RuntimeException("Test exception")
        }
        job.invokeOnCompletion { throwable ->
            exceptionCaught = throwable != null
        }
        job.join()
        assertThat(exceptionCaught).isTrue()
    }

    @Test
    fun `test multiple coroutines in different scopes`() = testScope.run {
        val jobs = mutableListOf<Job>()
        var ioExecuted = false
        var uiExecuted = false
        var cpuExecuted = false

        jobs += ioScope { ioExecuted = true }
        jobs += uiScope { uiExecuted = true }
        jobs += cpuScope { cpuExecuted = true }

        jobs.forEach { it.join() }

        assertThat(ioExecuted).isTrue()
        assertThat(uiExecuted).isTrue()
        assertThat(cpuExecuted).isTrue()
    }
} 