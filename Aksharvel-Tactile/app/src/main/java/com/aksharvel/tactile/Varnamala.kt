package com.aksharvel.tactile

/** Complete Marathi Varnamala: swar, swar-aadi (anusvara/visarga), and vyanjan. */
val SWAR = listOf("अ", "आ", "इ", "ई", "उ", "ऊ", "ऋ", "ए", "ऐ", "ओ", "औ", "अं", "अः")

val VYANJAN = listOf(
    "क", "ख", "ग", "घ", "ङ",
    "च", "छ", "ज", "झ", "ञ",
    "ट", "ठ", "ड", "ढ", "ण",
    "त", "थ", "द", "ध", "न",
    "प", "फ", "ब", "भ", "म",
    "य", "र", "ल", "व", "श",
    "ष", "स", "ह", "ळ", "क्ष", "त्र", "ज्ञ"
)

val VARNAMALA: List<String> = SWAR + VYANJAN

/**
 * Optional: if the phone's voice pronounces a letter badly, put a better-sounding
 * spelling here, e.g.  "ज्ञ" to "द्न्य".  Letters not listed are spoken as-is.
 */
val SPOKEN_OVERRIDES: Map<String, String> = emptyMap()

/** Letters per "भाग" (part) on the learning path. */
const val PART_SIZE = 4
