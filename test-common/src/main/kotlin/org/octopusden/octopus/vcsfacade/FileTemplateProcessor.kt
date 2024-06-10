package org.octopusden.octopus.vcsfacade

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader


/**
 * A class for processing template files by replacing variables with specified values.
 *
 * @property fileName The name of the template file in the resources folder.
 */
class FileTemplateProcessor(private val fileName: String) {

    /**
     * Reads the content of the file from the resources folder.
     *
     * @return The content of the file as a String.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    @Throws(IOException::class)
    private fun readFile(): String {
        val inputStream = javaClass.classLoader.getResourceAsStream(fileName)
            ?: throw IOException("File not found: $fileName")

        BufferedReader(InputStreamReader(inputStream)).use { reader ->
            return reader.readText()
        }
    }

    /**
     * Replaces variables in the content with specified values.
     *
     * @param content The content with variables to be replaced.
     * @param variables A map containing variable names and their corresponding replacement values.
     * @return The content with variables replaced by their corresponding values.
     */
    private fun bindVariables(content: String, variables: Map<String, String>): String {
        var boundContent = content
        variables.forEach { (key, value) ->
            boundContent = boundContent.replace("{{${key}}}", value)
        }
        return boundContent
    }

    /**
     * Processes the template by reading the file and replacing variables with specified values.
     *
     * @param variables A map containing variable names and their corresponding replacement values.
     * @return The processed template content.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    @Throws(IOException::class)
    fun processTemplate(variables: Map<String, String>): String {
        val content = readFile()
        return bindVariables(content, variables)
    }

    /**
     * Processes the template by reading the file.
     *
     * @return The processed template content.
     * @throws IOException If an I/O error occurs reading from the file or a malformed or unmappable byte sequence is read.
     */
    @Throws(IOException::class)
    fun processTemplate(): String {
        return processTemplate(emptyMap())
    }
}