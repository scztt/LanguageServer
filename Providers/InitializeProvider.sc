// https://microsoft.github.io/language-server-protocol/specifications/specification-current/#initialize
InitializeProvider : LSPProvider {
    classvar <>suggestedServerPort=57110;
    classvar <>initializeActions, <>startupFiles;
    
    var <initializationOptions, initializeParams;
    
    *methodNames { 
        ^["initialize"] 
    }
    *clientCapabilityName { ^nil }
    *serverCapabilityName { ^nil }
    
    *onInitialize {
        |func|
        initializeActions = initializeActions.add(func);
    }
    
    *prDoOnInitialize {
        |options|
        
        thisProcess.platform.startup;
        StartUp.run;
        
        initializeActions.do {
            |func|
            protect {
                func.value(options)
            }
        }
    }
    
    init {
    }
    
    options {
        // https://microsoft.github.io/language-server-protocol/specifications/specification-3-17/#clientCapabilities
        ^(
            // @TODO Fetch these from LSPCompletionHandler
            triggerCharacters: [".", "(", "~"],
            
            // @TODO These are overridden by commit chars for each completion - do we need?
            allCommitCharacters: [],
            
            resolveProvider: false,
            completionItem: (
                labelDetailsSupport: true
            )
        )
    }
    
    onReceived {
        |method, params|
        var serverCapabilities, startupPaths;
        
        initializeParams = params;
        initializationOptions = initializeParams["initializationOptions"] ?? {()};
        
        initializeParams["workspaceFolders"] !? {
            |folders|
            folders.do {
                |folder|
                server.workspaceFolders.add(folder["uri"].copy.replace("file://", "").urlDecode)
            };
        } ?? {
            initializeParams["rootUri"] ?? initializeParams["rootPath"] !? {
                |root|
                server.workspaceFolders.add(root.copy.replace("file://", "").urlDecode)
            };
        };
        
        Log('LanguageServer.quark').error("suggestedServerPortRange: %", initializationOptions["suggestedServerPortRange"]);
        initializationOptions["suggestedServerPortRange"] !? {
            |range|
            range = [range[0].asInteger, range[1].asInteger];
            this.class.suggestedServerPort = range[0];
            "Using default server port: % (allocated range: %-%)".format(range[0], range[0], range[1]-1).postln;
            Server.all.do {
                |s|
                s.addr.port = this.class.suggestedServerPort.asInteger;
            }
        };
        
        initializationOptions["useGlobalStartupFile"] !? {
            |bool|
            if (bool == "true") {
                startupPaths = startupPaths.add(thisProcess.platform.userConfigDir);
            }
        };
        
        initializationOptions["useWorkspaceStartupFile"] !? {
            |bool|
            if (bool == "true") {
                startupPaths = startupPaths.addAll(server.workspaceFolders);
            }
        };
        
        this.class.startupFiles = startupPaths.collect { |p| p +/+ "startup.scd" };
        
        serverCapabilities = ();
        this.addProviders(initializeParams["capabilities"], serverCapabilities);
        Log('LanguageServer.quark').info("Server capabilities are: %", serverCapabilities);
        
        { this.class.prDoOnInitialize(initializationOptions) }.defer(0.0000001);
        
        ^(
            "serverInfo": server.serverInfo,
            "capabilities": serverCapabilities;
        );
    }
    
    addProviders {
        |clientCapabilities, serverCapabilities, pathRoot=([])|
        var allProviders = LSPFeature.all;
        
        Log('LanguageServer.quark').info("Found providers: %", allProviders.collect(_.methodNames).join(", "));
        
        allProviders.do {
            |providerClass|
            var provider, clientCapability;
            
            // If clientCapabilityName.isNil, assume we ALWAYS use this provider
            clientCapability = providerClass.clientCapabilityName !? {
                this.getClientCapability(clientCapabilities, providerClass.clientCapabilityName)
            } ?? { () };
            
            clientCapability !? {
                |capability|
                Log('LanguageServer.quark').info("Registering provider: %", providerClass.methodNames);
                
                provider = providerClass.new(server, capability);
                
                providerClass.serverCapabilityName !? {
                    |capabilityName|
                    this.addServerCapability(
                        serverCapabilities,
                        capabilityName,
                        provider.options
                    )
                };	
                
                server.addProvider(provider);
            }
        }
    }
    
    getClientCapability {
        |clientCapabilities, path|
        Log('LanguageServer.quark').info("Checking for client capability at % (clientCapabilities: %)", path, clientCapabilities);
        
        if (path.isNil) { ^() };
        
        path.split($.).do {
            |key|
            if (clientCapabilities.isNil or: { clientCapabilities.isKindOf(Dictionary).not }) {
                ^nil
            } {
                clientCapabilities = clientCapabilities[key]
            }
        };
        
        ^clientCapabilities
    }
    
    addServerCapability {
        |serverCapabilities, path, options|
        Log('LanguageServer.quark').info("Adding server capability at %: %", path, options);
        
        if (path.isNil) { ^this };
        
        if (options.notNil) {
            path = path.split($.).collect(_.asSymbol);
            
            if (path.size > 1) {
                path[0..(path.size-2)].do {
                    |key|
                    Log('LanguageServer.quark').info("looking up key %", key);
                    serverCapabilities[key] = serverCapabilities[key] ?? { () };
                    serverCapabilities = serverCapabilities[key];
                };
            };
            
            Log('LanguageServer.quark').info("writing options into key %", path.last);
            serverCapabilities[path.last] = options;
        }
    }
}

