DocumentationProvider : LSPProvider {
  classvar registry;

  *initClass {
    // currently support only `Class` doc.
    registry = Dictionary.new(1);
  }

  *methodNames { ^[ "internal/documentation" ] }
  *clientCapabilityName { ^nil }
  *serverCapabilityName { ^nil }
  options { ^() }
  init { |clientCapabilities| }

  *registerProvider { |objectType, provider| 
    registry.add(objectType.name -> provider);
  } 

  *hasProvider { |key| 
    ^registry.at(key.name).notNil
  }

  *getDocumentation { |class| 
    ^registry[Class.name].(class)
   } 
}