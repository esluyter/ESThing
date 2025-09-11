+Symbol {
  at { |...channels|
    ^ESSymbolWithIndices(this, channels)
  }

  copySeries { |first, second, last|
    var channels = (first, second .. last);
    ^ESSymbolWithIndices(this, channels)
  }

  asESSymbolWithIndices {
    ^ESSymbolWithIndices(this, nil);
  }

  asESIntegerWithIndices {
    ^ESIntegerWithIndices(-1, nil);
  }
}

/*
\in[1, 3, 7].postcs

\in[1].postcs
\.asESSymbolWithIndices
\osc[\fb]

\[1, 3..22]
*/

