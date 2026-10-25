// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.LeaderState
import nl.pe1mew.zangbundel.core.protocol.engine.LeaderMachine.Effect
import nl.pe1mew.zangbundel.core.protocol.engine.LeaderMachine.Event
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LeaderMachineTest {
    private val mine = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val other = GatheringId(byteArrayOf(9, 9, 9, 9, 9))

    private fun running(): LeaderMachine {
        val m = LeaderMachine(Random(7))
        m.handle(Event.StartRequested(0, canScan = true))
        m.handle(Event.Tick(5_000))
        assertEquals(LeaderState.RUNNING, m.state)
        return m
    }

    @Test
    fun `a phone that cannot scan cannot lead`() {
        val m = LeaderMachine()
        assertEquals(listOf(Effect.CannotLead), m.handle(Event.StartRequested(0, canScan = false)))
        assertEquals(LeaderState.IDLE, m.state)
    }

    @Test
    fun `a running gathering nearby blocks the start`() {
        val m = LeaderMachine()
        assertEquals(listOf(Effect.StartPreStartScan), m.handle(Event.StartRequested(0, canScan = true)))
        m.handle(Event.BeaconSeen(1_000, other, "Kampvuur Bevers", ended = false, relay = false))
        assertTrue(m.handle(Event.Tick(4_999)).isEmpty())
        assertEquals(listOf(Effect.Blocked(listOf("Kampvuur Bevers"))), m.handle(Event.Tick(5_000)))
        assertEquals(LeaderState.IDLE, m.state)
    }

    @Test
    fun `ended gatherings and relays do not block`() {
        val m = LeaderMachine(Random(7))
        m.handle(Event.StartRequested(0, canScan = true))
        m.handle(Event.BeaconSeen(1_000, other, "Oud vuur", ended = true, relay = false))
        m.handle(Event.BeaconSeen(1_000, other, "Relay", ended = false, relay = true))
        val begin = assertIs<Effect.Begin>(m.handle(Event.Tick(5_000)).single())
        assertFalse(begin.resumed)
        assertEquals(0L, begin.ts)
        assertTrue(begin.seq in 0..0xFFFF)
    }

    @Test
    fun `every change advances seq and a strictly larger ts`() {
        val m = running()
        val start = m.seq
        val a = assertIs<Effect.SignAndBurst>(m.handle(Event.Changed(6_000)).single())
        val b = assertIs<Effect.SignAndBurst>(m.handle(Event.Changed(6_000)).single())   // same instant
        assertEquals((start + 1) and 0xFFFF, a.seq)
        assertEquals((start + 2) and 0xFFFF, b.seq)
        assertEquals(1_000L, a.ts)
        assertEquals(1_001L, b.ts)
        assertFalse(a.ended)
    }

    @Test
    fun `seq wraps around at 65535`() {
        val m = LeaderMachine()
        m.handle(Event.ResumeRequested(0, canScan = true, gid = mine, seq = 0xFFFE, ts = 10))
        m.handle(Event.Tick(5_000))
        assertEquals(0xFFFF, m.seq)
        assertEquals(0, assertIs<Effect.SignAndBurst>(m.handle(Event.Changed(5_100)).single()).seq)
    }

    @Test
    fun `another gathering while running is warned about once`() {
        val m = running()
        assertEquals(listOf(Effect.WarnOtherGathering("Kampvuur Bevers")), m.handle(Event.BeaconSeen(6_000, other, "Kampvuur Bevers", false, false)))
        assertTrue(m.handle(Event.BeaconSeen(7_000, other, "Kampvuur Bevers", false, false)).isEmpty())
    }

    @Test
    fun `ending broadcasts the final state, refuses writes and stops after 60 s`() {
        val m = running()
        val fx = m.handle(Event.EndRequested(10_000))
        assertTrue(assertIs<Effect.SignAndBurst>(fx[0]).ended)
        assertEquals(Effect.RejectWrites, fx[1])
        assertEquals(LeaderState.ENDING, m.state)
        assertTrue(m.handle(Event.Changed(20_000)).isEmpty())                      // nothing changes while ending
        assertTrue(m.handle(Event.Tick(69_999)).isEmpty())
        assertEquals(listOf(Effect.StopAndForget), m.handle(Event.Tick(70_000)))
        assertEquals(LeaderState.ENDED, m.state)
    }

    @Test
    fun `a resumed gathering ignores its own beacons, continues ts and bursts with seq + 1`() {
        val m = LeaderMachine()
        m.handle(Event.ResumeRequested(1_000_000, canScan = true, gid = mine, seq = 500, ts = 90_000))
        m.handle(Event.BeaconSeen(1_001_000, mine, "Ons vuur", ended = false, relay = false))
        val begin = assertIs<Effect.Begin>(m.handle(Event.Tick(1_005_000)).single())
        assertTrue(begin.resumed)
        assertEquals(501, begin.seq)
        assertEquals(90_001L, begin.ts)
        val next = assertIs<Effect.SignAndBurst>(m.handle(Event.Changed(1_006_000)).single())
        assertEquals(91_001L, next.ts)
    }

    @Test
    fun `six slots, the seventh refused, busy one below the maximum`() {
        val p = SlotPolicy()
        val ids = (1..5).map { p.accept()!! }
        assertTrue(p.busy)                                                            // 5 open, max 6
        val sixth = p.accept()!!
        assertNull(p.accept())                                                        // the seventh is disconnected
        p.release(sixth, successful = true)
        p.release(ids[0], successful = true)
        assertFalse(p.busy)
    }

    @Test
    fun `slots rise after 10 successful cycles at the maximum and fall on exhaustion`() {
        val p = SlotPolicy()
        repeat(SlotPolicy.RAISE_AFTER) {
            val ids = (1..6).map { p.accept()!! }
            p.release(ids.last(), successful = true)
            ids.dropLast(1).forEach { id -> p.release(id, successful = false) }       // failures reset the run
            val again = (1..2).map { p.accept()!! }
            again.forEach { id -> p.release(id, successful = true) }
        }
        assertEquals(6, p.maxConn)                                                    // never 10 in a row

        val q = SlotPolicy()
        var cycles = 0
        while (q.maxConn == 6 && cycles < 50) {
            val ids = (1..6).map { q.accept()!! }
            q.release(ids[0], successful = true)
            q.release(ids[1], successful = true)
            ids.drop(2).forEach { q.release(it, successful = true) }
            cycles++
        }
        assertEquals(7, q.maxConn)
        q.exhausted()
        assertEquals(6, q.maxConn)
    }

    @Test
    fun `stay goes to delegates first, others only up to half the slots`() {
        val p = SlotPolicy()
        repeat(3) { p.accept() }
        assertTrue(p.grantStay(isDelegate = false))                                   // 3 <= 6 / 2
        p.accept()
        assertFalse(p.grantStay(isDelegate = false))
        assertTrue(p.grantStay(isDelegate = true))
    }
}
