// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.app.gathering

import nl.pe1mew.zangbundel.core.model.GatheringId
import nl.pe1mew.zangbundel.core.protocol.beacon.Fingerprint

/**
 * Other gatherings heard while leading (FR-GATH-17, ble-protocol 10.1 step 6, guiDesign D-68): beacons of
 * ended gatherings, relays and this gathering's own `gid` are ignored; one heard within [timeoutMs] is
 * near, and the one heard last is named. Thread-safe: the scan callback adds, the scan loop reads.
 */
internal class OtherCampfires(private val own: GatheringId, private val timeoutMs: Long) {
    private val heard = HashMap<GatheringId, Pair<String, Long>>()

    @Synchronized
    fun seen(gid: GatheringId, name: String, ended: Boolean, relay: Boolean, now: Long) {
        if (ended || relay || gid == own) return
        heard[gid] = name to now
    }

    /**
     * How the other gathering heard last is named in the drawer, or null when none was heard within the
     * timeout: its name, or its two words (FR-GATH-18) when the name is this gathering's own [ownName],
     * as two default names of the same day are (owner, 2026-10-27).
     */
    @Synchronized
    fun near(now: Long, ownName: String): String? {
        heard.entries.removeAll { now - it.value.second > timeoutMs }
        val (gid, name) = heard.maxByOrNull { it.value.second }?.let { it.key to it.value.first } ?: return null
        return if (name.trim().equals(ownName.trim(), ignoreCase = true)) Fingerprint.of(gid) else name
    }
}
