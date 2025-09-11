

//            ESThingSession
//         holds an array of thing players
//       with a little sugar for composition


ESThingSession {
  var <>tps, <>group, <>groups, <>fadeGroups, <>fadeBuses, <>amps, <>synths;

  *new { |tps = #[]|
    ^super.newCopyArgs(tps, nil, [], [], [], [], []);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  // sugar: put a thing space directly
  // interacts directly with ESPhasor -- this won't work if you don't use this sugar
  put { |index, ts|
    Server.default.waitForBoot {
      // make a group to play the session in
      if (group.isNil) { group = Group(Server.default) };
      // set ESPhasor space id
      ESPhasor.spaceId = index * 100;
      // make sure arrays are big enough
      if (tps.size <= index) {
        tps = tps.extend(index + 1);
        while { groups.size <= index } {
          groups = groups.add(Group(group, \addToTail));
        };
        fadeGroups = fadeGroups.extend(index + 1);
        fadeBuses = fadeBuses.extend(index + 1);
        amps = amps.extend(index + 1, 1);
        while { synths.size <= index } {
          synths = synths.add([]);
        };
      };
      // make sure there's a tp
      if (tps[index].isNil) {
        // make a new player with default winBounds by index
        tps[index] = ESThingPlayer().play(true).winBounds_(Rect(500 * index, 80, 800, 800));
        tps[index].presets.makeWindow(Rect(500 * index, 910, 800, 330));
      };
      // and play or stop accordingly
      if (ts.isNil) {
        tps[index].stop;
        tps[index].free;
        //tps[index].presets.w.close;
        tps[index] = nil;
      } {
        // after all this, if ts not nil put it where it goes
        ts = ESThingSpace.newFrom(ts, tps[index].ts).index_(index).session_(this).target_(groups[index]);
        tps[index].ts = ts;
        {
          var fadeGroup, fadeBus;
          fadeGroups[index].free;
          fadeGroup = Group(groups[index], \addToTail);
          fadeGroups[index] = group;
          fadeBuses[index].free;
          fadeBus = Bus.audio(Server.default, ts.outChannels);
          fadeBuses[index] = fadeBus;
          synths[index] = ts.outbus.numChannels.collect { |i|
            Synth(\ESThingPatch,
              [in: ts.outbus.index + i, out: fadeBus.index + i, amp: amps[index]],
              fadeGroup)
          };
        }.();
      };
    };
  }
}