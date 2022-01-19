/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain.tools

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.VisibleForTesting
import org.rust.cargo.toolchain.RsToolchainBase
import org.rust.openapiext.RsDeserializationException
import org.rust.openapiext.RsProcessExecutionOrDeserializationException
import org.rust.openapiext.execute
import org.rust.stdext.RsResult
import org.rust.stdext.RsResult.Err
import org.rust.stdext.RsResult.Ok
import org.rust.stdext.unwrapOrElse
import java.nio.file.Path

fun Cargo.config(owner: Project, projectDirectory: Path?): CargoConfig =
    CargoConfig(this.toolchain, owner, projectDirectory)

class CargoConfig(
    toolchain: RsToolchainBase,
    private val owner: Project,
    private val projectDirectory: Path?
) : RsTool("cargo", toolchain) {
    /**
     * Execute `cargo config get <path>` to and parse output as Jackson Tree ([JsonNode]).
     * Use [JsonNode.at] to get properties by path (in `/foo/bar` format)
     */
    @VisibleForTesting
    fun getConfig(path: String): RsResult<JsonNode, RsProcessExecutionOrDeserializationException> {
        val parameters = mutableListOf("-Z", "unstable-options", "config", "get", path)

        val output = createBaseCommandLine(
            parameters,
            workingDirectory = projectDirectory,
            environment = mapOf(RsToolchainBase.RUSTC_BOOTSTRAP to "1")
        ).execute(owner).unwrapOrElse { return Err(it) }.stdout

        return try {
            Ok(TOML_MAPPER.readTree(output).at(path.toJsonPointer()))
        } catch (e: JacksonException) {
            Err(RsDeserializationException(e))
        }
    }

    fun getBuildTarget(): RsResult<String, RsProcessExecutionOrDeserializationException>
        = getConfig("build.target").map { it.asText() }

    fun getEnvParams(): RsResult<Map<String, EnvValue>, RsProcessExecutionOrDeserializationException> {
        val params = mutableMapOf<String, EnvValue>()

        val cfg = getConfig("env").unwrapOrElse { return Err(it) }
        for (field in cfg.fields()) {
            // Value can be either string or object with additional `forced` and `relative` params.
            // https://doc.rust-lang.org/cargo/reference/config.html#env
            if (field.value.isTextual) {
                params[field.key] = EnvValue(field.value.asText())
            } else if (field.value.isObject) {
                val valueParams = try {
                    TOML_MAPPER.treeToValue(field.value, EnvValue::class.java)
                } catch (e: JacksonException) {
                    return Err(RsDeserializationException(e))
                }
                params[field.key] = EnvValue(valueParams.value, valueParams.isForced, valueParams.isRelative)
            }
        }

        return Ok(params)
    }

    data class EnvValue(
        @JsonProperty("value")
        val value: String,
        @JsonProperty("forced")
        val isForced: Boolean = false,
        @JsonProperty("relative")
        val isRelative: Boolean = false
    )

    private fun String.toJsonPointer() = "/" + this.replace(".", "/")

    companion object {
        private val TOML_MAPPER = TomlMapper()
    }
}
