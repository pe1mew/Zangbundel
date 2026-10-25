// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.protocol.Transport
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The protocol simulation scenarios S-01 to S-06 of `design/test-plan.md` section 3.3: a
 * [LeaderSession] and many [SingerEngine]s in [SimulatedAir], in virtual time. Each prints its
 * measurements (recorded in `design/test-results/`); the assertions are the plan's pass criteria
 * where the design is meant to meet them, and arrival alone where a criterion is known not to hold.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SimulationScenariosTest {
    private val crypto = JvmSodiumCrypto()
    private val ext = Transport.Capabilities(true, true, true, 251)
    private fun song(seed: Int) = Random(seed).nextBytes(SONG_BYTES)

    private class Singer(val engine: SingerEngine, val events: MutableList<SingerEngine.Event>)
    private class Gathering(val leader: LeaderSession, val singers: List<Singer>)

    private suspend fun TestScope.gathering(
        n: Int,
        joinOverMs: Long = 0,
        reception: (Int) -> Double = { 1.0 },
        legacySets: Int = 3,
        extended: Boolean = false,
        local: Boolean = false,
        maxSingers: Int = 100,
    ): Gathering {
        val air = SimulatedAir(backgroundScope).also { it.legacySetsPerDevice = legacySets }
        val clock = { testScheduler.currentTime }
        val caps = if (extended) ext else SimulatedTransport.DEFAULT_CAPABILITIES
        val leader = LeaderSession(SimulatedTransport(air, caps), crypto, backgroundScope, clock, "Testvuur", random = Random(9), maxSingers = maxSingers)
        leader.start(song(1))
        val join = Random(77)
        val singers = List(n) { i ->
            val t = SimulatedTransport(air, caps, reception = reception(i))
            val engine = SingerEngine(t, crypto, backgroundScope, clock, { it.name == "Testvuur" }, hasSong = { local }, random = Random(1000 + i))
            val events = ArrayList<SingerEngine.Event>()
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { events += it } }
            val at = if (joinOverMs > 0) join.nextLong(joinOverMs) else 0
            backgroundScope.launch { delay(at); engine.start() }
            Singer(engine, events)
        }
        return Gathering(leader, singers)
    }

    /** Milliseconds from the push until each singer holds the new song (or, with a local copy, has applied its state). */
    private suspend fun TestScope.push(g: Gathering, seed: Int, limitMs: Long = 60_000): List<Long?> {
        val pushedAt = testScheduler.currentTime
        val song = song(seed)
        g.leader.push(song)
        testScheduler.advanceTimeBy(limitMs)
        return g.singers.map { s ->
            s.events.firstOrNull {
                it.at >= pushedAt && (
                    (it is SingerEngine.Event.Delivered && it.doc == DocumentType.SONG && it.bytes.contentEquals(song)) ||
                        (it is SingerEngine.Event.LocalSong && s.engine.state?.body?.seq == g.leader.seq)
                    )
            }?.let { it.at - pushedAt }
        }
    }

    private fun summary(times: List<Long?>): String {
        val got = times.filterNotNull().sorted()
        if (got.isEmpty()) return "none of ${times.size}"
        return "${got.size} of ${times.size}, median ${got[got.size / 2]} ms, max ${got.last()} ms, min ${got.first()} ms"
    }

    private fun repairs(g: Gathering) = g.singers.sumOf { s -> s.events.count { it is SingerEngine.Event.Repaired } }

    private fun report(id: String, line: String) = println("$id: $line")

    @Test
    fun `S-01 join and first song`() = runTest {
        val g = gathering(25, joinOverMs = 30_000)
        testScheduler.advanceTimeBy(40_000)
        assertEquals(25, g.leader.service.singerCount, "all registered")
        val pushed = song(2)
        val times = push(g, 2)
        report("S-01", "25 singers joined over 30 s, all registered; 4 kB push: ${summary(times)}; repairs ${repairs(g)}")
        assertTrue(times.all { it != null }, "every singer shows the complete song")
        // FR-BLE-04: nothing but verified, complete songs is ever handed out.
        val songs = g.singers.flatMap { s -> s.events.filterIsInstance<SingerEngine.Event.Delivered>().filter { it.doc == DocumentType.SONG } }
        assertTrue(songs.all { it.bytes.contentEquals(song(1)) || it.bytes.contentEquals(pushed) })
    }

    @Test
    fun `S-02 delivery time on the legacy carrier, 10 percent loss`() = runTest {
        val g = gathering(25, reception = { 0.9 })
        testScheduler.advanceTimeBy(30_000)
        val times = (2..4).flatMap { push(g, it) }
        val within5 = times.count { it != null && it <= 5_000 }
        report("S-02", "25 singers, 3 sets, 10 % loss, 3 pushes of 4 kB: ${summary(times)}; within 5 s: $within5 of ${times.size}; repairs ${repairs(g)}; peak connections ${g.leader.service.peakConnections}")
        assertTrue(times.all { it != null }, "every push arrives")
    }

    @Test
    fun `S-03 delivery time with a local copy`() = runTest {
        val g = gathering(25, reception = { 0.9 }, local = true)
        testScheduler.advanceTimeBy(30_000)
        val times = (2..4).flatMap { push(g, it, limitMs = 20_000) }
        val stateRepairs = g.singers.sumOf { s -> s.events.count { it is SingerEngine.Event.Repaired && it.groups.any { (doc, _) -> doc == DocumentType.STATE } } }
        val songRepairs = g.singers.sumOf { s -> s.events.count { it is SingerEngine.Event.Repaired && it.groups.any { (doc, _) -> doc == DocumentType.SONG } } }
        report("S-03", "25 singers with the song locally, 10 % loss: ${summary(times)}; within 2 s ${times.count { it != null && it <= 2_000 }}; repairs ${repairs(g)} (state $stateRepairs, song $songRepairs)")
        // The 2 s criterion is not met yet (test-results 2026-10-25-S-01-06); arrival is.
        assertTrue(times.all { it != null }, "every singer applies the state")
    }

    @Test
    fun `S-04 group size`() = runTest {
        for ((n, sets, extended) in listOf(Triple(50, 3, false), Triple(50, 1, false), Triple(50, 3, true), Triple(100, 3, false), Triple(100, 3, true))) {
            val g = gathering(n, joinOverMs = 30_000, reception = { 0.9 }, legacySets = sets, extended = extended)
            testScheduler.advanceTimeBy(60_000)
            val times = push(g, 2)
            report(
                "S-04",
                "$n singers, $sets legacy sets, extended ${if (extended) "on" else "off"}, 10 % loss: registered ${g.leader.service.singerCount}; " +
                    "${summary(times)}; within 5 s ${times.count { it != null && it <= 5_000 }}; repairs ${repairs(g)}; " +
                    "peak connections ${g.leader.service.peakConnections} of ${g.leader.service.maxConnections}, refused ${g.leader.service.refusedConnections}",
            )
            assertTrue(times.all { it != null }, "every singer gets the push")
            g.leader.stop()
        }
        // Beyond the table limit: join result 2, the "full" message.
        val g = gathering(50, joinOverMs = 20_000, maxSingers = 40)
        testScheduler.advanceTimeBy(40_000)
        val full = g.singers.count { s -> s.events.any { it is SingerEngine.Event.Joined && it.result == JoinResponse.FULL } }
        report("S-04", "50 singers, table limit 40: registered ${g.leader.service.singerCount}, refused as full $full")
        assertEquals(40, g.leader.service.singerCount)
        assertEquals(10, full)
    }

    @Test
    fun `S-05 degraded mode, no data set`() = runTest {
        val g = gathering(25, joinOverMs = 30_000, legacySets = 0)
        testScheduler.advanceTimeBy(40_000)
        assertEquals(25, g.leader.service.singerCount)
        val times = (2..4).flatMap { push(g, it) }
        report("S-05", "25 singers, BCAST 0: ${summary(times)}; repairs ${repairs(g)}; peak connections ${g.leader.service.peakConnections} of ${g.leader.service.maxConnections}, refused ${g.leader.service.refusedConnections}")
        assertTrue(times.all { it != null }, "everything arrives over GATT")
    }

    @Test
    fun `S-06 repair, 30 percent loss on a third of the singers`() = runTest {
        val g = gathering(24, reception = { if (it % 3 == 0) 0.7 else 1.0 })
        testScheduler.advanceTimeBy(30_000)
        val before = g.leader.service.bytesServed
        val transfersBefore = g.leader.service.transfersServed
        val times = (2..4).flatMap { push(g, it) }
        val bytes = g.leader.service.bytesServed - before
        val transfers = g.leader.service.transfersServed - transfersBefore
        report(
            "S-06",
            "24 singers, 8 at 30 % loss: ${summary(times)}; transfers $transfers carrying $bytes bytes " +
                "(${if (transfers > 0) bytes / transfers else 0} per transfer, a whole song is $SONG_BYTES); " +
                "peak connections ${g.leader.service.peakConnections} of ${g.leader.service.maxConnections}",
        )
        assertTrue(times.all { it != null })
        assertTrue(g.leader.service.peakConnections <= g.leader.service.maxConnections, "slots never exceeded")
        assertTrue(transfers == 0 || bytes < transfers.toLong() * SONG_BYTES, "only missing ranges are requested")
    }

    companion object {
        /** A 4 kB song not held locally (test plan S-01, S-02). */
        const val SONG_BYTES = 4000
    }
}
