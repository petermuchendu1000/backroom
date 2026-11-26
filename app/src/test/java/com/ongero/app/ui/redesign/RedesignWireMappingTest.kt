package com.ongero.app.ui.redesign

import com.ongero.app.ui.redesign.screens.Topic as RedesignTopic
import com.ongero.app.ui.redesign.screens.Weight
import com.ongero.app.ui.screens.sharer.Tone
import com.ongero.app.ui.screens.sharer.Topic as OriginalTopic
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The redesign softens the wording on the "What's going on?" screen, but listener
 * boundaries and server-side matching are keyed on the ORIGINAL strings. Display copy
 * must therefore be free to change while the wire values stay fixed.
 *
 * Caught a real defect: the redesign labels "Letting out" and "A lot" are not the wire
 * values "Letting Out" and "Very Heavy". Sent as-is they would not have errored — they
 * would have quietly failed to match a listener's boundaries.
 */
class RedesignWireMappingTest {

    @Test
    fun everyRedesignTopicSendsAStringTheOriginalFlowAlreadySends() {
        val known = OriginalTopic.entries.map { it.title }.toSet()
        RedesignTopic.entries.forEach { t ->
            assertTrue(
                "Topic.${t.name} sends \"${t.wire}\", which is not one of $known",
                t.wire in known,
            )
        }
    }

    @Test
    fun everyIntensitySendsAToneTheOriginalFlowAlreadySends() {
        val known = Tone.entries.map { it.title }.toSet()
        Weight.entries.forEach { w ->
            assertTrue(
                "Weight.${w.name} sends \"${w.wire}\", which is not one of $known",
                w.wire in known,
            )
        }
    }

    @Test
    fun theDefaultTopicFallbackIsAlsoARealTopic() {
        // RedesignSharerFlow sends "Just Talking" when no topic is chosen.
        assertTrue(
            "the no-topic fallback must be a topic the server already accepts",
            "Just Talking" in OriginalTopic.entries.map { it.title }.toSet(),
        )
    }
}
