package io.github.jagadeeshm007.pin.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class AppVersionTest {
    @ParameterizedTest
    @CsvSource(
        "0.1.0, 1, 0.1.0 (1)",
        "'', 7, unknown (7)",
        "'   ', 7, unknown (7)",
    )
    fun formatsNameAndCode(name: String, code: Long, expected: String) {
        assertEquals(expected, AppVersion.label(name, code))
    }

    @Test
    fun nullNameIsUnknown() {
        assertEquals("unknown (3)", AppVersion.label(null, 3))
    }
}
