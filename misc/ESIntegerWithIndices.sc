/*

Integer with indices

*/

ESIntegerWithIndices {
  var <>integer, <>indices, <>metadata;

  storeArgs { ^[integer, indices, metadata] }
  asString { ^"an " ++ this.asCompileString }

  *new { |integer, indices, metadata|
    metadata = metadata ? ();
    ^super.newCopyArgs(integer, indices, metadata)
  }

  asESIntegerWithIndices {
    ^ESIntegerWithIndices(integer, indices, metadata);
  }

  == { |other|
    ^(integer == other.integer) and: (indices == other.indices)
  }
}