+Main {
    startup {
        var didWarnOverwrite = false;
        
        // setup the platform first so that class initializers can call platform methods.
        // create the platform, then intialize it so that initPlatform can call methods
        // that depend on thisProcess.platform methods.
        platform = this.platformClass.new;
        platform.initPlatform;
        
        super.startup;
        
        // set the 's' interpreter variable to the default server.
        interpreter.s = Server.default;
        
        // 'langPort' may fail if no UDP port was available
        // this wreaks several manners of havoc, so, inform the user
        // also allow the rest of init to proceed
        try {
            openPorts = Set[NetAddr.langPort];
        } { |error|
            openPorts = Set.new;  // don't crash elsewhere
            "\n\nWARNING: An error occurred related to network initialization.".postln;
            "The error is '%'.\n".postf(error.errorString);
            "There may be an error message earlier in the sclang startup log.".postln;
            "Please look backward in the post window and report the error on the mailing list or user forum.".postln;
            "You may be able to resolve the problem by killing 'sclang%' processes in your system's task manager.\n\n"
                .postf(if(this.platform.name == \windows) { ".exe" } { "" });
        };
        
        Main.overwriteMsg.split(Char.nl).drop(-1).collect(_.split(Char.tab)).do {|x|
            if(x[2].beginsWith(Platform.classLibraryDir) and: {x[1].contains(""+/+"SystemOverwrites"+/+"").not}
            ) {
                warn("Extension in '%' overwrites % in main class library.".format(x[1],x[0]));
                didWarnOverwrite = true;
            }
        };
        if(didWarnOverwrite) {
            postln("Intentional overwrites must be put in a 'SystemOverwrites' subfolder.")
        };
        
        ("\n\n*** Welcome to SuperCollider %. ***".format(Main.version)
            + (Platform.ideName.switch(
                "scvim", {"For help type :SChelp."},
                "scel",  {"For help type C-c C-y."},
                "sced",  {"For help type ctrl-U."},
                "scapp", {"For help type cmd-d."},
                "scqt", {
                    if (Platform.hasQtWebEngine) {
                        "For help press %.".format(if(this.platform.name==\osx,"Cmd-D","Ctrl-D"))
                    } {
                        "For help visit http://doc.sccode.org" // Help browser is not available
                    }
            }) ?? {
                (
                    osx: "For help type cmd-d.",
                    linux: "For help type ctrl-c ctrl-h (Emacs) or :SChelp (vim) or ctrl-U (sced/gedit).",
                    windows: "For help press F1.",
                    iphone: ""
                ).at(platform.name);
                
            })
        ).postln;  
    }
}

+Platform {
    startupFiles {
        ^InitializeProvider.startupFiles
    }
}


