package org.octopusden.octopus.vcsfacade

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FileTemplateProcessorTest {

    @Test
    fun testProcessTemplate() {
        val fileName = "testFile.txt"
        val expectedContent = "Hello, Will!"

        val processor = FileTemplateProcessor(fileName)
        val result = processor.processTemplate(mapOf("name" to "Will"))

        assertEquals(expectedContent, result)
    }

}