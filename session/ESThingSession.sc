

//            ESThingSession
//         holds an array of thing players
//       with a little sugar for composition


ESThingSession {
  var <>tps, <>group, <>groups, <>fadeGroups, <>fadeBuses, <amps, <>synths, <routingSynths;
  var <>routing, <>inputRouting, <>outputRouting;
  var <patchDescs;
  var <>topFadeGroup;

  *new { |tps = #[]|
    ^super.newCopyArgs(tps, nil, [], [], [], [], [], [],
      [], [], []);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  clear {
    tps.size.do { |i|
      if (tps[i].notNil) {
        tps[i].stop;
        tps[i].free;
        tps[i] = nil;
      };
    };
    this.route([])
  }

  setAmp { |index, amp|
    amps[index] = amp;
    synths[index].do(_.set(\amp, amp));
    this.changed(\amps, index);
  }

  route { |arr|
    var sessionOutChannels = Server.default.options.numOutputBusChannels;
    var sessionInChannels = Server.default.options.numInputBusChannels;
    var sessionOutBus = 0;
    var sessionInBus = Server.default.options.numOutputBusChannels;

    var normals = [];

    // complicated logic to patch:
    var patchMe = { |from, to, amp=1, pre=false, pruneNormals=true|
      var fromIndex, toIndex;
      var outbus, fadeBus, fadeGroup;
      var inbus;
      from = from.asESIntegerWithIndices;
      to = to.asESIntegerWithIndices;
      fromIndex = from.integer;
      toIndex = to.integer;

      if (fromIndex >= 0) {
        var fromTs;
        if (tps[fromIndex].isNil) {
          ("Nothing in slot " ++ fromIndex).error;
        } {
          fromTs = tps[fromIndex].ts;
          outbus = fromTs.outbus.index;
          fadeBus = fadeBuses[fromIndex].index;
          fadeGroup = fadeGroups[fromIndex];
          from.indices = from.indices ?? (0..fromTs.outChannels - 1);
        };
      } {
        outbus = sessionInBus;
        fadeBus = outbus;
        fadeGroup = topFadeGroup;
        from.indices = from.indices ?? (0..sessionInChannels - 1);
      };

      if (toIndex >= 0) {
        var toTs;
        if (tps[toIndex].isNil) {
          ("Nothing in slot " ++ toIndex).error;
        } {
          toTs = tps[toIndex].ts;
          inbus = toTs.inbus.index;


          if (from.notNil) {
            var size = min(toTs.inChannels, from.indices.size);
            to.indices = to.indices ?? (0..size - 1);
          } {
            to.indices = to.indices ?? (0..toTs.inChannels - 1);
          };
        };
      } {
        // this means it's going to output
        var size = min(sessionOutChannels, from.indices.size);
        inbus = sessionOutBus;
        to.indices = to.indices ?? (0..size - 1);
      };

      if (inbus.notNil and: outbus.notNil) {
        to.indices.do { |toI, i|
          var fromI = from.indices.wrapAt(i);
          if (fromI.notNil) {
            routingSynths = routingSynths.add(
              Synth(\ESThingPatch,
                [in: if (pre) { outbus } { fadeBus } + fromI, out: inbus + toI, amp: amp],
                fadeGroup, \addToTail)
            );
            patchDescs = patchDescs.add([fromIndex[fromI], toIndex[toI], amp, pre]);
            if (pruneNormals) {
              removeNormals.(toIndex, toI, fromIndex, fromI);
            };
          };
        };
      };
    };
    var removeNormals = { |toIndex, toI, fromIndex, fromI|
      var toRemove = [];
      normals.do { |n, j|
        if (toIndex.notNil) {
          if (n == [-1[toI], toIndex[toI]]) { toRemove = toRemove.add(j) };
        };
        if (fromIndex.notNil) {
          if (n == [fromIndex[fromI], -1[fromI]]) { toRemove = toRemove.add(j) };
        };
      };
      toRemove.reverse.do { |j|
        normals.removeAt(j);
      };
    };

    routing = arr;
    routingSynths.do(_.free);
    routingSynths = [];
    patchDescs = [];

    // build normalled connections
    tps.select(_.notNil).do { |tp|
      var ts = tp.ts;
      var numInputs = min(ts.inChannels, sessionInChannels);
      var numOutputs = min(ts.outChannels, sessionOutChannels);
      var inputs = numInputs.collect { |i| [-1[i], ts.index[i]] };
      var outputs = numOutputs.collect { |i| [ts.index[i], -1[i]] };
      normals = normals ++ inputs ++ outputs;
    };

    // break them using the array
    arr.pairsDo { |from, to|
      if (to.isNil) { to = [nil] };
      if (from == 'nil') { from = nil };
      // treat every to as an array
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

        if (from.isNil) {
          // remove normal patch
          to = to.asESIntegerWithIndices;
          if (to.indices.isNil) {
            to.indices = to.indices ?? (0..tps[to.integer].ts.inChannels - 1);
          };
          to.indices.do { |toI|
            removeNormals.(to.integer, toI);
          };
        } {
          if (item.isNil) {
            // remove normal patch
            from = from.asESIntegerWithIndices;
            if (from.indices.isNil) {
              from.indices = from.indices ?? (0..tps[from.integer].ts.outChannels - 1);
            };
            from.indices.do { |fromI|
              removeNormals.(nil, nil, from.integer, fromI);
            };
          } {
            // execute the patches for this to item
            patchMe.(from, item, amp, pre);
          };
        };
      };
    };

    normals.do { |n|
      // patch without pruning normals
      patchMe.(n[0], n[1], 1, false, false);
    };

    this.changed(\routing);
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
        tps[index] = ESThingPlayer(session: this, index: index).play(true).winBounds_(Rect(500 * index, 80, 800, 800));
        //tps[index].presets.makeWindow(Rect(500 * index, 910, 800, 330));
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