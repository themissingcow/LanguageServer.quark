// Base class for all LSP features
// At initialization time:
//  1. Each subclass will be created IF the capability object provided by the client
//     has non-null data at the field specified in `clientCapabilityName`. This data
//     (usually a dictionary-like object) is passed to the LSPProvider:init function.
//     Search LSP docs for "Client Capability" / "Capabilities" for details of the
//     configuration struct that will be provided by a client.
//
//  2. A created LSPProvider should return it's options from the `options` method.
//     This should match the data structure described as `Server Capability` in the
//     LSP documentation. `LSPProvider:serverCapabilityName` should match the server
//     capability name specified in the docs - usually something of the form:
//     `xxxxxxxxxProvider`.
//
// When a provider is registered:
//  1. Any request received by the LSPConnection matching a method specified by
//     an LSPProviders's `methodNames` will be forwardeded to `LSPProvider:onReceived`.
//     The request `params` field will be passed as the second argument.
//  2. Any value returned by `onReceived` will be passed back to the LSP client.
//     nil values indicate no response. MAKE SURE TO RESPOND WITH THE EXPECTED/CORRECT
//     DATA STRUCTURE (search for ""Response:"" in the LSP documentation).
//  3. Any responses that are expensive to calculate should be calculated in a thread with
//     `wait` calls in it, to give other scheduled sclang routines a change to execute.
//     If results are not immediately available, a Deferred object can be returned, where
//     the delayed result data can be set later using `deferred.value = result`.

LSPFeature {
    var server;
    
    //////////////////////////////////////////////////////////////////////////////////////////////////
    // SUBCLASS INTERFACE
    
    // Array of LSP method names to respond to, e.g.:["textDocument/didSave", "textDocument/didClose"]
    *methodNames { this.subclassResponsibility(thisMethod) }
        
        // Path to read in client capability data structure.
        *clientCapabilityName { this.subclassResponsibility(thisMethod) }
        
        // Path to respond with server capability options. Can be nil.
        *serverCapabilityName { this.subclassResponsibility(thisMethod) }
        
        // Server capability response
        options { ^() }
        
        init { |clientCapabilities| this.subclassResponsibility(thisMethod) }
        
        
        //////////////////////////////////////////////////////////////////////////////////////////////////
        // IMPLEMENTATION
        
        methodNames { ^this.class.methodNames }
        clientCapabilityName {^ this.class.clientCapabilityName }
        serverCapabilityName { ^this.class.serverCapabilityName }
        
        *all {
            var providerClasses, dictionary;
            
            providerClasses = this.allSubclasses.collect(_.allSubclasses).flatten;
            providerClasses = providerClasses.reject(_ == DynamicProvider).reject(_.isNil);
            providerClasses = providerClasses.addAll(DynamicProvider.all);
            
            ^providerClasses
        }
        
        *new {
            |server, clientCapabilities|
            ^super.newCopyArgs(server).init(clientCapabilities)
        }
}

// LSPProvider describes a feature that responds to requests from a client
LSPProvider : LSPFeature {
    // Callback for any methods specified by `methodNames`
    onReceived { |method, params| this.subclassResponsibility(thisMethod) }
}

// LSPProvider describes a feature that responds to requests from a client
LSPRequest : LSPFeature {
    // Send a request for any method in `methodNames`
    sendRequest { 
        |params| 		// @TODO What about multiple method names?
        ^server.prHandleRequest(this.methodNames[0], params)
    }
}

LSPNotification : LSPFeature {
    sendNotification {
        |params|
        ^server.prSendMessage((
            method: this.methodNames[0],
            params: params
        ))
    }
}
