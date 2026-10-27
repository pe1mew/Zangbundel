// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.model.ContentHash
import nl.pe1mew.zangbundel.core.model.LibraryId
import nl.pe1mew.zangbundel.core.model.SongId
import nl.pe1mew.zangbundel.core.model.SongRef
import nl.pe1mew.zangbundel.core.protocol.gatt.LeaderGattService
import nl.pe1mew.zangbundel.core.protocol.payload.PayloadException
import nl.pe1mew.zangbundel.core.protocol.payload.SongDocument
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Resume after the leader's app died (ble-protocol 10.1 step 2, 10.9 v0.39; guiDesign D-67). */
@OptIn(ExperimentalCoroutinesApi::class)
class ResumeTest {
    private val crypto = JvmSodiumCrypto()

    private fun song(name: String): Pair<SongRef, ByteArray> {
        val file = javaClass.classLoader.getResourceAsStream("conformance/songs/$name.cho")!!.use { it.readBytes() }
        return SongRef(LibraryId("zangbundel-voorbeeld"), SongId(name), ContentHash(crypto.sha256(file).copyOf(ContentHash.SIZE))) to file
    }

    @Test
    fun `the record decodes to what was encoded`() {
        val record = ResumeRecord(
            LeaderSession.Resume(
                crypto.generateSigningKeyPair(), crypto.generateBoxKeyPair(), "4711", seq = 65_535, ts = 123_456,
                singers = listOf(LeaderGattService.Registered(ByteArray(8) { 1 }, ByteArray(16) { 2 }, ByteArray(32) { 3 })),
            ),
            name = "Kampvuur", protection = ResumeRecord.PROTECTION_CODE,
            setlist = listOf(ResumeRecord.Position("zangbundel-voorbeeld/vuur-in-de-nacht", "Vuur in de nacht"), ResumeRecord.Position(null, "Niet gevonden")),
            pushed = listOf(1, 0), current = 0, startedWall = 1_000, writtenWall = 2_000, peak = 7,
        )
        val back = ResumeRecord.decode(record.encode())
        assertContentEquals(record.session.signing.secretKey, back.session.signing.secretKey)
        assertContentEquals(record.session.box.secretKey, back.session.box.secretKey)
        assertEquals(listOf("4711", "65535", "123456"), listOf(back.session.joinCode, back.session.seq.toString(), back.session.ts.toString()))
        assertContentEquals(record.session.singers.single().tok, back.session.singers.single().tok)
        assertEquals(listOf("zangbundel-voorbeeld/vuur-in-de-nacht", null), back.setlist.map { it.songKey })
        assertEquals(listOf(1, 0), back.pushed)
        assertEquals(listOf(0, 7, 2_000), listOf(back.current, back.peak, back.writtenWall.toInt()))
    }

    @Test
    fun `a record that does not decode, or names a position outside its setlist, is refused`() {
        assertFailsWith<PayloadException> { ResumeRecord.decode(byteArrayOf(0x01, 0x02, 0x03)) }
        val ok = ResumeRecord(
            LeaderSession.Resume(crypto.generateSigningKeyPair(), crypto.generateBoxKeyPair(), null, 1, 1, emptyList()),
            "Kampvuur", ResumeRecord.PROTECTION_OPEN, setlist = emptyList(), pushed = emptyList(), current = null,
            startedWall = 0, writtenWall = 0, peak = 0,
        ).encode()
        ResumeRecord.decode(ok)
        val bad = ResumeRecord(
            LeaderSession.Resume(crypto.generateSigningKeyPair(), crypto.generateBoxKeyPair(), null, 1, 1, emptyList()),
            "Kampvuur", ResumeRecord.PROTECTION_OPEN, setlist = emptyList(), pushed = listOf(3), current = null,
            startedWall = 0, writtenWall = 0, peak = 0,
        ).encode()
        assertFailsWith<PayloadException> { ResumeRecord.decode(bad) }
    }

    @Test
    fun `a singer carries on with a leader resumed from its snapshot`() = runTest {
        val air = SimulatedAir(backgroundScope)
        val clock = { testScheduler.currentTime }
        val (ref1, file1) = song("vuur-in-de-nacht")
        val (ref2, file2) = song("vader-jacob")
        val first = CoroutineScope(backgroundScope.coroutineContext + Job())
        val leader = LeaderSession(SimulatedTransport(air), crypto, first, clock, "Kampvuur", random = Random(3))
        leader.start(LeaderSession.Publication(ref1, "Vuur in de nacht", SongDocument(ref1, file1, null)))
        val singer = SingerEngine(SimulatedTransport(air, reception = 1.0), crypto, backgroundScope, clock, { it.name == "Kampvuur" }, random = Random(4))
        singer.start()
        testScheduler.advanceTimeBy(20_000)
        assertEquals(ref1, singer.state!!.body.cur)
        assertEquals(1, leader.singers, "registered before the app dies")

        // The app dies: what was persisted last is all the next process has.
        val saved = ResumeRecord.decode(
            ResumeRecord(leader.snapshot(), "Kampvuur", ResumeRecord.PROTECTION_OPEN, emptyList(), emptyList(), null, 0, 0, 1).encode(),
        ).session
        val lastTs = singer.state!!.body.ts
        leader.stop()
        first.cancel()
        testScheduler.advanceTimeBy(30_000)

        val resumed = LeaderSession(SimulatedTransport(air), crypto, backgroundScope, clock, "Kampvuur", resume = saved)
        assertEquals(leader.gid, resumed.gid, "the same gathering")
        assertEquals(1, resumed.singers, "the singer table is back")
        resumed.start(LeaderSession.Publication(ref2, "Vader Jacob", SongDocument(ref2, file2, null)))
        testScheduler.advanceTimeBy(20_000)
        assertEquals(ref2, singer.state!!.body.cur, "the singer took the resumed leader's state")
        assertTrue(singer.state!!.body.ts > lastTs, "9.4: ts went on from where it was")
        assertEquals((saved.seq + 1) and 0xFFFF, singer.state!!.body.seq, "seq + 1, so a burst follows")
    }
}
