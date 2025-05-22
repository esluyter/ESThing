ESThingClient {
  var <session, <>tpIndex;
  var <presetIndices, <fades, <lastPresets;

  *new { |session, tpIndex = 0, presetIndices, fades, lastPresets|
    presetIndices = presetIndices ?? { session.tps.size.collect { 0 } };
    fades = fades ?? { session.tps.size.collect{ true } };
    lastPresets = lastPresets ?? { session.tps.size.collect { nil } };
    ^super.newCopyArgs(session, tpIndex, presetIndices, fades, lastPresets);
  }

  presetIndex { ^presetIndices[tpIndex] }
  presetIndex_ { |val| presetIndices[tpIndex] = val }
  fade { ^fades[tpIndex] }
  fade_ { |val| fades[tpIndex] = val }
  lastPreset { ^lastPresets[tpIndex] }
  lastPreset_ { |val| lastPresets[tpIndex] = val }

  tp { ^session[tpIndex] }
  ts { ^this.tp.ts }

  map { |string, width = 5, height = 5, n = 64|
    // x => knob
    // . => break
    // + => add space
    // - => skip knob
    // A => skip and store in "a"
    // a => use stored knob from earlier
    var verticalMap = string ?? { $x.dup(n).join };
    var map = nil.dup(n);
    {
      var x = 0, y = 0, i = 0;
      var symbols = ();
      var insertKnob = { |i|
        var bank;
        x = x + ((y / height).floor);
        y = y % height;
        bank = (x / height).floor;
        map[(bank * height * width) + (y*height) + (x%height)] = i;
        y = y + 1;
      };
      verticalMap.do { |c|
        switch(c)
        {$x} {
          insertKnob.(i);
          i = i + 1;
        }
        {$-} {
          i = i + 1;
        }
        {$+} {
          y = y + 1;
        }
        {$.} {
          x = x + 1;
          y = 0;
        }
        {
          if (c.isUpper) {
            symbols[c] = i;
            i = i + 1;
          };
          if (c.isLower) {
            insertKnob.(symbols[c.toUpper]);
          };
        };
      };
    }.value;
    ^map;
  }
}