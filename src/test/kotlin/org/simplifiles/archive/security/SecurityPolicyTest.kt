package org.simplifiles.archive.security

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

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
}
