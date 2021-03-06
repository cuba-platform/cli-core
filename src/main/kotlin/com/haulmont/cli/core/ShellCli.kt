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

import com.beust.jcommander.MissingCommandException
import com.beust.jcommander.ParameterException
import com.google.common.eventbus.EventBus
import com.haulmont.cli.core.commands.*
import com.haulmont.cli.core.event.AfterCommandExecutionEvent
import com.haulmont.cli.core.event.BeforeCommandExecutionEvent
import com.haulmont.cli.core.event.ErrorEvent
import org.jline.builtins.Completers
import org.jline.builtins.Completers.TreeCompleter.Node
import org.jline.builtins.Completers.TreeCompleter.node
import org.jline.reader.Completer
import org.jline.reader.EndOfFileException
import org.jline.reader.LineReader
import org.jline.reader.UserInterruptException
import org.jline.reader.impl.LineReaderImpl
import org.kodein.di.generic.instance
import java.io.PrintWriter
import java.nio.charset.StandardCharsets
import java.util.*

class ShellCli(private val commandsRegistry: CommandsRegistry) : Cli {

    private val commandParser: CommandParser

    private val cliContext: CliContext by kodein.instance()

    private val writer: PrintWriter by kodein.instance<PrintWriter>()

    private val messages by localMessages()

    private val printHelper: PrintHelper by kodein.instance()

    private val bus: EventBus by kodein.instance()

    private val applicationProperties by lazy {
        val properties = Properties()

        val propertiesInputStream = Cli::class.java.getResourceAsStream("application.properties")
        propertiesInputStream.use {
            val inputStreamReader = java.io.InputStreamReader(propertiesInputStream, StandardCharsets.UTF_8)
            properties.load(inputStreamReader)
        }

        properties
    }

    private val workingDirectoryManager: WorkingDirectoryManager by kodein.instance()

    init {
        commandsRegistry {
            command("help", HelpCommand)
            command("stacktrace", Stacktrace)
            command("version", VersionCommand)
            command("exit", ExitCommand)
            command("cd", CdCommand()) {
                completer(DirectoriesCompleter(workingDirectoryManager.workingDirectory))
            }
            command("parameters", ShowNonInteractiveParameters(commandsRegistry))
        }

        commandParser = CommandParser(commandsRegistry, shellMode = true)
    }

    private val lineReader: LineReader by kodein.instance(arg = createCommandsCompleter(commandsRegistry))

    override fun run() {

        while (true) {
            CommonParameters.reset()
            commandParser.reset()

            val command = try {

                (lineReader as? LineReaderImpl)?.completer = createCommandsCompleter(commandsRegistry)

                val line = lineReader.readLine(buildPrompt()).also {
                    it != null || return
                }.takeIf {
                    it.isNotBlank()
                } ?: continue

                val parsedLine = lineReader.parser.parse(line, 0)
                val args = parsedLine.words().toTypedArray()
                commandParser.parseCommand(args)
            } catch (e: UserInterruptException) {
                return
            } catch (e: MissingCommandException) {
                printHelper.unrecognizedCommand()
                continue
            } catch (e: ParameterException) {
                printHelper.unrecognizedParameters(e)
                continue
            } catch (e: EndOfFileException) {
                return
            }

            if (CommonParameters.help) {
                commandParser.printHelp(command)
                continue
            }

            when (command) {
                is HelpCommand -> commandParser.printHelp()
                is Stacktrace -> printHelper.printLastStacktrace()
                is ExitCommand -> return
                else -> evalCommand(command)
            }
        }
    }

    private fun buildPrompt(): String = try {
        cliContext.prompt()
    } catch (e: Exception) {
        PROMPT
    }

    private fun evalCommand(command: CliCommand) {
        bus.post(BeforeCommandExecutionEvent(command))
        try {
            command.execute()
        } catch (e: EndOfFileException) {
        } catch (e: UserInterruptException) {
        } catch (e: Exception) {
            printHelper.handleCommandException(e)
            bus.post(ErrorEvent(e))
        }
        bus.post(AfterCommandExecutionEvent(command))
        cliContext.clearModels()
    }

    companion object {
        private const val PROMPT: String = ">"
    }
}

private fun createCommandsCompleter(commandsRegistry: CommandsRegistry): Completers.TreeCompleter {
    val rootBuilders = mutableListOf<NodeBuilder>()
    val stack = mutableListOf<NodeBuilder>()

    commandsRegistry.traverse(object : CommandVisitor {
        override fun enterCommand(command: CommandRecord) {
            val builder = NodeBuilder(command.name, command.completer)
            if (stack.isEmpty()) {
                rootBuilders += builder
            } else {
                stack.last().builders += builder
            }
            stack += builder
        }

        override fun exitCommand() {
            stack.removeAt(stack.lastIndex)
        }
    })

    return Completers.TreeCompleter(*rootBuilders.buildNodes())
}

private class NodeBuilder(val name: String, val completer: Completer?) {
    val builders: MutableList<NodeBuilder> = mutableListOf()

    fun build(): Node = when {
        builders.isEmpty() -> if (completer == null) {
            node(name)
        } else {
            node(name, node(completer))
        }
        else -> if (completer == null) {
            node(name, *builders.buildNodes())
        } else {
            node(name, *builders.buildNodes(), node(completer))
        }
    }
}

private fun MutableList<NodeBuilder>.buildNodes() = map { it.build() }.toTypedArray()