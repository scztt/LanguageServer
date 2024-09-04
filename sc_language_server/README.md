# sc-language-server

This python script is a stdio wrapper for the SuperCollider Language Server. Since the LSP Quark currently communicates
over UDP, this program exists to support LSP clients that support stdio, such as neovim.

See this github [issue](https://github.com/scztt/LanguageServer.quark/issues/9) for background info.

## Development

Create a new venv and install dependencies

    python -m venv .venv
    source .venv/bin/activate
    python -m pip install -r requirements.dev.txt

## User Installation

Download the Quark from within SuperCollider:

    Quarks.install("https://github.com/scztt/LanguageServer.quark");
    thisProcess.recompile;

After downloading/installing the LanguageServer Quark, locate the directory.

e.g. on MacOs this might be:

    ~/Library/Application\ Support/SuperCollider/downloaded-quarks/LanguageServer

Navigate to the sc_language_server directory within that:

    cd sc_language_server

Pip install to give your system the `sc-language-server` command.

    python -m pip install .

Once installed, the command will be available, but you will need to set this up to be executed by your editor.


## Command-line Arguments

The script accepts the following command-line arguments:

- `--sclang-path`: Path to the SuperCollider language executable (sclang).
  - Default: default value currently only provided for MacOS.

- `--config-path`: Path to the configuration file.
  - Default: default value currently only provided for MacOS.
  - Depending on how many Quarks your regular sclang config contains, it may be beneficial to point sc-language-server
    to a minimal sclang config which only loads LanguageServer (and its dependencies).

- `--send-port`: Port number for sending data.
  - Optional
  - If not set (along with an unset --receive-port), a free port will be found.

- `--receive-port`: Port number for receiving data.
  - Optional
  - If not set (along with an unset --send-port), a free port will be found.

- `--ide-name`: Name of the IDE.
  - NOTE: currently this must be set to 'vscode' (the default)

- `-v, --verbose`: Enable verbose output.

- `-l, --log-file`: Specify a log file to write output.
  - Optional

## Neovim LSP Configuration

An example neovim lsp configuration:

```lua
local configs = require('lspconfig.configs')

configs.supercollider = {
    default_config = {
        cmd = {
            "sc-language-server",
            "--log-file",
            "/tmp/sc_lsp_output.log",
        }
        filetypes = {'supercollider'},
        root_dir = function(fname)
            return "/"
        end,
        settings = {},
    },
}
```
