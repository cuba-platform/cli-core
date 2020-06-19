<h1> CLI Core
</h1>

<p>
<a href="http://www.apache.org/licenses/LICENSE-2.0"><img src="https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat" alt="license" title=""></a>
</p>

<p>
The CLI Core makes it easy to create a command line application that already works, right out of the box.
</p>
<h3>
<img src="https://github.com/cuba-platform/cuba-cli/blob/master/img/cuba-cli-shell.gif" align="center">
</h3>

### CLI Core features 

- User friendly command line interface.
- Commands autocomplition.
- Supports external plugins.

### CLI Core application development

CLI lib coordinates `com.haulmont.cli.core:cli-core:1.0.0`

Your application should have implementation for `MainCliPlugin` interface. And provide plugin implementation in `module-info.java`. 

For example: 
```asciidoc
provides MainCliPlugin with YourPlugin;
```
To customize your application you can override `MainCliPlugin` methods:

- `welcome()` - prints welcome message for interactive plugin mode
- `prompt` - cli prompt for interactive mode
- `priority` - main CLI plugin priority, plugin with highest priority will be used as main
- `pluginsDir` - plugins folder path 


For more details please check https://github.com/cuba-platform/cli-core-sample sample project.