package com.abrah.nightmare

import com.abrah.nightmare.coloring.ColoringPrototype
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColoringPrototypeTest {
    @Test fun buildsSingleImageGraphWithoutChangingRecipe() {
        val flow = ColoringPrototype.workflow("content://test/image/1", 0.4f)
        assertEquals(1, flow.graph.nodes.count { it.type == "core.image" })
        assertEquals("content://test/image/1", flow.graph.byId.getValue("image").params["uri"])
        assertEquals("0.4", flow.graph.byId.getValue("generate").params["denoise"])
        assertEquals(ColoringPrototype.PROMPT, flow.graph.byId.getValue("prompt").params["prompt"])
        assertTrue(flow.graph.byId.getValue("output").inputs.containsKey("media"))
    }

    @Test fun rejectsInvalidInputs() {
        for (input in listOf("", "relative/path", "file:///tmp/test.png")) {
            try { ColoringPrototype.workflow(input); throw AssertionError("Accepted $input") }
            catch (_: IllegalArgumentException) { }
        }
        try { ColoringPrototype.workflow("/tmp/image.png", 1.2f); throw AssertionError("Accepted denoise") }
        catch (_: IllegalArgumentException) { }
    }
}
