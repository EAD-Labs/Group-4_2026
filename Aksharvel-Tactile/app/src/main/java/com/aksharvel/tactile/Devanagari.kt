package com.aksharvel.tactile

/**
 * Splits Devanagari into the letters a reader would count.
 *
 * java.text.BreakIterator cannot be used: it breaks at the virama, so
 * नमस्ते comes out as 4 pieces instead of 3 and क्ष as 2 instead of 1.
 * This follows Unicode 15.1's Indic conjunct rule instead — a virama
 * (linker) binds the consonant before it to the consonant after it.
 */
object Devanagari {

    /** Marks that hang off the preceding base: matras, anusvara, nukta, accents, ZWJ/ZWNJ. */
    private fun isMark(c: Char): Boolean {
        val u = c.code
        return u in 0x0900..0x0903 ||     // candrabindu, anusvara, visarga
               u in 0x093A..0x093C ||     // vowel signs oe/ooe, nukta
               u in 0x093E..0x094C ||     // matras
               u in 0x094E..0x094F ||     // vowel signs prishthamatra/aw
               u in 0x0951..0x0957 ||     // stress/accent marks
               u in 0x0962..0x0963 ||     // vocalic l matras
               u == 0x200C || u == 0x200D // ZWNJ, ZWJ
    }

    /** The virama / halant: the conjunct linker. */
    private fun isLinker(c: Char): Boolean = c.code == 0x094D

    /** Consonants that a linker can join. */
    private fun isConsonant(c: Char): Boolean {
        val u = c.code
        return u in 0x0915..0x0939 ||     // क..ह
               u in 0x0958..0x095F ||     // nukta forms क़..य़
               u in 0x0978..0x097F        // additional consonants
    }

    fun clusters(text: String): List<String> {
        if (text.isEmpty()) return emptyList()
        val out = ArrayList<String>(text.length)
        var i = 0
        while (i < text.length) {
            val start = i
            i++                                   // the base character
            while (i < text.length) {
                // soak up marks attached to the current base
                while (i < text.length && isMark(text[i])) i++
                // a linker followed by a consonant continues the same letter
                if (i < text.length && isLinker(text[i])) {
                    var j = i + 1
                    while (j < text.length && isMark(text[j])) j++
                    if (j < text.length && isConsonant(text[j])) {
                        i = j + 1                 // absorb linker + next consonant
                        continue
                    }
                    i++                           // trailing virama, no consonant after
                    continue
                }
                break
            }
            out.add(text.substring(start, i))
        }
        return out
    }
}
