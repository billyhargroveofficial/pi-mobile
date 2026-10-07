package ru.billyhargrove.pimobile.net

import org.junit.Assert.*
import org.junit.Test

class PendingCommandsTest {
    private class Fixture {
        val timers = mutableListOf<Runnable>()
        val cancelled = mutableListOf<Runnable>()
        val events = mutableListOf<Triple<String, String, String>>()
        val ledger = PendingCommands({ task, delay -> assertEquals(20_000L, delay); timers.add(task) },
            cancelled::add, { id, session, reason -> events.add(Triple(id, session, reason)) }, 20_000)
    }
    @Test fun foreignAckDoesNotCompleteOrCancelTheRealDeadline() {
        val f = Fixture(); f.ledger.register("r", "s", "timeout")
        assertFalse(f.ledger.resolve("r", "other")); assertTrue(f.ledger.contains("r", "s"))
        assertTrue(f.cancelled.isEmpty()); f.timers.single().run()
        assertEquals(listOf(Triple("r", "s", "timeout")), f.events)
    }
    @Test fun resolvedAndReplacedRequestsIgnoreAlreadyQueuedTimeouts() {
        val f = Fixture(); f.ledger.register("r", "s", "old")
        assertTrue(f.ledger.resolve("r", "s")); assertFalse(f.ledger.resolve("r", "s"))
        f.ledger.register("r", "s", "new"); f.timers.first().run()
        assertTrue(f.events.isEmpty()); assertEquals(setOf("r"), f.ledger.ids("s"))
        f.timers.last().run(); f.timers.last().run()
        assertEquals(listOf(Triple("r", "s", "new")), f.events)
    }
    @Test fun connectionReplacementFailsEachScopeOnceAndNeverReplays() {
        val f = Fixture(); f.ledger.register("a", "one", "timeout"); f.ledger.register("b", "two", "timeout")
        assertEquals(setOf("a"), f.ledger.ids("one")); assertEquals(setOf("a", "b"), f.ledger.ids())
        f.ledger.failAll("replaced"); f.ledger.failAll("again"); f.timers.forEach(Runnable::run)
        assertTrue(f.ledger.ids().isEmpty()); assertEquals(2, f.events.size); assertEquals(2, f.cancelled.size)
        assertTrue(f.events.all { it.third == "replaced" })
    }
    @Test fun unknownAckCannotCreateOrResolveAnything() {
        val f = Fixture(); assertFalse(f.ledger.resolve("unknown", "s")); assertFalse(f.ledger.contains("unknown", "s"))
        assertTrue(f.ledger.ids().isEmpty()); assertTrue(f.events.isEmpty())
    }
}
