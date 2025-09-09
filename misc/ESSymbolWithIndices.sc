/*

This just describes an abstract patch source / destination, to be unified into a patch description

metadata is a bit wishy washy,
it exists so a destination point can encode amp for the patch desc

*/

ESSymbolWithIndices {
  var <>symbol, <>indices, <>metadata;

  storeArgs { ^[symbol, indices, metadata] }
  asString { ^"an " ++ this.asCompileString }

  *new { |symbol, indices, metadata|
    metadata = metadata ? ();
    ^super.newCopyArgs(symbol, indices, metadata)
  }

  asESSymbolWithIndices {
    ^this;
  }
}