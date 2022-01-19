/*
 * Use of this source code is governed by the MIT license that can be
 * found in the LICENSE file.
 */

package org.rust.cargo.toolchain

import org.rust.FileTreeBuilder
import org.rust.cargo.RsWithToolchainTestBase
import org.rust.cargo.project.settings.toolchain
import org.rust.cargo.toolchain.tools.CargoConfig
import org.rust.fileTree

class CargoConfigTest : RsWithToolchainTestBase() {
    private val toolchain: RsToolchainBase
        get() = project.toolchain!!

    fun `test current project`() = checkPaths("/foo", listOf("one" to "42", "two" to """"hello"""")) {
        dir("foo") {
            dir(".cargo") {
                file("config.toml", """
                    one = 42
                    two = "hello"
                """)
                file("Cargo.toml")
            }
        }
    }

    fun `test path`() = checkPaths("/foo", listOf("one.two.three" to "42")) {
        dir("foo") {
            dir(".cargo") {
                file("config.toml", """
                    [one.two]
                    three = 42
                """)
                file("Cargo.toml")
            }
        }
    }

    fun `test inheritance`() = checkPaths("/foo/bar", listOf(
        "one" to "42", "two" to """"hello"""", "three" to """["1","2"]"""
    )) {
        dir("foo") {
            dir(".cargo") {
                file("config.toml", """
                    one = 0
                    two = "hello"
                """)
            }

            dir("bar") {
                dir(".cargo") {
                    file("config.toml", """
                        one = 42
                        three = ["1", "2"]
                    """)
                }
                file("Cargo.toml")
            }
        }
    }

    fun `test build target`() {
        val vFile = fileTree {
            dir("foo") {
                dir(".cargo") {
                    file("config.toml", """
                        [build]
                        target = "wasm32-unknown-unknown"
                    """)
                    file("Cargo.toml")
                }
            }
        }.create().file("/foo")

        val cargoConfig = CargoConfig(toolchain, project, vFile.toNioPath())
        val actual = cargoConfig.getBuildTarget().unwrap()

        assertEquals("wasm32-unknown-unknown", actual)
    }

    fun `test env`() {
        val vFile = fileTree {
            dir("foo") {
                dir(".cargo") {
                    file("config.toml", """
                        [env]
                        foo = "42"
                        bar = { value = "24", forced = true }
                        baz = { value = "hello/world", relative = true }
                    """)
                    file("Cargo.toml")
                }
            }
        }.create().file("/foo")

        val cargoConfig = CargoConfig(toolchain, project, vFile.toNioPath())
        val params = cargoConfig.getEnvParams().unwrap()

        assertEquals(CargoConfig.EnvValue("42"), params["foo"])
        assertEquals(CargoConfig.EnvValue("24", isForced = true), params["bar"])
        assertEquals(CargoConfig.EnvValue("hello/world", isRelative = true), params["baz"])
    }

    private fun checkPaths(
        path: String,
        expectedProperties: List<Pair<String, String>>,
        builder: FileTreeBuilder.() -> Unit
    ) {
        val cargoConfig = CargoConfig(toolchain, project, fileTree(builder).create().file(path).toNioPath())

        for (property in expectedProperties) {
            val key = property.first
            val expected = property.second

            val actual = cargoConfig.getConfig(key).unwrap().toString()

            assertEquals(
                "Expected $expected for $key key, but actual is $actual",
                expected,
                actual
            )
        }
    }
}
