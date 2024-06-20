// https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textDocument_implementation
ExecuteCommandProvider : LSPProvider {
    *methodNames {
        ^[
            "workspace/executeCommand",
        ]
    }
    *clientCapabilityName { ^"workspace.executeCommand" }
    *serverCapabilityName { ^"executeCommandProvider" }
    
    init {
        |clientCapabilities|
    }
    
    options {
        ^(
            commands: this.class.commands.keys.asArray
        )
    }
    
    *commands {
        ^(
            'supercollider.internal.bootServer': {
                Server.default.boot;
            },
            'supercollider.internal.rebootServer': {
                Server.default.reboot;
            },
            'supercollider.internal.killAllServers': {
                Server.killAll();
            },
            'supercollider.internal.showServerWindow': {
                Server.default.makeWindow()
            },
            'supercollider.internal.showServerMeter': {
                Server.default.meter()
            },
            'supercollider.internal.showScope': {
                Server.default.scope()
            },
            'supercollider.internal.showFreqscope': {
                Server.default.freqscope()
            },
            'supercollider.internal.dumpNodeTree': {
                Server.default.queryAllNodes()
            },
            'supercollider.internal.dumpNodeTreeWithControls': {
                Server.default.queryAllNodes(true)
            },
            'supercollider.internal.showNodeTree': {
                Server.default.plotTree()
            },
            'supercollider.internal.startRecording': {
                Server.default.record()
            },
            'supercollider.internal.pauseRecording': {
                Server.default.pauseRecording()
            },
            'supercollider.internal.stopRecording': {
                Server.default.stopRecording()
            },
            'supercollider.internal.cmdPeriod': {
                CmdPeriod.run();
            }
        )
    }
    
    onReceived {
        |method, params|
        var command, arguments;
        
        command = params["command"].asSymbol;
        arguments = params["arguments"];
        
        this.class.commands[command] !? {
            |func|
            func.value();
            ^nil
        } ?? {
            Exception("Command doesn't exist: %".format(command)).throw
        }
    }
}
