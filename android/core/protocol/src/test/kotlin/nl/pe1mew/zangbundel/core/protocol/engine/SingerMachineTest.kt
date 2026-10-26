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
    private fun listening(carrier: Carrier = Carrier.LEGACY, songLocal: Boolean = false): SingerMachine {
        val m = machine()
        m.handle(Event.Selected(0, gid, carrier))
        m.handle(Event.InfoVerified(100, pk, 1))
        m.handle(Event.StateApplied(200, seq = 10, ended = false, songIsLocal = songLocal))
        m.handle(Event.JoinAnswered(500, JoinResponse.ACCEPTED))
        m.handle(Event.BeaconSeen(1000, gid, 10, ended = false, busy = false))
        assertEquals(SingerState.LISTENING, m.state)
        return m
    }

    /** Frames of one document arriving at [perSecond] new distinct frames per second, from [from] to [to]. */
    private fun SingerMachine.arrive(doc: Int, dv: Int, needed: Int, perSecond: Double, from: Long, to: Long): List<Effect> {
        val fx = ArrayList<Effect>()
        var t = from
        var have = 0
        val step = (1000 / perSecond).toLong()
        while (t <= to) {
            have++
            handle(Event.FrameProgress(t, doc, dv, have, needed))
            fx += handle(Event.Tick(t))
            t += step
        }
        return fx
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
        val register = assertIs<Effect.Register>(m.handle(Event.StateApplied(40, 1, ended = false, songIsLocal = false)).single())
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
        assertTrue(Effect.ShowUpdateRequired in m.handle(Event.InfoVerified(10, pk, 2)))
        assertEquals(SingerState.IDLE, m.state)
        assertNull(m.gid)
    }

    @Test
    fun `a full gathering leaves the singer listening and retrying in 30 to 60 s`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        m.handle(Event.StateApplied(10, 1, false, false))
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
        m.handle(Event.StateApplied(10, 1, false, false))
        assertEquals(listOf(Effect.ShowJoinResult(JoinResponse.WRONG_CODE)), m.handle(Event.JoinAnswered(100, JoinResponse.WRONG_CODE)))
        assertEquals(SingerState.REGISTERING, m.state)
        assertIs<Effect.Register>(m.handle(Event.CodeEntered(200)).single())
    }

    @Test
    fun `failed connections back off from 300 ms, doubling to 2 s, with jitter`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.LEGACY))
        m.handle(Event.StateApplied(0, 1, false, false))
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
    fun `nothing new for 3 s means repair`() {
        val m = listening()
        m.handle(Event.FrameProgress(2_000, SONG, 0x2002, 1, 125))
        assertTrue(m.handle(Event.Tick(4_999)).isEmpty())
        assertEquals(listOf(SONG to 0x2002), assertIs<Effect.Repair>(m.handle(Event.Tick(5_000)).single()).groups)
        assertEquals(SingerState.REPAIRING, m.state)
        m.handle(Event.RepairDone(5_300))
        assertEquals(SingerState.LISTENING, m.state)
    }

    @Test
    fun `a rate that will not reach k within 10 s means repair after 2 s of measuring`() {
        // 2 new frames per second towards k = 125: about 60 s to go.
        val fx = listening().arrive(SONG, 7, needed = 125, perSecond = 2.0, from = 2_000, to = 4_500)
        val repair = fx.filterIsInstance<Effect.Repair>()
        assertEquals(1, repair.size)
        assertEquals(listOf(SONG to 7), repair.single().groups)
    }

    @Test
    fun `a rate that reaches k within 5 s of the start never repairs`() {
        // 30 new frames per second towards k = 125: done 4.2 s after the first frame (v0.24, Q26).
        val m = listening()
        val fx = m.arrive(SONG, 7, needed = 125, perSecond = 30.0, from = 2_000, to = 6_100)
        assertTrue(fx.none { it is Effect.Repair }, "$fx")
        m.handle(Event.DocumentComplete(6_200, SONG, 7))
        assertTrue(m.handle(Event.Tick(20_000)).none { it is Effect.Repair })
    }

    @Test
    fun `a rate that would take 10 s repairs 2 s after the start`() {
        // 13 new frames per second towards k = 125, as at 40 % on three legacy sets: about 10 s. v0.21 waited.
        val fx = listening().arrive(SONG, 7, needed = 125, perSecond = 13.0, from = 2_000, to = 4_300)
        assertEquals(1, fx.filterIsInstance<Effect.Repair>().size)
    }

    @Test
    fun `both carriers have the same 5 s deadline`() {
        // 1 new frame per second towards k = 9: 9 s in all, too slow on either carrier (v0.24).
        val ext = listening(carrier = Carrier.EXTENDED).arrive(SONG, 7, needed = 9, perSecond = 1.0, from = 2_000, to = 4_000)
        assertEquals(1, ext.filterIsInstance<Effect.Repair>().size)
        val legacy = listening().arrive(SONG, 7, needed = 9, perSecond = 1.0, from = 2_000, to = 4_000)
        assertEquals(1, legacy.filterIsInstance<Effect.Repair>().size)
        // 3 frames per second towards k = 9: done 3 s after the start, on time on either.
        val quick = listening(carrier = Carrier.EXTENDED).arrive(SONG, 7, needed = 9, perSecond = 3.0, from = 2_000, to = 4_600)
        assertTrue(quick.none { it is Effect.Repair })
    }

    @Test
    fun `a new seq without state frames means repair after 1 s (v0_33), a verified state cancels it`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 11, false, false))
        assertTrue(m.handle(Event.Tick(2_999)).isEmpty())
        assertEquals(listOf(STATE to 11), assertIs<Effect.Repair>(m.handle(Event.Tick(3_000)).single()).groups)

        val n = listening()
        n.handle(Event.BeaconSeen(2_000, gid, 11, false, false))
        n.handle(Event.StateApplied(2_400, 11, false, false))
        assertTrue(n.handle(Event.Tick(3_000)).isEmpty())
    }

    @Test
    fun `a document completed by broadcast is not repaired`() {
        val m = listening()
        m.handle(Event.FrameProgress(2_000, SETLIST, 0x3333, 1, 38))
        m.handle(Event.DocumentComplete(2_500, SETLIST, 0x3333))
        assertTrue(m.handle(Event.Tick(9_000)).isEmpty())
    }

    @Test
    fun `a singer with the song locally never repairs it`() {
        val m = listening(songLocal = true)
        m.handle(Event.FrameProgress(2_000, SONG, 0x2002, 1, 125))
        assertTrue(m.handle(Event.Tick(9_000)).isEmpty())
    }

    @Test
    fun `a busy leader means a longer random delay`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 10, ended = false, busy = true))
        m.handle(Event.FrameProgress(2_000, SETLIST, 1, 1, 38))
        val repair = assertIs<Effect.Repair>(m.handle(Event.Tick(5_000)).single())
        assertTrue(repair.delayMs in 0..2_000)
    }

    @Test
    fun `a failed repair is retried for the same documents`() {
        val m = listening()
        m.handle(Event.FrameProgress(2_000, SONG, 5, 1, 125))
        m.handle(Event.Tick(5_000))
        m.handle(Event.ConnectionFailed(5_100))
        var retry: Effect.Repair? = null
        for (t in 5_100L..5_600L step 10) {
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
        val fx = b.handle(Event.StateApplied(2_000, 11, ended = true, songIsLocal = false))
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
        assertEquals(listOf(Effect.SendLeave, Effect.DeleteGatheringData, Effect.WriteGatheringMemory, Effect.StopScan), m.handle(Event.LeaveRequested(2_000)))
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

    @Test
    fun `without Info by broadcast within 1·5 s the singer registers anyway, reading Info and State on that connection`() {
        val m = machine()
        m.handle(Event.Selected(0, gid, Carrier.EXTENDED))
        assertTrue(m.handle(Event.Tick(1_400)).none { it is Effect.Register })
        assertIs<Effect.Register>(m.handle(Event.Tick(1_500)).single())
        assertTrue(m.handle(Event.Tick(1_750)).none { it is Effect.Register })          // asked once
        m.handle(Event.ConnectionFailed(1_800))
        val retry = (1_900L..4_000L step 250).flatMap { m.handle(Event.Tick(it)) }
        assertEquals(1, retry.count { it is Effect.Register })                           // after the backoff
    }

    @Test
    fun `a document the manifest names is repaired 3 s after the state even if none of its frames comes`() {
        val m = listening()
        m.handle(Event.BeaconSeen(2_000, gid, 11, ended = false, busy = false))
        m.handle(Event.StateApplied(2_500, seq = 11, ended = false, songIsLocal = false, missing = listOf(SONG to 0x4242)))
        assertTrue(m.handle(Event.Tick(5_250)).none { it is Effect.Repair })
        val repair = assertIs<Effect.Repair>(m.handle(Event.Tick(5_500)).single())
        assertEquals(listOf(SONG to 0x4242), repair.groups)
    }

    @Test
    fun `a local song is not watched from the manifest`() {
        val m = listening(songLocal = true)
        m.handle(Event.StateApplied(2_500, seq = 11, ended = false, songIsLocal = true, missing = listOf(SONG to 0x4242)))
        assertTrue((2_500L..10_000L step 250).flatMap { m.handle(Event.Tick(it)) }.none { it is Effect.Repair })
    }

    @Test
    fun `a repair that comes due takes every document still being collected, the state first`() {
        val m = listening()
        m.handle(Event.FrameProgress(1_500, SONG, 7, have = 5, needed = 125))
        m.handle(Event.BeaconSeen(2_000, gid, 11, ended = false, busy = false))    // a new state is awaited too
        // The song stalls: due 3 s after its last new frame; the state's own repair is not due yet.
        val repair = assertIs<Effect.Repair>((4_500L..4_750L step 250).flatMap { m.handle(Event.Tick(it)) }.single())
        assertEquals(listOf(STATE to 11, SONG to 7), repair.groups)
    }
}
