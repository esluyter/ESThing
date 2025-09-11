

//            ESThingSession
//         holds an array of thing players
//       with a little sugar for composition


ESThingSession {
  var <>tps, <>group, <>groups, <>fadeGroups, <>fadeBuses, <>amps, <>synths, <routingSynths;
  var <>routing, <>inputRouting, <>outputRouting;
  var <>topFadeGroup;

  *new { |tps = #[]|
    ^super.newCopyArgs(tps, nil, [], [], [], [], [], [], [], [], []);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  route { |arr|
    routing = arr;
    routingSynths.do(_.free);

    arr.pairsDo { |from, to|

      from = from.asESIntegerWithIndices;

      to.asArray.do { |item|
        var amp = 1;
        var pre = false;

        // get parameters from association
        // \in : 2[1..3]->0.75
        // \in : 2[1..3]->(amp: 0.75, pre: true)
        if (item.isKindOf(Association)) {
          if (item.value.isNumber) {
            amp = item.value;
          } {
            // value is event with possible amp and pre
            amp = amp ?? item.value[\amp];
            pre = pre ?? item.value[\pre];
          };
          item = item.key;
        };

        item = item.asESIntegerWithIndices;

        {
          var fromIndex = from.integer;
          var toIndex = item.integer;
          var outbus, fadeBus, fadeGroup;
          var inbus;

          if (fromIndex >= 0) {
            var fromTs = tps[fromIndex].ts;
            outbus = fromTs.outbus.index;
            fadeBus = fadeBuses[fromIndex].index;
            fadeGroup = fadeGroups[fromIndex];
            from.indices = from.indices ?? (0..fromTs.outChannels - 1);
          } {
            // this is the first input bus....v confusing re var names
            outbus = Server.default.options.numOutputBusChannels;
            fadeBus = outbus;
            fadeGroup = topFadeGroup;
            from.indices = from.indices ?? (0..Server.default.options.numInputBusChannels - 1);
          };

          if (toIndex >= 0) {
            var toTs = tps[toIndex].ts;
            inbus = toTs.inbus.index;
            item.indices = item.indices ?? (0..toTs.inChannels - 1);
          } {
            // this means it's going to output
            inbus = 0;
            item.indices = item.indices ?? (0..Server.default.options.numOutputBusChannels - 1);
          };

          item.indices.do { |i|
            var fromI = from.indices[i];
            if (fromI.notNil) {
              routingSynths = routingSynths.add(
                Synth(\ESThingPatch,
                  [in: if (pre) { outbus } { fadeBus } + i, out: inbus + i, amp: amp],
                  fadeGroup, \addToTail)
              );
            };
          };
        }.();
      };
    };
  }

  // sugar: put a thing space directly
  // interacts directly with ESPhasor -- this won't work if you don't use this sugar
  put { |index, ts|
    Server.default.waitForBoot {
      // make a group to play the session in
      if (group.isNil) { group = Group(Server.default) };
      if (topFadeGroup.isNil) { topFadeGroup = Group(group) };
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
          fadeGroups[index] = fadeGroup;
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

      // redo routing
      this.route(routing);
    };
  }
}