DocumentationProvider : LSPProvider {
  classvar registry;
  classvar <classDocs;

  *initClass {
    registry = Dictionary.new(); // currently support only `Class` doc.
    classDocs = Dictionary.new();
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