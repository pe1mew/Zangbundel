// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol.engine

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import nl.pe1mew.zangbundel.core.protocol.payload.JoinResponse
import nl.pe1mew.zangbundel.core.testing.JvmSodiumCrypto
import nl.pe1mew.zangbundel.core.testing.SimulatedAir
import nl.pe1mew.zangbundel.core.testing.SimulatedTransport
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The join code (FR-GATH-05) and the key pinned from the QR code (FR-GATH-19, 20; ble-protocol 6.5). */
@OptIn(ExperimentalCoroutinesApi::class)
class JoinProtectionTest {
    private val crypto = JvmSodiumCrypto()

    private class Singer(val engine: SingerEngine, val events: MutableList<SingerEngine.Event>)

    private fun TestScope.singer(air: SimulatedAir, leader: LeaderSession, pinnedKey: ByteArray? = null, code: String? = null, seed: Int = 4): Singer {
        val engine = SingerEngine(SimulatedTransport(air, reception = 1.0), crypto, backgroundScope, { testScheduler.currentTime },
            { it.gid == leader.gid }, random = Random(seed), pinnedKey = pinnedKey, code = code)
        val events = ArrayList<SingerEngine.Event>()
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { events += it } }
        engine.start()
        return Singer(engine, events)
    }

    private fun Singer.joins() = events.filterIsInstance<SingerEngine.Event.Joined>().map { it.result }

    @Test
    fun `a wrong join code is refused, the right one accepted`() = runTest {
        val air = SimulatedAir(backgroundScope)
        val leader = LeaderSession(SimulatedTransport(air), crypto, backgroundScope, { testScheduler.currentTime }, "Kampvuur", random = Random(3), joinCode = "4711")
        leader.start()
        val s = singer(air, leader, code = "1234")
        testScheduler.advanceTimeBy(15_000)
        assertEquals(listOf(JoinResponse.WRONG_CODE), s.joins())
        assertEquals(0, leader.singers)
        s.engine.enterCode("4711")
        testScheduler.advanceTimeBy(10_000)
        assertEquals(listOf(JoinResponse.WRONG_CODE, JoinResponse.ACCEPTED), s.joins())
        assertEquals(1, leader.singers)
    }

    @Test
    fun `a pinned key joins its own gathering and refuses another signed with a different key`() = runTest {
        val air = SimulatedAir(backgroundScope)
        val leader = LeaderSession(SimulatedTransport(air), crypto, backgroundScope, { testScheduler.currentTime }, "Kampvuur", random = Random(3))
        leader.start()
        val right = singer(air, leader, pinnedKey = leader.publicKey)
        // A key that is not the leader's: its states never verify, as with a forged gathering under the same gid.
        val wrong = singer(air, leader, pinnedKey = crypto.generateSigningKeyPair().publicKey, seed = 5)
        testScheduler.advanceTimeBy(20_000)
        assertEquals(listOf(JoinResponse.ACCEPTED), right.joins())
        assertTrue(wrong.joins().isEmpty(), "never registers")
        assertTrue(wrong.events.any { it is SingerEngine.Event.NotTheCampfire }, "D-35: the singer is told")
        assertEquals(1, leader.singers)
    }
}
