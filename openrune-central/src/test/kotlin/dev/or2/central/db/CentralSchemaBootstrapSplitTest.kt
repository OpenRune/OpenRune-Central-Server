package dev.or2.central.db

import dev.or2.central.splitPostgresStatements
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test

class CentralSchemaBootstrapSplitTest {
    @Test
    fun splitKeepsDollarQuotedFunctionAsOneStatement() {
        val sql =
            """
            CREATE TABLE a (id int);
            CREATE OR REPLACE FUNCTION f() RETURNS int AS $$
            BEGIN
              RETURN 1;
            END;
            $$ LANGUAGE plpgsql;
            SELECT 1;
            """.trimIndent()
        val parts = splitPostgresStatements(sql)
        assertEquals(3, parts.size)
        assertTrue(parts[0].contains("CREATE TABLE a"))
        assertTrue(parts[1].contains("CREATE OR REPLACE FUNCTION f()"))
        assertTrue(parts[1].contains("RETURN 1"))
        assertEquals("SELECT 1", parts[2].trim())
    }
}
