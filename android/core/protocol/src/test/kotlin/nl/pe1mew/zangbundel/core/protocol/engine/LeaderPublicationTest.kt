// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.payload.Deflate
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

/** Real songs and setlists through [LeaderSession] to a [SingerEngine] (ble-protocol 9.5, 10.5, 10.10). */
@OptIn(ExperimentalCoroutinesApi::class)
class LeaderPublicationTest {
    private val crypto = JvmSodiumCrypto()
    private val file = javaClass.classLoader.getResourceAsStream("conformance/songs/vuur-in-de-nacht.cho")!!.use { it.readBytes() }
    private val ref = SongRef(LibraryId("zangbundel-voorbeeld"), SongId("vuur-in-de-nacht"), ContentHash(crypto.sha256(file).copyOf(ContentHash.SIZE)))

    @Test
    fun `a gathering starts empty, then carries the song and the setlist as sung so far, and ends`() = runTest {
        val air = SimulatedAir(backgroundScope)
        val clock = { testScheduler.currentTime }
        val leader = LeaderSession(SimulatedTransport(air), crypto, backgroundScope, clock, "Kampvuur", random = Random(3))
        leader.start() // D-25: the lyric view is empty until the first song is pushed
        val singer = SingerEngine(SimulatedTransport(air, reception = 0.2), crypto, backgroundScope, clock, { it.name == "Kampvuur" }, random = Random(4))
        val events = ArrayList<SingerEngine.Event>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { singer.events.collect { events += it } }
        singer.start()
        val stays = SingerEngine(SimulatedTransport(air, reception = 1.0), crypto, backgroundScope, clock, { it.name == "Kampvuur" }, random = Random(5))
        val staysEvents = ArrayList<SingerEngine.Event>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { stays.events.collect { staysEvents += it } }
        stays.start()
        testScheduler.advanceTimeBy(15_000)
        assertNull(singer.state!!.body.cur, "no current song before the first push")

        val setlist = SetlistDocument(listOf(SetlistDocument.Item(ref, "Vuur in de nacht", status = 1)))
        assertNull(leader.progress(), "no song pushed yet")
        leader.push(LeaderSession.Publication(ref, "Vuur in de nacht", SongDocument(ref, file, "© 2026 Zangbundel-project"), setlist))
        testScheduler.advanceTimeBy(200)
        assertEquals(0, leader.progress()!!.passesDone, "D-56: the burst has only begun")
        testScheduler.advanceTimeBy(20_000)
        val progress = leader.progress()!!
        assertEquals(3, progress.passesDone)
        val fetched = events.filterIsInstance<SingerEngine.Event.Delivered>().count { it.doc == DocumentType.SONG && it.viaRepair }
        assertEquals(fetched, progress.repairs, "every song fetched over GATT is one repair on the leader")
        // D-56 (v0.21): the share of singers helped over GATT in a window, and nothing once it has passed.
        val helped = listOf(events, staysEvents).count { ev -> ev.any { it is SingerEngine.Event.Repaired && it.ok && it.at >= testScheduler.currentTime - 25_000 } }
        assertTrue(helped > 0, "the singer at 20 % reception fetches over GATT")
        assertEquals(helped / 2f, leader.helpShare(25_000))
        testScheduler.advanceTimeBy(10_000)
        assertEquals(0f, leader.helpShare())
        val song = events.filterIsInstance<SingerEngine.Event.Delivered>().last { it.doc == DocumentType.SONG }
        assertEquals(Deflate.ENC_DEFLATE, song.enc, "a song file compresses by more than 10 % (7.3)")
        val received = SongDocument.decode(song.document())
        assertEquals(ref, received.ref)
        assertContentEquals(file, received.file)
        assertContentEquals(ref.hash.bytes, crypto.sha256(received.file).copyOf(ContentHash.SIZE), "FR-BLE-04: the file matches its reference")
        val list = SetlistDocument.decode(events.filterIsInstance<SingerEngine.Event.Delivered>().last { it.doc == DocumentType.SETLIST }.document())
        assertEquals(listOf("Vuur in de nacht"), list.items.map { it.title })

        // FR-LIB-22: a song not to be redistributed travels as its reference and title only.
        val other = SongRef(LibraryId("zangbundel-voorbeeld"), SongId("alleen-tonen"), ContentHash(ByteArray(ContentHash.SIZE) { 7 }))
        leader.push(LeaderSession.Publication(other, "Alleen tonen"))
        testScheduler.advanceTimeBy(20_000)
        val body = singer.state!!.body
        assertEquals(other, body.cur)
        assertEquals("Alleen tonen", body.title)
        assertTrue(DocumentType.SONG !in body.docs)

        // FR-GATH-07: a singer that leaves on purpose is out of the count at once (10.11, Control op 10).
        assertEquals(2, leader.singers)
        singer.leave()
        testScheduler.advanceTimeBy(1_000)
        assertEquals(1, leader.singers)

        backgroundScope.launch { leader.end(lingerMs = 30_000) }
        testScheduler.advanceTimeBy(20_000)
        // 10.11: the beacon's ENDED alone is enough; the final state may never be collected.
        assertTrue(staysEvents.any { it is SingerEngine.Event.Ended }, "10.10: the end reaches the singer while the leader lingers")
    }
}
