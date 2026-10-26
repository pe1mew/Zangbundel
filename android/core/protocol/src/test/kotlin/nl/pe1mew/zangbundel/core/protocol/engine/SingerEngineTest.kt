// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.frame.Carrier
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** A leader and singers in [SimulatedAir], in virtual time (ble-protocol 10.2–10.5, test plan S-nn). */
@OptIn(ExperimentalCoroutinesApi::class)
class SingerEngineTest {
    private val crypto = JvmSodiumCrypto()
    private fun song(seed: Int) = Random(seed).nextBytes(2000)

    private class Singer(val engine: SingerEngine, val events: MutableList<SingerEngine.Event>)

    private suspend fun TestScope.gathering(
        receptions: List<Double>,
        extendedSingers: Boolean = false,
        leaderExtended: Boolean = false,
        extendedReception: Double? = null,
        dataReception: Double? = null,
    ): Pair<LeaderSession, List<Singer>> {
        val air = SimulatedAir(backgroundScope)
        val clock = { testScheduler.currentTime }
        val ext = Transport.Capabilities(true, true, true, 251)
        val leader = LeaderSession(SimulatedTransport(air, if (leaderExtended) ext else SimulatedTransport.DEFAULT_CAPABILITIES), crypto, backgroundScope, clock, "Testvuur", random = Random(9))
        leader.start(song(1))
        val singers = receptions.mapIndexed { i, r ->
            val t = SimulatedTransport(air, if (extendedSingers) ext else SimulatedTransport.DEFAULT_CAPABILITIES, reception = r, extendedReception = extendedReception, dataReception = dataReception)
            val engine = SingerEngine(t, crypto, backgroundScope, clock, { it.name == "Testvuur" }, random = Random(100 + i))
            val events = ArrayList<SingerEngine.Event>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { events += it } }
            engine.start()
            Singer(engine, events)
        }
        return leader to singers
    }

    /** Milliseconds from the push until each singer holds the new song, or null if it never did. */
    private suspend fun TestScope.pushAndTime(leader: LeaderSession, singers: List<Singer>, seed: Int, limitMs: Long = 120_000): List<Long?> {
        val pushedAt = testScheduler.currentTime
        val song = song(seed)
        leader.push(song)
        testScheduler.advanceTimeBy(limitMs)
        return singers.map { s ->
            s.events.filterIsInstance<SingerEngine.Event.Delivered>()
                .firstOrNull { it.doc == DocumentType.SONG && it.at >= pushedAt && it.bytes.contentEquals(song) }
                ?.let { it.at - pushedAt }
        }
    }

    @Test
    fun `at full reception the singer registers once and has each push within 5 s`() = runTest {
        val (leader, singers) = gathering(listOf(1.0))
        testScheduler.advanceTimeBy(20_000)
        val s = singers.single()
        assertEquals(listOf(JoinResponse.ACCEPTED), s.events.filterIsInstance<SingerEngine.Event.Joined>().map { it.result })
        assertEquals(1, leader.service.singerCount)
        val t = pushAndTime(leader, singers, 2).single()!!
        println("full reception, 3 legacy sets: song after $t ms, repairs ${s.events.count { it is SingerEngine.Event.Repaired }}")
        // v0.24: the whole state opens the burst, so a 2 kB song by broadcast would take about 5.7 s on
        // three legacy sets; the 5 s deadline of 7.6 sends even this singer to GATT for it.
        assertTrue(t < 5_000, "$t ms")
        assertContentEquals(song(2), s.events.filterIsInstance<SingerEngine.Event.Delivered>().last { it.doc == DocumentType.SONG }.bytes)
    }

    @Test
    fun `at 13 percent reception repair over GATT completes every push`() = runTest {
        val (leader, singers) = gathering(listOf(0.13))
        testScheduler.advanceTimeBy(30_000)
        assertEquals(1, leader.service.singerCount)
        val times = (2..6).map { pushAndTime(leader, singers, it, limitMs = 60_000).single() }
        println("13 % reception, with repair: $times ms")
        assertTrue(times.all { it != null && it < 15_000 }, "$times")
        val events = singers.single().events
        assertTrue(events.filterIsInstance<SingerEngine.Event.Delivered>().any { it.doc == DocumentType.SONG && it.viaRepair })
        assertTrue(events.filterIsInstance<SingerEngine.Event.Repaired>().all { it.ok })
    }

    @Test
    fun `twenty singers at 13 to 40 percent all register and get every push`() = runTest {
        val receptions = List(20) { 0.13 + 0.27 * it / 19 }
        val (leader, singers) = gathering(receptions)
        testScheduler.advanceTimeBy(60_000)
        assertEquals(20, leader.service.singerCount)
        val times = pushAndTime(leader, singers, 2, limitMs = 60_000)
        val sorted = times.map { it ?: Long.MAX_VALUE }.sorted()
        println("20 singers, 13–40 %: median ${sorted[10]} ms, max ${sorted.last()} ms, repairs ${singers.sumOf { s -> s.events.count { it is SingerEngine.Event.Repaired } }}")
        assertTrue(times.all { it != null && it < 30_000 }, "$times")
    }

    @Test
    fun `an extended singer follows a legacy-only leader on the legacy carrier`() = runTest {
        val (leader, singers) = gathering(listOf(1.0), extendedSingers = true, leaderExtended = false)
        testScheduler.advanceTimeBy(20_000)
        val t = pushAndTime(leader, singers, 2).single()
        println("extended singer, legacy-only leader: song after $t ms")
        assertTrue(t != null && t < 5_000, "$t")
        assertEquals(Carrier.LEGACY, singers.single().engine.carrier)
    }

    @Test
    fun `a leader whose extended set is not heard, as the A52's, the extended singer falls back to legacy after 3 s`() = runTest {
        val (leader, singers) = gathering(listOf(1.0), extendedSingers = true, leaderExtended = true, extendedReception = 0.0)
        testScheduler.advanceTimeBy(30_000)
        assertEquals(1, leader.service.singerCount)
        assertEquals(Carrier.LEGACY, singers.single().engine.carrier)
        val times = (2..4).map { pushAndTime(leader, singers, it, limitMs = 30_000).single() }
        println("extended set not heard, after the fallback: $times ms")
        assertTrue(times.all { it != null && it < 5_000 }, "$times")
    }

    @Test
    fun `an extended singer that hears the extended set only now and then collects legacy`() = runTest {
        // The S23 with the A52 leading: the extended set heard 0.2 times a second, the legacy sets a third of the time.
        val (_, singers) = gathering(listOf(0.33), extendedSingers = true, leaderExtended = true, extendedReception = 0.02)
        testScheduler.advanceTimeBy(30_000)
        assertEquals(Carrier.LEGACY, singers.single().engine.carrier)
    }

    @Test
    fun `an extended singer that hears the extended set stays on it`() = runTest {
        val (_, singers) = gathering(listOf(1.0), extendedSingers = true, leaderExtended = true)
        testScheduler.advanceTimeBy(10_000)
        assertEquals(Carrier.EXTENDED, singers.single().engine.carrier)
    }

    @Test
    fun `a singer that hears beacons but no frames gets each push on one repair connection`() = runTest {
        val (leader, singers) = gathering(listOf(1.0), dataReception = 0.0)
        testScheduler.advanceTimeBy(30_000)
        assertEquals(1, leader.service.singerCount)
        val s = singers.single()
        assertTrue(s.events.filterIsInstance<SingerEngine.Event.Delivered>().any { it.doc == DocumentType.SONG }, "the first song, on the registration connection")
        val before = s.events.count { it is SingerEngine.Event.Repaired }
        val times = (2..5).map { pushAndTime(leader, singers, it, limitMs = 30_000).single() }
        println("no frames heard, state and song on one connection: $times ms")
        assertTrue(times.all { it != null && it < 6_000 }, "$times")
        assertEquals(4, s.events.count { it is SingerEngine.Event.Repaired } - before, "one repair connection per push")
    }

    @Test
    fun `an extended singer with an extended leader gets the song by broadcast within 2 s`() = runTest {
        val (leader, singers) = gathering(listOf(1.0), extendedSingers = true, leaderExtended = true)
        testScheduler.advanceTimeBy(20_000)
        val t = pushAndTime(leader, singers, 2).single()!!
        println("extended both sides: song after $t ms")
        assertTrue(t < 2_000, "$t ms")
        assertTrue(singers.single().events.none { it is SingerEngine.Event.Repaired })
    }

    @Test
    fun `the reception bar shows the song still to come, then complete`() = runTest {
        // Gaps within a version are covered by ReassemblerTest; since v0.34 a song not held ahead mostly comes by repair,
        // because its frames follow the state's lead-in.
        val (leader, singers) = gathering(listOf(0.5))
        testScheduler.advanceTimeBy(30_000)
        val engine = singers.single().engine
        leader.push(song(2))
        testScheduler.advanceTimeBy(1_000)
        val during = engine.reception().single { it.doc == DocumentType.SONG }.blocks
        assertTrue(Reassembler.Block.RECEIVED !in during, "nothing of the song yet: $during")
        testScheduler.advanceTimeBy(20_000)
        val after = engine.reception().single { it.doc == DocumentType.SONG }.blocks
        assertTrue(after.isNotEmpty() && after.all { it == Reassembler.Block.RECEIVED }, "$after")
    }
}
