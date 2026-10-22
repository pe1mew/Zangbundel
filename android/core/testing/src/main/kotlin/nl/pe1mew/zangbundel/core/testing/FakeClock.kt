// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.testing

/** A clock the tests advance by hand; the protocol's timers take a clock like this (ble-protocol 7.6). */
class FakeClock(startMillis: Long = 0L) {
    var nowMillis: Long = startMillis
        private set

    fun advance(millis: Long) {
        require(millis >= 0)
        nowMillis += millis
    }
}
