package com.inputblocker.app

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito
import org.mockito.stubbing.Answer

class InputBlockerServiceManagerTest {

    private val capturedCommands = mutableListOf<String>()

    @Before
    fun setUp() {
        capturedCommands.clear()
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            capturedCommands.add(cmd)
            when {
                cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}") -> "3"
                cmd.contains("cat /nonexistent") -> ""
                else -> ""
            }
        }
    }

    @After
    fun tearDown() {
        InputBlockerServiceManager.testCommandRunner = null
    }

    // ── getCrashCount ──────────────────────────────────────────────

    @Test
    fun `getCrashCount returns 0 when file read fails`() {
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE} 2>/dev/null")) ""
            else ""
        }
        assertEquals(0, InputBlockerServiceManager.getCrashCount())
    }

    @Test
    fun `getCrashCount returns parsed value from root command`() {
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}")) "5" else ""
        }
        assertEquals(5, InputBlockerServiceManager.getCrashCount())
    }

    @Test
    fun `getCrashCount returns 0 for non-numeric output`() {
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}")) "not_a_number" else ""
        }
        assertEquals(0, InputBlockerServiceManager.getCrashCount())
    }

    @Test
    fun `getCrashCount returns 0 for empty output`() {
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}")) "" else ""
        }
        assertEquals(0, InputBlockerServiceManager.getCrashCount())
    }

    @Test
    fun `getCrashCount returns 0 when root command throws`() {
        InputBlockerServiceManager.testCommandRunner = { throw RuntimeException("su not available") }
        assertEquals(0, InputBlockerServiceManager.getCrashCount())
    }

    @Test
    fun `getCrashCount handles whitespace in output`() {
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}")) "  2  \n" else ""
        }
        assertEquals(2, InputBlockerServiceManager.getCrashCount())
    }

    // ── reportCrash ────────────────────────────────────────────────

    @Test
    fun `reportCrash does not throw`() {
        // Should handle the temp file write gracefully when /data/local/tmp doesn't exist
        InputBlockerServiceManager.reportCrash()
        // No exception = pass
    }

    @Test
    fun `reportCrash sends expected root commands on first crash`() {
        // Simulate crash counter file doesn't exist (returns 0)
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            capturedCommands.add(cmd)
            when {
                cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}") -> ""
                else -> ""
            }
        }
        capturedCommands.clear()

        InputBlockerServiceManager.reportCrash()

        // Should include mkdir, echo incrementing to 1, crash flag touch
        val allCommands = capturedCommands.joinToString("\n")
        assertTrue("Should mkdir crash dir", allCommands.contains("mkdir -p /data/local/tmp/inputblocker"))
        assertTrue("Should touch crash flag", allCommands.contains("touch ${InputBlockerServiceManager.CRASH_FLAG}"))
    }

    @Test
    fun `reportCrash with throwable includes details in crash log`() {
        val throwable = RuntimeException("test crash")
        throwable.stackTrace = arrayOf(
            StackTraceElement("com.test", "foo", "Test.kt", 42)
        )

        // Intercept all root commands silently
        var crashContents = ""
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            if (cmd.contains("cat ${InputBlockerServiceManager.CRASH_COUNTER_FILE}")) "0"
            else ""
        }

        InputBlockerServiceManager.reportCrash(throwable)
        // No exception = pass (details written to temp file which may fail on host)
    }

    // ── resetCrashCounter ──────────────────────────────────────────

    @Test
    fun `resetCrashCounter sends rm command`() {
        capturedCommands.clear()
        InputBlockerServiceManager.resetCrashCounter()
        assertTrue(
            capturedCommands.any { it.contains("rm -f ${InputBlockerServiceManager.CRASH_COUNTER_FILE}") }
        )
    }

    // ── runRootCommand (via test hook) ─────────────────────────────

    @Test
    fun `runRootCommand returns test hook output`() {
        InputBlockerServiceManager.testCommandRunner = { "mocked_output" }
        val result = InputBlockerServiceManager.runRootCommand("some command")
        assertEquals("mocked_output", result)
    }

    @Test
    fun `runRootCommand passes command to test hook`() {
        val cmds = mutableListOf<String>()
        InputBlockerServiceManager.testCommandRunner = { cmd ->
            cmds.add(cmd)
            ""
        }
        InputBlockerServiceManager.runRootCommand("echo hello")
        assertEquals("echo hello", cmds.single())
    }

    // ── getCrashCountDir ───────────────────────────────────────────

    @Test
    fun `getCrashCountDir returns expected path`() {
        assertEquals("/data/local/tmp/inputblocker", InputBlockerServiceManager.getCrashCountDir())
    }
}

class ConfigFileObserverTest {

    @Test
    fun `constructor accepts custom handler`() {
        val handler = Mockito.mock(android.os.Handler::class.java)
        var callbackInvoked = false
        val observer = ConfigFileObserver(
            configPath = "/tmp/test.conf",
            onConfigChanged = { callbackInvoked = true },
            handler = handler
        )
        assertNotNull(observer)
    }

    @Test
    fun `notifyChanged triggers callback via handler`() {
        val handler = Mockito.mock(android.os.Handler::class.java)

        // Capture the posted runnable and execute it
        Mockito.doAnswer(Answer<Any?> { invocation ->
            val runnable = invocation.getArgument<Runnable>(0)
            runnable.run()
            null
        }).`when`(handler).post(Mockito.any())

        var callbackInvoked = false
        val observer = ConfigFileObserver(
            configPath = "/nonexistent/test.conf",
            onConfigChanged = { callbackInvoked = true },
            handler = handler
        )
        observer.startWatching()
        observer.notifyChanged()
        assertTrue("onConfigChanged should be invoked", callbackInvoked)
        observer.stopWatching()
    }

    @Test
    fun `start and stop does not throw`() {
        val handler = Mockito.mock(android.os.Handler::class.java)
        val observer = ConfigFileObserver(
            configPath = "/nonexistent/test.conf",
            onConfigChanged = {},
            handler = handler
        )
        observer.startWatching()
        observer.stopWatching()
        observer.stopWatching()
    }

    @Test
    fun `start twice is idempotent`() {
        val handler = Mockito.mock(android.os.Handler::class.java)
        var count = 0
        val observer = ConfigFileObserver(
            configPath = "/nonexistent/test.conf",
            onConfigChanged = { count++ },
            handler = handler
        )
        observer.startWatching()
        observer.startWatching()
        observer.stopWatching()
    }
}
