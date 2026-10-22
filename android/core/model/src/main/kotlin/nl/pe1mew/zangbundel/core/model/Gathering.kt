// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.model

/** Role of this device in a gathering (FRD section 2.2). A device is never both. */
enum class Role { LEADER, SINGER }

/** Singer-side states of ble-protocol section 3.3. */
enum class SingerState { IDLE, DISCOVERED, REGISTERING, LISTENING, REPAIRING, RECONNECTING, ENDED }

/** Leader-side lifecycle (ble-protocol sections 10.1, 10.10). */
enum class LeaderState { IDLE, PRE_START_SCAN, RUNNING, ENDING, ENDED }

/** Gathering ID: the first 5 bytes of SHA-256 over the gathering public key (ble-protocol section 5). */
class GatheringId(bytes: ByteArray) {
    val bytes: ByteArray = bytes.copyOf()
    init { require(bytes.size == SIZE) { "gathering id must be $SIZE bytes" } }
    override fun equals(other: Any?): Boolean = other is GatheringId && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
    override fun toString(): String = "GatheringId(" + bytes.joinToString("") { "%02x".format(it) } + ")"
    companion object { const val SIZE = 5 }
}
