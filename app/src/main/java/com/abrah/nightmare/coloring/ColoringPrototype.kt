package com.abrah.nightmare.coloring

import com.abrah.nightmare.canvas.Workflow
import com.abrah.nightmare.canvas.img2imgWorkflow

/** Single-image technical spike. Uses Nightmare's existing image-to-image recipe. */
object ColoringPrototype {
    const val PROMPT = "Convert the supplied image into a printable coloring book page. Preserve the main subject, pose and composition. Clean black outlines, enclosed colorable regions, pure white background, no shading, no text, no watermark."
    const val NEGATIVE = "color, gray shading, gradients, shadows, crosshatching, photographic texture, watermark, text, solid black areas"

    /** Build a fresh graph for exactly one image, without mutating the user's canvas. */
    fun workflow(source: String, denoise: Float = 0.55f): Workflow {
        require(source.isNotBlank()) { "An image URI or absolute path is required" }
        require(source.startsWith("content://") || source.startsWith("/")) {
            "Expected a content URI or absolute file path"
        }
        require(denoise.isFinite() && denoise in 0f..1f) { "Denoise must be between 0 and 1" }
        val base = img2imgWorkflow()
        val graph = base.graph
            .withParams("prompt", mapOf("prompt" to PROMPT, "negative" to NEGATIVE))
            .withParam("image", "uri", source)
            .withParam("generate", "denoise", denoise.toString())
        return base.copy(graph = graph)
    }
}
