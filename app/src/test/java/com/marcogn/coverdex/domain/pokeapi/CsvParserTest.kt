package com.marcogn.coverdex.domain.pokeapi

import org.junit.Assert.assertEquals
import org.junit.Test

class CsvParserTest {

    @Test
    fun `parses a simple header and rows by name, not position`() {
        val rows = CsvParser.parse("id,identifier\n1,bulbasaur\n2,ivysaur\n")

        assertEquals(2, rows.size)
        assertEquals("bulbasaur", rows[0]["identifier"])
        assertEquals("1", rows[0]["id"])
        assertEquals("ivysaur", rows[1]["identifier"])
    }

    @Test
    fun `handles a quoted field containing a comma`() {
        val rows = CsvParser.parse("id,name\n1,\"Mime, Jr.\"\n")

        assertEquals("Mime, Jr.", rows[0]["name"])
    }

    @Test
    fun `handles an escaped double-quote inside a quoted field`() {
        val rows = CsvParser.parse("id,name\n1,\"Farfetch\"\"d\"\n")

        assertEquals("Farfetch\"d", rows[0]["name"])
    }

    @Test
    fun `handles an empty trailing field`() {
        val rows = CsvParser.parse("id,identifier,note\n1,bulbasaur,\n")

        assertEquals("", rows[0]["note"])
    }

    @Test
    fun `an empty trailing field is present with an empty value, not absent from the map`() {
        // The distinction this protects: moves.csv's power column is always present as a header,
        // but an individual row's value can be empty (status move) meaning "no power", not "0".
        val rows = CsvParser.parse("id,power\n1,\n")

        assertEquals(true, rows[0].containsKey("power"))
        assertEquals("", rows[0]["power"])
    }

    @Test
    fun `handles CRLF line endings`() {
        val rows = CsvParser.parse("id,identifier\r\n1,bulbasaur\r\n2,ivysaur\r\n")

        assertEquals(2, rows.size)
        assertEquals("bulbasaur", rows[0]["identifier"])
        assertEquals("ivysaur", rows[1]["identifier"])
    }

    @Test
    fun `handles a mix of CRLF and LF line endings`() {
        val rows = CsvParser.parse("id,identifier\r\n1,bulbasaur\n2,ivysaur\r\n")

        assertEquals(2, rows.size)
    }

    @Test
    fun `an empty input produces no rows`() {
        assertEquals(emptyList<Map<String, String>>(), CsvParser.parse(""))
    }

    @Test
    fun `looks fields up by header name so column order never matters`() {
        val rows = CsvParser.parse("b,a\n2,1\n")

        assertEquals("1", rows[0]["a"])
        assertEquals("2", rows[0]["b"])
    }
}
