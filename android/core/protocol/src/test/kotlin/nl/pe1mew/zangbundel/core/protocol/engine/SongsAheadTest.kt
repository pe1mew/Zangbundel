// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.frame.Reassembler
import nl.pe1mew.zangbundel.core.protocol.payload.DocumentType
import nl.pe1mew.zangbundel.core.protocol.payload.SetlistDocument
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Songs ahead (ble-protocol 7.4 rule 7, 7.5, 9.5 as of v0.32; FRD FR-BLE-18): sent from the start, shown only when pushed. */
@OptIn(ExperimentalCoroutinesApi::class)
class SongsAheadTest {
    private val crypto = JvmSodiumCrypto()

    private class Song(val ref: SongRef, val title: String, val document: SongDocument)

    private fun song(i: Int): Song {
        val file = Random(i).nextBytes(2000)
        val ref = SongRef(LibraryId("test"), SongId("lied-$i"), ContentHash(crypto.sha256(file).copyOf(ContentHash.SIZE)))
        return Song(ref, "Lied $i", SongDocument(ref, file, "CC0"))
    }

    private val songs = (1..3).map(::song)

    private class Singer(val engine: SingerEngine, val events: MutableList<SingerEngine.Event>)

    private suspend fun TestScope.campfire(reception: Double, ahead: Boolean = true): Pair<LeaderSession, Singer> {
        val air = SimulatedAir(backgroundScope)
        val clock = { testScheduler.currentTime }
        val leader = LeaderSession(SimulatedTransport(air), crypto, backgroundScope, clock, "Kampvuur", random = Random(3))
        leader.start(LeaderSession.Publication(ahead = if (ahead) songs.map { it.document } else null))
        val engine = SingerEngine(SimulatedTransport(air, reception = reception), crypto, backgroundScope, clock, { it.name == "Kampvuur" }, random = Random(4))
        val events = ArrayList<SingerEngine.Event>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { events += it } }
        engine.start()
        return leader to Singer(engine, events)
    }

    private suspend fun LeaderSession.push(i: Int) {
        val s = songs[i]
        push(LeaderSession.Publication(s.ref, s.title, s.document, SetlistDocument(listOf(SetlistDocument.Item(s.ref, s.title, SetlistDocument.CURRENT)))))
    }

    /** Milliseconds from now until the singer delivers song [i] as the current song, after running [limitMs]. */
    private suspend fun TestScope.pushAndTime(leader: LeaderSession, singer: Singer, i: Int, limitMs: Long = 60_000): Long? {
        val pushedAt = testScheduler.currentTime
        leader.push(i)
        testScheduler.advanceTimeBy(limitMs)
        return singer.events.filterIsInstance<SingerEngine.Event.Delivered>()
            .firstOrNull { it.doc == DocumentType.SONG && it.at >= pushedAt && SongDocument.decode(it.document()).ref == songs[i].ref }
            ?.let { it.at - pushedAt }
    }

    @Test
    fun `before the first push every song arrives ahead, and none is delivered`() = runTest {
        val (_, singer) = campfire(reception = 0.4)
        testScheduler.advanceTimeBy(6 * 60_000)
        assertTrue(singer.events.filterIsInstance<SingerEngine.Event.Delivered>().none { it.doc == DocumentType.SONG }, "FR-BLE-18: nothing shown before a push")
        val rows = singer.engine.reception().filter { it.doc == DocumentType.AHEAD }
        assertEquals(listOf(1, 2, 3), rows.map { it.position }, "a row per setlist position")
        assertTrue(rows.all { it.blocks == listOf(Reassembler.Block.RECEIVED) }, "every song held ahead after six minutes at 40 %")
        assertTrue(rows.all { it.ref == null }, "D-47: no reference before the push")
    }

    @Test
    fun `a song held ahead is shown as soon as the state that pushes it arrives`() = runTest {
        val (leader, singer) = campfire(reception = 0.4)
        testScheduler.advanceTimeBy(6 * 60_000)
        val ms = pushAndTime(leader, singer, 1)!!
        assertTrue(ms < 3_000, "held ahead: shown with the state record, not after the song's burst; took $ms ms")
        val songRepairs = singer.events.filterIsInstance<SingerEngine.Event.Repaired>().filter { r -> r.groups.any { it.first == DocumentType.SONG || it.first == DocumentType.AHEAD } }
        assertTrue(songRepairs.isEmpty(), "no repair connection for a song held ahead, nor for songs ahead (7.6)")
        val row = singer.engine.reception().first { it.doc == DocumentType.AHEAD && it.position == 2 }
        assertTrue(row.current)
        assertEquals(songs[1].ref, row.ref, "a pushed song's row carries its reference for the title (D-81)")
        val delivered = singer.events.filterIsInstance<SingerEngine.Event.Delivered>().last { it.doc == DocumentType.SONG }
        assertContentEquals(songs[1].document.file, SongDocument.decode(delivered.document()).file)
        assertEquals(1, singer.events.filterIsInstance<SingerEngine.Event.Delivered>().count { it.doc == DocumentType.SONG }, "delivered once, not again when the burst completes")
    }

    @Test
    fun `at 13 percent the state is fetched a second after its pass went by, and the song held ahead with it`() = runTest {
        val (leader, singer) = campfire(reception = 0.13)
        testScheduler.advanceTimeBy(10 * 60_000)
        val ms = pushAndTime(leader, singer, 1)!!
        assertTrue(ms < 4_000, "v0.32 took 4.2 to 4.5 s, waiting 3 s of silence; took $ms ms")
    }

    @Test
    fun `without songs ahead the push still arrives, more slowly`() = runTest {
        val (leader, singer) = campfire(reception = 0.4, ahead = false)
        testScheduler.advanceTimeBy(60_000)
        val ms = pushAndTime(leader, singer, 1)
        assertTrue(ms != null && ms > 3_000, "the burst or a repair, as before v0.32; took $ms ms")
        assertTrue(singer.engine.reception().none { it.doc == DocumentType.AHEAD }, "no setlist rows without songs ahead")
    }

    @Test
    fun `a song pushed before it arrived ahead comes by the burst as before`() = runTest {
        val (leader, singer) = campfire(reception = 0.4)
        testScheduler.advanceTimeBy(5_000)
        assertNull(singer.events.filterIsInstance<SingerEngine.Event.Delivered>().firstOrNull { it.doc == DocumentType.SONG })
        val ms = pushAndTime(leader, singer, 2)
        assertTrue(ms != null, "delivered by the burst or by repair")
    }
}
