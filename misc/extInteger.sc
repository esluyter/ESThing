+Integer {
  at { |...channels|
    ^ESIntegerWithIndices(this, channels)
  }

  copySeries { |first, second, last|
    var channels = (first, second .. last);
    ^ESIntegerWithIndices(this, channels)
  }

  asESIntegerWithIndices {
    ^ESIntegerWithIndices(this, nil);
  }
}