// SPDX-License-Identifier: Apache-2.0
package nl.pe1mew.zangbundel.core.protocol

import kotlin.test.Test
import kotlin.test.assertEquals

class ProtocolConstantsTest {
    @Test
    fun `characteristic uuids follow the base uuid of ble-protocol 8-1`() {
        assertEquals("7e2b0001-9f1c-4a7d-b2d4-5a4e47424c45", ProtocolConstants.uuidOf(ProtocolConstants.CHAR_SERVICE))
        assertEquals("7e2b0009-9f1c-4a7d-b2d4-5a4e47424c45", ProtocolConstants.uuidOf(ProtocolConstants.CHAR_CONTROL))
    }

    @Test
    fun `an extended frame fits the largest structure measured on Android`() {
        // ble-protocol 7.1: at most 251 bytes per structure on the first measured phone.
        assertEquals(233, ProtocolConstants.EXTENDED_FRAME_MAX_DATA_BYTES)
        // The legacy frame fits the 27 bytes a non-connectable legacy manufacturer-data structure leaves.
        assertEquals(26, ProtocolConstants.LEGACY_FRAME_HEADER_BYTES + ProtocolConstants.LEGACY_FRAME_DATA_BYTES)
        assertEquals(251, ProtocolConstants.EXTENDED_FRAME_MAX_BYTES + ProtocolConstants.MANUFACTURER_AD_OVERHEAD_BYTES)
    }

    @Test
    fun `legacy document limit is 255 frames of 16 bytes`() {
        assertEquals(4080, ProtocolConstants.LEGACY_DOCUMENT_MAX_BYTES)
    }
}
