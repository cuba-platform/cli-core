/*
 * Copyright (c) 2008-2018 Haulmont.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.haulmont.cli.core

import com.haulmont.cli.core.commands.CliCommand
import org.kodein.di.direct
import org.kodein.di.generic.instance
import java.net.URI
import java.nio.file.*
import kotlin.properties.ReadOnlyProperty
import kotlin.reflect.KProperty

class Resources(val cliPlugin: CliPlugin) {

    val resourcesBasePath: String = cliPlugin.resources.let {
        (it as? HasResources)?.resourcesBasePath
                ?: throw RuntimeException("Plugin ${cliPlugin.javaClass} doesn't support resources")
    }

    companion object {
        fun fromMyPlugin(): ReadOnlyProperty<CliCommand, Resources> = object : ReadOnlyProperty<CliCommand, Resources> {
            override fun getValue(thisRef: CliCommand, property: KProperty<*>): Resources {
                val context = kodein.direct.instance<CliContext>()

                val plugin = context.plugins.find {
                    it.javaClass.module == thisRef.javaClass.module
                }!!

                return Resources(plugin)
            }
        }

        fun getResourcePath(resourceName: String, clazz: Class<Any>): Path? {
            if (jrtFileSystem != null) {
                val moduleName = clazz.module.name
                if (moduleName != null) { // check for tests
                    val jrtPath = jrtFileSystem.getPath("/modules", moduleName, resourceName)
                    if (Files.exists(jrtPath)) {
                        return jrtPath
                    }
                }
            }

            return clazz.getResource(resourceName)
                    ?.toURI()
                    ?.let {
                        if (it.scheme == "jar") {
                            val fileSystem = getFileSystem(it)
                            fileSystem.getPath(resourceName)
                        } else {
                            Paths.get(it)
                        }
                    }
        }

        private fun getFileSystem(templateUri: URI?) = try {
            FileSystems.getFileSystem(templateUri)
        } catch (e: FileSystemNotFoundException) {
            FileSystems.newFileSystem(templateUri, mutableMapOf<String, Any>())
        }

        private val jrtFileSystem: FileSystem? = try {
            FileSystems.getFileSystem(URI.create("jrt:/"))
        } catch (e: Exception) {
            null
        }
    }
}