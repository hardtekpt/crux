package com.hardtekpt.crux.data.local

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * Every schema version since migrations began has its exported JSON committed, so the
 * migration tests can build each old version. A bump without its export fails here first.
 */
class SchemaExportTest {

    @Test
    fun `every schema since the first migratable one is exported`() {
        val dir = File("schemas/${CruxDatabase::class.java.name}")
        val exported = dir.listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }.sorted()
        assertEquals((FIRST_MIGRATABLE_VERSION..CruxDatabase.VERSION).toList(), exported)
    }

    private companion object {
        /** Auto-migrations start at schema 4; older installs predate any saved data worth keeping. */
        const val FIRST_MIGRATABLE_VERSION = 4
    }
}
