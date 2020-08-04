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
import java.nio.file.Paths
import java.util.*
import java.util.logging.Level
import java.util.logging.Logger

class PluginLoader {

    private val log: Logger = Logger.getLogger(PluginLoader::class.java.name)

    private val context: CliContext by kodein.instance<CliContext>()

    private val writer: PrintWriter by kodein.instance<PrintWriter>()

    private val bus: EventBus by kodein.instance<EventBus>()

    fun loadPlugins(commandsRegistry: CommandsRegistry, mode: CliMode) {
        log.log(Level.INFO, "Creating plugins module layer")

        context.mainPlugin()?.systemPluginsDirs?.forEach {
            loadPluginsRecursively(it, mode, true)
        }

        context.mainPlugin()?.pluginsDir?.let { pluginsDir ->
            loadPluginsRecursively(pluginsDir, mode, false)
        }

        log.log(Level.INFO, "InitPluginEvent")
        bus.post(InitPluginEvent(commandsRegistry, mode))
    }

    private fun loadPluginsRecursively(pluginsDir: Path, mode: CliMode, system: Boolean = false) {
        walkDirectory(pluginsDir) {
            loadPluginsByDir(it, mode, system)
        }
    }

    private fun walkDirectory(rootDir: Path, action: (dir: Path) -> Unit) {
        if (Files.exists(rootDir)) {
            action(rootDir)
            Files.walk(rootDir, 1)
                    .filter { it != rootDir }
                    .filter { Files.isDirectory(it) }
                    .forEach { action(it) }
        }
    }

    private fun loadPluginsByDir(pluginsDir: Path, mode: CliMode, system: Boolean = false) {
        createModuleLayer(pluginsDir)?.let { loadPlugins(it, mode, system) }
    }

    private fun createModuleLayer(pluginsDir: Path): ModuleLayer? = try {
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
        null
    }

    private fun loadPlugins(pluginsLayer: ModuleLayer, mode: CliMode, system: Boolean = false) {
        log.log(Level.INFO, "Start loading plugins")

        val pluginsIterator = ServiceLoader.load(pluginsLayer, CliPlugin::class.java).iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()
            loadPlugin(plugin, mode, system)
        }
    }

    fun loadMainPlugin(commandsRegistry: CommandsRegistry, mode: CliMode) {
        log.log(Level.INFO, "Start loading main plugins")

        val pluginsIterator = ServiceLoader.load(MainCliPlugin::class.java).iterator()

        while (pluginsIterator.hasNext()) {
            val plugin = pluginsIterator.next()
            loadPlugin(plugin, mode, true)
        }
    }

    private fun loadPlugin(plugin: CliPlugin, mode: CliMode, system: Boolean = false) {
        try {
            if (plugin.javaClass in context.plugins.map { it.javaClass })
                return

            val version = getPluginVersion(plugin)
            if (version != API_VERSION) {
                writer.println("Plugin's ${plugin.javaClass.name} version ($version) doesn't correspond current CUBA CLI version ($API_VERSION)".bgRed())
                return
            }
            context.registerPlugin(plugin)
            if (context.mainPlugin() != plugin && mode == CliMode.SHELL && !system) {
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
