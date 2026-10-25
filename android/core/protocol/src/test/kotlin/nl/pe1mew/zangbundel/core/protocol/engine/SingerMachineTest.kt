// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.model.SingerState
import nl.pe1mew.zangbundel.core.protocol.engine.SingerMachine.Effect
import nl.pe1mew.zangbundel.core.protocol.engine.SingerMachine.Event
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SETLIST
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.SONG
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType.STATE
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SingerMachineTest {
    private val gid = GatheringId(byteArrayOf(1, 2, 3, 4, 5))
    private val other = GatheringId(byteArrayOf(9, 9, 9, 9, 9))
    private val pk = ByteArray(32) { 7 }

    private fun machine() = SingerMachine(Random(42))

    /** Joined and registered, listening at t = 1000 with seq 10. */
    private fun listening(carrier: Carrier = Carrier.LEGACY, pt: Long? = 4_200, songLocal: Boolean = false): SingerMachine {
        val m = machine()
        m.handle(Event.Selected(0, gid, carrier))
        m.handle(Event.InfoVerified(100, pk, 1))
        m.handle(Event.StateApplied(200, seq = 10, ended = false, passTimeMs = pt, songIsLocal = songLocal))
        m.handle(Event.JoinAnswered(500, JoinResponse.ACCEPTED))
        m.handle(Event.BeaconSeen(1000, gid, 10, ended = false, busy = false))
        assertEquals(SingerState.LISTENING, m.state)
        return m
    }

    @Test
    fun `discovery, selection, verification and registration`() {
        val m = machine()
        assertEquals(listOf(Effect.StartScan), m.handle(Event.DiscoveryStarted(0)))
        m.handle(Event.BeaconSeen(10, gid, 1, ended = false, busy = false))
        assertEquals(SingerState.DISCOVERED, m.state)
        m.handle(Event.Selected(20, gid, Carrier.LEGACY))
        assertEquals(SingerState.REGISTERING, m.state)
        assertTrue(m.handle(Event.InfoVerified(30, pk, 1)).isEmpty())         // no Join before a verified state (10.2)
        val fx = m.handle(Event.StateApplied(40, 1, ended = false, passTimeMs = null, songIsLocal = false))
        val register = assertIs<Effect.Register>(fx.single())
        assertTrue(register.delayMs in 0..400)
        m.handle(Event.JoinAnswered(60, JoinResponse.ACCEPTED))
        assertEquals(SingerState.LISTENING, m.state)
        assertTrue(m.registered)
    }

    @Test
    fun `a key from the QR code is never replaced by Info`() {
        val m = machine()
        val fromQr = ByteArray(32) { 3 }
        m.handle(Event.Selected(0, gid, Carrier.LEGACY, pk = fromQr))
        m.handle(Event.InfoVerified(10, pk, 1))
        assertContentEquals(fromQr, m.pinnedPk)
    }

    @Test
    fun `another protocol major is not registered with`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        val fx = m.handle(Event.InfoVerified(10, pk, 2))
        assertTrue(Effect.ShowUpdateRequired in fx)
        assertEquals(SingerState.IDLE, m.state)
        assertNull(m.gid)
    }

    @Test
    fun `a full gathering leaves the singer listening and retrying in 30 to 60 s`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        m.handle(Event.StateApplied(10, 1, false, null, false))
        assertEquals(listOf(Effect.ShowJoinResult(JoinResponse.FULL)), m.handle(Event.JoinAnswered(1_000, JoinResponse.FULL)))
        assertEquals(SingerState.LISTENING, m.state)
        for (t in 2_000L..30_000L step 1_000) {
            m.handle(Event.BeaconSeen(t, gid, 1, false, false))
            assertTrue(m.handle(Event.Tick(t)).none { it is Effect.Register }, "no retry at $t")
        }
        var retried = false
        for (t in 31_000L..61_000L step 1_000) {
            m.handle(Event.BeaconSeen(t, gid, 1, false, false))
            if (m.handle(Event.Tick(t)).any { it is Effect.Register }) { retried = true; break }
        }
        assertTrue(retried)
        m.handle(Event.JoinAnswered(62_000, JoinResponse.ACCEPTED))
        assertTrue(m.registered)
        assertEquals(SingerState.LISTENING, m.state)
    }

    @Test
    fun `a wrong join code waits for the user`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        m.handle(Event.StateApplied(10, 1, false, null, false))
        assertEquals(listOf(Effect.ShowJoinResult(JoinResponse.WRONG_CODE)), m.handle(Event.JoinAnswered(100, JoinResponse.WRONG_CODE)))
        assertEquals(SingerState.REGISTERING, m.state)
        assertIs<Effect.Register>(m.handle(Event.CodeEntered(200)).single())
    }

    @Test
    fun `failed connections back off from 300 ms, doubling to 2 s, with jitter`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        m.handle(Event.StateApplied(0, 1, false, null, false))
        var now = 0L
        for (base in listOf(300L, 600L, 1_200L, 2_000L, 2_000L)) {
            m.handle(Event.ConnectionFailed(now))
            var at: Long? = null
            for (t in now..now + 3_000 step 5) {
                m.handle(Event.BeaconSeen(t, gid, 1, false, false))
                if (m.handle(Event.Tick(t)).any { it is Effect.Register }) { at = t; break }
            }
            val waited = at!! - now
            assertTrue(waited in (base * 3 / 4)..(base * 5 / 4 + 5), "waited $waited for base $base")
            now = at
        }
    }

    @Test
    fun `a song frame starts the repair timer, min(1,5 x pt, 3 s) on legacy`() {
        val m = listening(pt = 1_000)                                   // 1.5 × 1 s = 1.5 s
        m.handle(Event.FrameSeen(2_000, SONG, 0x2002))
        assertTrue(m.handle(Event.Tick(3_499)).isEmpty())
        val fx = m.handle(Event.Tick(3_500))
        assertEquals(listOf(SONG to 0x2002), assertIs<Effect.Repair>(fx.single()).groups)
        assertEquals(SingerState.REPAIRING, m.state)
        m.handle(Event.RepairDone(3_800))
        assertEquals(SingerState.LISTENING, m.state)
    }

    @Test
    fun `a document completed by broadcast is not repaired`() {
        val m = listening()
        m.handle(Event.FrameSeen(2_000, SETLIST, 0x3333))
        m.handle(Event.DocumentComplete(2_500, SETLIST, 0x3333))
        assertTrue(m.handle(Event.Tick(9_000)).isEmpty())
    }

    @Test
    fun `a singer with the song locally never repairs it`() {
        val m = listening(songLocal = true)
        m.handle(Event.FrameSeen(2_000, SONG, 0x2002))
        assertTrue(m.handle(Event.Tick(9_000)).isEmpty())
    }

    @Test
    fun `timer lengths follow 7-6`() {
        val legacy = listening(pt = 4_200)
        assertEquals(1_500, legacy.timeoutFor(STATE))
        assertEquals(3_000, legacy.timeoutFor(SONG))                   // min(6.3 s, 3 s)
        assertEquals(8_400, legacy.timeoutFor(SETLIST))                // 2 × pt
        assertEquals(20_000, listening(pt = 15_000).timeoutFor(SETLIST))
        val extended = listening(carrier = Carrier.EXTENDED)
        assertEquals(listOf(1_500L, 2_000L, 6_000L), listOf(STATE, SONG, SETLIST).map { extended.timeoutFor(it) })
    }

    @Test
    fun `a new seq expects a state record and repairs it if none arrives`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 11, false, false))
        assertTrue(m.handle(Event.Tick(3_499)).isEmpty())
        assertEquals(listOf(STATE to 11), assertIs<Effect.Repair>(m.handle(Event.Tick(3_500)).single()).groups)

        val n = listening()
        n.handle(Event.BeaconSeen(2_000, gid, 11, false, false))
        n.handle(Event.StateApplied(2_400, 11, false, 4_200, false))
        assertTrue(n.handle(Event.Tick(3_600)).isEmpty())
    }

    @Test
    fun `a busy leader means a longer random delay`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 10, ended = false, busy = true))
        m.handle(Event.FrameSeen(2_000, SETLIST, 1))
        // The setlist timer is 2 × pt = 8.4 s; the delay while BUSY is 0–2 s instead of 0–400 ms.
        val repair = assertIs<Effect.Repair>(m.handle(Event.Tick(10_400)).single())
        assertTrue(repair.delayMs in 0..2_000)
    }

    @Test
    fun `a failed repair is retried for the same documents`() {
        val m = listening(pt = 1_000)
        m.handle(Event.FrameSeen(2_000, SONG, 5))
        m.handle(Event.Tick(3_500))
        m.handle(Event.ConnectionFailed(3_600))
        var retry: Effect.Repair? = null
        for (t in 3_600L..4_100L step 10) {
            retry = m.handle(Event.Tick(t)).filterIsInstance<Effect.Repair>().firstOrNull() ?: continue
            break
        }
        assertEquals(listOf(SONG to 5), retry!!.groups)
    }

    @Test
    fun `silence means reconnecting after 10 s and ended after 5 minutes`() {
        val m = listening()
        assertTrue(m.handle(Event.Tick(10_999)).isEmpty())
        assertEquals(listOf(Effect.ShowReconnecting), m.handle(Event.Tick(11_000)))
        assertEquals(SingerState.RECONNECTING, m.state)
        assertEquals(listOf(Effect.ShowReconnected), m.handle(Event.BeaconSeen(12_000, gid, 10, false, false)))
        assertEquals(SingerState.LISTENING, m.state)

        m.handle(Event.Tick(22_000))
        val fx = m.handle(Event.Tick(12_000 + 5 * 60_000))
        assertEquals(SingerState.ENDED, m.state)
        assertTrue(fx.containsAll(listOf(Effect.ShowEnded, Effect.DeleteGatheringData, Effect.WriteGatheringMemory)))
    }

    @Test
    fun `an ended beacon or an ended state ends the gathering`() {
        val a = listening()
        a.handle(Event.BeaconSeen(2_000, gid, 11, ended = true, busy = false))
        assertEquals(SingerState.ENDED, a.state)
        val b = listening()
        val fx = b.handle(Event.StateApplied(2_000, 11, ended = true, passTimeMs = null, songIsLocal = false))
        assertEquals(SingerState.ENDED, b.state)
        assertTrue(Effect.DeleteGatheringData in fx)
    }

    @Test
    fun `beacons of another gathering are ignored`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, other, 99, ended = true, busy = false))
        assertEquals(SingerState.LISTENING, m.state)
    }

    @Test
    fun `leaving sends the leave notice only when registered, and forgets everything`() {
        val m = listening()
        val fx = m.handle(Event.LeaveRequested(2_000))
        assertEquals(listOf(Effect.SendLeave, Effect.DeleteGatheringData, Effect.WriteGatheringMemory, Effect.StopScan), fx)
        assertEquals(SingerState.IDLE, m.state)
        assertNull(m.pinnedPk)

        val u = machine()
        u.handle(Event.Selected(0, gid, Carrier.LEGACY))
        assertTrue(Effect.SendLeave !in u.handle(Event.LeaveRequested(10)))
    }

    @Test
    fun `from ended the user goes back to idle or discovers again`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 11, ended = true, busy = false))
        assertEquals(listOf(Effect.StartScan), m.handle(Event.DiscoveryStarted(3_000)))
        assertEquals(SingerState.IDLE, m.state)
    }
}
