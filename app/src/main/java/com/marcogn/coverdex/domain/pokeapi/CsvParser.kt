package com.marcogn.coverdex.domain.pokeapi

/**
 * A small, header-aware CSV reader for PokéAPI's `data/v2/csv/` source files (see
 * docs/plan/reference-pokedata.md §2). Rows are returned as name -> value maps — **never
 * positional indexing** — because upstream has added columns to these files before and will
 * again; only the columns this app actually reads are ever looked up by name.
 *
 * Handles quoted fields (including embedded commas and escaped `""` quotes), CRLF and LF line
 * endings, and empty trailing fields. Does not handle a literal newline embedded inside a quoted
 * field — none of the 8 pinned files need that, they are plain identifier/numeric tables.
 */
object CsvParser {

    fun parse(text: String): List<Map<String, String>> {
        val lines = splitLines(text)
        if (lines.isEmpty()) return emptyList()
        val header = parseRow(lines[0])
        return lines.drop(1).map { line ->
            val fields = parseRow(line)
            header.indices.associate { i -> header[i] to fields.getOrElse(i) { "" } }
        }
    }

    private fun splitLines(text: String): List<String> =
        text.split("\r\n", "\r", "\n").filter { it.isNotEmpty() }

    private fun parseRow(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                inQuotes -> when {
                    c == '"' && i + 1 < line.length && line[i + 1] == '"' -> {
                        current.append('"')
                        i++
                    }
                    c == '"' -> inQuotes = false
                    else -> current.append(c)
                }
                c == '"' -> inQuotes = true
                c == ',' -> {
                    fields.add(current.toString())
                    current.setLength(0)
                }
                else -> current.append(c)
            }
            i++
        }
        fields.add(current.toString())
        return fields
    }
}
