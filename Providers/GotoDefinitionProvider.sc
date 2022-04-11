// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_definition
GotoDefinitionProvider : LSPProvider {
	*methodNames {
		^[
			"textDocument/definition",
		]
	}
	*clientCapabilityName { ^"textDocument.definition" }
	*serverCapabilityName { ^"definitionProvider" }

	init {
		|clientCapabilities|
	}

	options {
		^()
	}

	handleRequest {
		|method, params|
		var doc = LSPDocument.findByQUuid(params["textDocument"]["uid"]);
		var wordAtCursor = LSPDatabase.getDocumentWordAt(
			doc,
			params["position"]["line"].asInteger,
			params["position"]["character"].asInteger
		);

		Log(GotoDefinitionProvider).info("Found word at cursor: %", wordAtCursor);

		^(wordAtCursor !? { this.getDefinitionsForWord(wordAtCursor) })
	}

	getDefinitionsForWord {
		|word|
		^LSPDatabase.findDefinitions(word.asSymbol)
	}
}
