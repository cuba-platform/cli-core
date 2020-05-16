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

package com.haulmont.cuba.cli.core.commands

/**
 * Base interface for all cli commands.
 *
 * To create your own command do as following:
 *
 * First of all, create new command class and implement this interface.
 * Most probably, you need extend [com.haulmont.cuba.cli.core.commands.GeneratorCommand].
 * Than, register your command with your plugin class, by subscribing to [com.haulmont.cuba.cli.core.event.InitPluginEvent]
 * After that, open commands package in your module-info.java.
 *
 * You can add parameters to command that will be input after command name.
 * To add them simply create corresponding field and mark them with [com.beust.jcommander.Parameter] annotation.
 * You can get more information about command parameters in [JCommander documentation](http://jcommander.org/).
 * To add command description, mark it with [com.beust.jcommander.Parameters] annotation and
 * specify [com.beust.jcommander.Parameters.commandDescription] value.
 */
interface CliCommand {

    @Throws(CommandExecutionException::class)
    fun execute()
}