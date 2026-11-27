package org.octopusden.octopus.vcsfacade.client

import feign.RequestTemplate
import feign.codec.Encoder
import feign.form.FormData
import java.lang.reflect.Type

/**
 * Turns the `content` byte array of a multipart call into a file part named `content.zip` of type
 * `application/zip`, which is what a standard multipart client (and `curl -F content=@x.zip`) sends.
 * feign-form would otherwise write a byte array without a filename, which a server reads as a plain field.
 */
class ZipFilePartEncoder(
    private val delegate: Encoder,
) : Encoder {
    override fun encode(
        obj: Any?,
        bodyType: Type?,
        template: RequestTemplate?,
    ) {
        val content = (obj as? Map<*, *>)?.get(CONTENT_PART)
        if (content is ByteArray) {
            val parts = LinkedHashMap<Any?, Any?>(obj as Map<*, *>)
            parts[CONTENT_PART] = FormData(ZIP_CONTENT_TYPE, ZIP_FILE_NAME, content)
            delegate.encode(parts, bodyType, template)
        } else {
            delegate.encode(obj, bodyType, template)
        }
    }

    companion object {
        const val CONTENT_PART = "content"
        const val ZIP_FILE_NAME = "content.zip"
        const val ZIP_CONTENT_TYPE = "application/zip"
    }
}
