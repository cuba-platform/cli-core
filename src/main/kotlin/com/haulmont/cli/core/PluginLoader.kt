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

import com.google.common.eventbus.EventBus
import com.haulmont.cli.core.commands.CommandsRegistry
import com.haulmont.cli.core.event.InitPluginEvent
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.lang.module.ModuleFinder
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

internal class PluginLoader {

    private val log: Logger = Logger.getLogger(PluginLoader::class.java.name)

    private val context: CliContext by kodein.instance<CliContext>()

    private val writer: PrintWriter by kodein.instance<PrintWriter>()

    private val bus: EventBus by kodein.instance<EventBus>()

    fun loadPlugins(commandsRegistry: CommandsRegistry, mode: CliMode) {
        log.log(Level.INFO, "Creating plugins module layer")

        context.mainPlugin()?.pluginsDir?.let { pluginsDir ->

            loadPluginsByDir(pluginsDir, mode)

            if (Files.exists(pluginsDir)) {
                Files.walk(pluginsDir, 1)
                        .filter { it != pluginsDir }
                        .filter { Files.isDirectory(it) }
                        .forEach { loadPluginsByDir(it, mode) }
            }
        }

        log.log(Level.INFO, "InitPluginEvent")
        bus.post(InitPluginEvent(commandsRegistry, mode))
    }

    private fun loadPluginsByDir(pluginsDir: Path, mode: CliMode) {

        val pluginsLayer = try {
            val bootLayer = ModuleLayer.boot()

            val pluginModulesFinder = ModuleFinder.of(pluginsDir)
            val pluginModules = pluginModulesFinder.findAll().map {
                it.descriptor().name()
            }

            val configuration = bootLayer.configuration().resolve(pluginModulesFinder, ModuleFinder.of(), pluginModules)

            ModuleLayer.defineModulesWithOneLoader(
                    configuration,
                    mutableListOf(bootLayer),
                    ClassLoader.getSystemClassLoader()
            ).layer()
        } catch (e: Exception) {
            log.log(Level.WARNING, "Error during loading module layer from directory $pluginsDir", e)
            writer.println("Error during loading module layer from directory $pluginsDir".bgRed())
            return
        }

        loadPlugins(pluginsLayer, mode)
    }

    private fun loadPlugins(pluginsLayer: ModuleLayer, mode: CliMode) {
        log.log(Level.INFO, "Start loading plugins")

        val pluginsIterator = ServiceLoader.load(pluginsLayer, CliPlugin::class.java).iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()
            loadPlugin(plugin, mode)
        }
    }

    fun loadMainPlugin(commandsRegistry: CommandsRegistry, mode: CliMode) {
        log.log(Level.INFO, "Start loading main plugins")

        val pluginsIterator = ServiceLoader.load(MainCliPlugin::class.java).iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()
            loadPlugin(plugin, mode)
        }
    }

    private fun loadPlugin(plugin: CliPlugin, mode: CliMode) {
        try {
            if (plugin.javaClass in context.plugins.map { it.javaClass })
                return

            val version = getPluginVersion(plugin)
            if (version != API_VERSION) {
                writer.println("Plugin's ${plugin.javaClass.name} version ($version) doesn't correspond current CUBA CLI version ($API_VERSION)".bgRed())
                return
            }
            context.registerPlugin(plugin)
            if (context.mainPlugin() != plugin && mode == CliMode.SHELL) {
                writer.println("Loaded plugin @|green ${plugin.javaClass.name}|@.")
            }
            bus.register(plugin)
        } catch (e: ServiceConfigurationError) {
            log.log(Level.SEVERE, e) { "Error loading plugin" }
            writer.println(e.message)
        }
    }

    private fun getPluginVersion(plugin: CliPlugin): Int {
        return try {
            plugin.apiVersion
        } catch (e: Throwable) {
            log.log(Level.WARNING, e) { "" }
            1
        }
    }
}
