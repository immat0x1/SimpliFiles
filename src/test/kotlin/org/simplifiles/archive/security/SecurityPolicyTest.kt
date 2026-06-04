package org.simplifiles.archive.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SecurityPolicyTest {
    @Test
    fun `strict policy uses safe archive defaults`() {
        val policy = SecurityPolicy.strict()

        assertEquals(DuplicatePolicy.ERROR, policy.duplicatePolicy)
        assertFalse(policy.allowSymlinks)
        assertFalse(policy.allowHardlinks)
        assertFalse(policy.allowAbsolutePaths)
        assertEquals(0, policy.maxNestedArchiveDepth)
    }

    @Test
    fun `builder can customize every policy option`() {
        val policy = SecurityPolicy.builder()
            .maxEntries(10)
            .maxTotalUncompressedSize(1_000)
            .maxSingleFileSize(100)
            .maxCompressionRatio(25.0)
            .maxNestedArchiveDepth(2)
            .allowSymlinks(true)
            .allowHardlinks(true)
            .allowAbsolutePaths(true)
            .duplicatePolicy(DuplicatePolicy.RENAME)
            .build()

        assertEquals(10, policy.maxEntries)
        assertEquals(1_000, policy.maxTotalUncompressedSize)
        assertEquals(100, policy.maxSingleFileSize)
        assertEquals(25.0, policy.maxCompressionRatio)
        assertEquals(2, policy.maxNestedArchiveDepth)
        assertTrue(policy.allowSymlinks)
        assertTrue(policy.allowHardlinks)
        assertTrue(policy.allowAbsolutePaths)
        assertEquals(DuplicatePolicy.RENAME, policy.duplicatePolicy)
    }

    @Test
    fun `policy toBuilder preserves current values`() {
        val original = SecurityPolicy.strict().copy(
            maxEntries = 10,
            duplicatePolicy = DuplicatePolicy.KEEP_LAST,
        )

        val updated = original.toBuilder()
            .maxEntries(20)
            .build()

        assertEquals(20, updated.maxEntries)
        assertEquals(DuplicatePolicy.KEEP_LAST, updated.duplicatePolicy)
    }

    @Test
    fun `policy rejects invalid limits`() {
        assertFailsWith<IllegalArgumentException> {
            SecurityPolicy(maxEntries = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SecurityPolicy(maxTotalUncompressedSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SecurityPolicy(maxSingleFileSize = 0)
        }
        assertFailsWith<IllegalArgumentException> {
            SecurityPolicy(maxCompressionRatio = 0.0)
        }
        assertFailsWith<IllegalArgumentException> {
            SecurityPolicy(maxNestedArchiveDepth = -1)
        }
    }
}
