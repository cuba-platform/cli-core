package com.haulmont.cli.core

import java.nio.file.Path

interface MainCliPlugin : CliPlugin {
    fun welcome()

    val prompt: String
        get() = ">"

    val priority: Int
        get() = 0

    val pluginsDir: Path?
        get() = null
}