ESThingSession {
  var <>tps;

  *new { |tps = #[]|
    ^super.newCopyArgs(tps);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  // sugar: put a thing space directly
  put { |index, ts|
    if (tps.size <= index) {
      tps = tps.extend(index + 1);
    };
    if (tps[index].isNil) {
      // make a new player with default winBounds by index
      tps[index] = ESThingPlayer().play.winBounds_(Rect(500 * index, 80, 650, 800));
      tps[index].presets.makeWindow(Rect(500 * index, 910, 650, 330));
    };
    // sugar: convert array to ESThingSpace
    if (ts.isArray) {
      var argNames = ['things', 'patches', 'initFunc', 'playFunc', 'stopFunc', 'freeFunc', 'inChannels', 'outChannels', 'target', 'oldSpace'];
      var args = [[], [], nil, nil, nil, nil, 2, 2, nil, tps[index].ts];
      var i = 0;
      ts.do { |item|
        var argIndex = argNames.indexOf(item);
        if (argIndex.notNil) {
          i = argIndex;
        } {
          args[i] = item;
          i = i + 1;
        };
      };
      ts = ESThingSpace(*args);
    };
    if (ts.isNil) {
      tps[index].stop;
      tps[index].free;
      tps[index].presets.w.close;
      tps[index] = nil;
    } {
      // after all this, if ts not nil put it where it goes
      tps[index].ts = ts;
    };
  }
}