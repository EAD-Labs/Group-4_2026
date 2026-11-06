package com.aksharvel.tactile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private typealias V = PronunciationScorer.Verdict

/**
 * Expected values produced by the Python reference implementation and checked
 * against this Kotlin with a real compiler, so the two agree exactly.
 *
 * Lives in app/src/test/java/com/aksharvel/tactile/.
 * Run with:  gradlew testDebugUnitTest
 */
class PronunciationScorerTest {

    private fun check(
        target: String, heard: String,
        heardLen: Int, targetLen: Int, distance: Int,
        match: Double, verdict: PronunciationScorer.Verdict,
    ) {
        val r = PronunciationScorer.score(heard, target)
        val tag = "'$target' heard as '$heard'"
        assertEquals("$tag heardLen", heardLen, r.heardLen)
        assertEquals("$tag targetLen", targetLen, r.targetLen)
        assertEquals("$tag distance", distance, r.distance)
        assertEquals("$tag match", match, r.match, 0.05)
        assertEquals("$tag verdict", verdict, r.verdict)
    }

    // ---------------------------------------------------------------- letters

    @Test
    fun devanagariLettersAreCountedAsReadersCountThem() {
        // java.text.BreakIterator gets these wrong: it breaks at the virama,
        // giving 4 for नमस्ते and 2 for क्ष. Devanagari.clusters must not.
        assertEquals(3, Devanagari.clusters("नमस्ते").size)
        assertEquals(4, Devanagari.clusters("धन्यवाद").size)
        assertEquals(4, Devanagari.clusters("सुप्रभात").size)
        assertEquals(1, Devanagari.clusters("क्ष").size)
        assertEquals(1, Devanagari.clusters("त्र").size)
        assertEquals(1, Devanagari.clusters("ज्ञ").size)
        assertEquals(1, Devanagari.clusters("अं").size)
        assertEquals(8, Devanagari.clusters("मी शिकतो आहे").size)

        assertEquals(listOf("न", "म", "स्ते"), Devanagari.clusters("नमस्ते"))
        assertEquals(listOf("सु", "प्र", "भा", "त"), Devanagari.clusters("सुप्रभात"))
    }

    @Test
    fun everyVarnamalaLetterIsExactlyOneCluster() {
        // The whole app is built on single letters, so this must hold for all.
        for (letter in VARNAMALA) {
            assertEquals(
                "$letter should be one letter",
                1, Devanagari.clusters(letter).size
            )
        }
    }

    // ---------------------------------------------------------------- scoring

    @Test
    fun singleLetterExactMatch() {
        check("क", "क", 1, 1, 0, 100.0, V.CORRECT)
        check("क्ष", "क्ष", 1, 1, 0, 100.0, V.CORRECT)
        check("ज्ञ", "ज्ञ", 1, 1, 0, 100.0, V.CORRECT)
    }

    @Test
    fun singleLetterWrongIsRetryNotClose() {
        // One letter out of one wrong is 0%, below the relaxed 40% close mark.
        check("ण", "न", 1, 1, 1, 0.0, V.RETRY)   // retroflex ण vs dental न
        check("ळ", "ल", 1, 1, 1, 0.0, V.RETRY)
        check("श", "ष", 1, 1, 1, 0.0, V.RETRY)
        check("अ", "आ", 1, 1, 1, 0.0, V.RETRY)
    }

    @Test
    fun conjunctHeardAsItsPartsIsAMiss() {
        check("क्ष", "क्श", 1, 1, 1, 0.0, V.RETRY)
        check("त्र", "तर", 2, 1, 2, 0.0, V.RETRY)
        check("ज्ञ", "ग्य", 1, 1, 1, 0.0, V.RETRY)
    }

    @Test
    fun wordExactMatches() {
        check("नमस्ते", "नमस्ते", 3, 3, 0, 100.0, V.CORRECT)
        check("कृपया", "कृपया", 3, 3, 0, 100.0, V.CORRECT)
        check("मी शिकतो आहे", "मी शिकतो आहे", 8, 8, 0, 100.0, V.CORRECT)
        check("अक्षरवेल", "अक्षरवेल", 5, 5, 0, 100.0, V.CORRECT)
    }

    @Test
    fun wordNearMisses() {
        check("नमस्ते", "नमसते", 4, 3, 2, 50.0, V.CLOSE)
        check("धन्यवाद", "धन्यवाध", 4, 4, 1, 75.0, V.CLOSE)
        check("सुप्रभात", "सुपरभात", 5, 4, 2, 60.0, V.CLOSE)
        check("पाणी", "पानी", 2, 2, 1, 50.0, V.CLOSE)
        check("शाळा", "शाला", 2, 2, 1, 50.0, V.CLOSE)
        check("अक्षरवेल", "अकशरवेल", 6, 5, 2, 66.7, V.CLOSE)
    }

    @Test
    fun wordFailures() {
        check("नमस्ते", "धन्यवाद", 4, 3, 4, 0.0, V.RETRY)
        check("मी शिकतो आहे", "मी आहे", 4, 8, 4, 50.0, V.RETRY)
    }

    @Test
    fun silenceIsNotMarkedWrong() {
        val r = PronunciationScorer.score("", "क")
        assertEquals(PronunciationScorer.Verdict.NO_SPEECH, r.verdict)
        assertEquals(0.0, r.match, 0.001)
        // NO_SPEECH must stay distinguishable from RETRY: a quiet child and a
        // child who said the wrong letter need different handling.
        assertTrue(r.verdict != PronunciationScorer.Verdict.RETRY)
    }

    // ---------------------------------------------------------------- hygiene

    @Test
    fun recogniserPaddingAndDandaAreNotMistakes() {
        assertEquals(100.0,
            PronunciationScorer.score("  सुप्रभात  ", "सुप्रभात").match, 0.001)
        assertEquals(100.0,
            PronunciationScorer.score("नमस्ते।", "नमस्ते").match, 0.001)
    }

    @Test
    fun shortTargetsUseRelaxedMarks() {
        assertEquals(65, PronunciationScorer.score("क", "क").passMark)
        assertEquals(65, PronunciationScorer.score("नमस्ते", "नमस्ते").passMark)
        assertEquals(80, PronunciationScorer.score("धन्यवाद", "धन्यवाद").passMark)
    }

    @Test
    fun oneSlipInAThreeLetterWordStillPasses() {
        // 2 of 3 letters right is 66.7%, over the relaxed 65 mark.
        val r = PronunciationScorer.score("नमसे", "नमस्ते")
        assertEquals(66.7, r.match, 0.05)
        assertEquals(PronunciationScorer.Verdict.CORRECT, r.verdict)
    }

    @Test
    fun emptyTargetIsRejected() {
        try {
            PronunciationScorer.score("क", "")
            throw AssertionError("expected an exception for an empty target")
        } catch (e: IllegalArgumentException) {
            // expected
        }
    }
}
