

//            ESThingSession
//         holds an array of thing players
//       with a little sugar for composition


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
  // interacts directly with ESPhasor -- this won't work if you don't use this sugar
  put { |index, ts|
    ESPhasor.spaceId = index * 100;
    if (tps.size <= index) {
      tps = tps.extend(index + 1);
    };
    if (tps[index].isNil) {
      // make a new player with default winBounds by index
      tps[index] = ESThingPlayer().play(true).winBounds_(Rect(500 * index, 80, 800, 800));
      tps[index].presets.makeWindow(Rect(500 * index, 910, 800, 330));
    };
    if (ts.isNil) {
      tps[index].stop;
      tps[index].free;
      //tps[index].presets.w.close;
      tps[index] = nil;
    } {
      // after all this, if ts not nil put it where it goes
      tps[index].ts = ESThingSpace.newFrom(ts, tps[index].ts).index_(index).session_(this);
    };
  }
}