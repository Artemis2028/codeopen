package app.gridfix.android.coords

/**
 * NATO phonetic alphabet (ICAO/ACP-125) with military digit pronunciation
 * (Tree, Fower, Fife, Niner). Used to spell grids for radio transmission and
 * to feed the voice callout.
 */
object Phonetic {

    val letters: Map<Char, String> = mapOf(
        'A' to "Alfa", 'B' to "Bravo", 'C' to "Charlie", 'D' to "Delta",
        'E' to "Echo", 'F' to "Foxtrot", 'G' to "Golf", 'H' to "Hotel",
        'I' to "India", 'J' to "Juliett", 'K' to "Kilo", 'L' to "Lima",
        'M' to "Mike", 'N' to "November", 'O' to "Oscar", 'P' to "Papa",
        'Q' to "Quebec", 'R' to "Romeo", 'S' to "Sierra", 'T' to "Tango",
        'U' to "Uniform", 'V' to "Victor", 'W' to "Whiskey", 'X' to "Xray",
        'Y' to "Yankee", 'Z' to "Zulu",
    )

    val digits: Map<Char, String> = mapOf(
        '0' to "Zero", '1' to "One", '2' to "Two", '3' to "Tree",
        '4' to "Fower", '5' to "Fife", '6' to "Six", '7' to "Seven",
        '8' to "Eight", '9' to "Niner",
    )

    private fun word(c: Char): String? =
        letters[c.uppercaseChar()] ?: digits[c]

    /** Spell one contiguous group ("VP" -> "Victor Papa"). */
    fun spellGroup(group: String): String =
        group.mapNotNull { word(it) }.joinToString(" ")

    /**
     * Spell a full MGRS string for display:
     * "18T VP 38089 79755" -> "One Eight Tango · Victor Papa · Tree Eight Zero Eight Niner · Seven Niner Seven Fife Fife"
     */
    fun mgrs(full: String): String =
        full.trim().split(Regex("\\s+"))
            .map { spellGroup(it) }
            .filter { it.isNotBlank() }
            .joinToString(" · ")

    /** Same content with comma pauses, for text-to-speech. */
    fun mgrsSpeech(full: String): String =
        full.trim().split(Regex("\\s+"))
            .map { spellGroup(it) }
            .filter { it.isNotBlank() }
            .joinToString(", ")
}
