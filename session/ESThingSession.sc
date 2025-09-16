

//            ESThingSession
//         holds an array of thing players
//       with a little sugar for composition


ESThingSession {
  var <>tps, <>group, <>groups, <>fadeGroups, <>fadeBuses, <amps, <>synths, <routingSynths, <ioSynths;
  var <>routing, <>inputRouting, <>outputRouting;
  var <>inChannels, <>outChannels, <>inbus, <>outbus;
  var <patchDescs, <inputDescs, <outputDescs;
  var <>inputGroup, <>topFadeGroup, <>outputGroup;

  *new { |tps = #[], inChannels, outChannels|
    inChannels = inChannels ?? Server.default.options.numInputBusChannels;
    outChannels = outChannels ?? Server.default.options.numOutputBusChannels;
    ^super.newCopyArgs(tps, nil, [], [], [], [], [], [], [],
      [], nil, nil, inChannels, outChannels);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  initBuses {
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
  }
  freeBuses {
    [inbus, outbus].do(_.free)
  }

  clear {
    tps.size.do { |i|
      if (tps[i].notNil) {
        tps[i].stop;
        tps[i].free;
        tps[i] = nil;
      };
    };
    inputRouting = nil;
    outputRouting = nil;
    this.route([])
  }

  setAmp { |index, amp|
    amps[index] = amp;
    synths[index].do(_.set(\amp, amp));
    this.changed(\amps, index);
  }

  channels { |arr|
    if (arr.isInteger) {
      inChannels = outChannels = arr;
    } {
      inChannels = arr[0];
      outChannels = arr[1];
    };
    if (inbus.notNil) {
      this.freeBuses;
      this.initBuses;
      this.route;
    };
  }

  input { |arr|
    inputRouting = arr;
    if (inbus.notNil) {
      this.route;
    };
  }

  output { |arr|
    outputRouting = arr;
    if (inbus.notNil) {
      this.route;
    };
  }

  route { |arr|
    if (inbus.isNil) {
      routing = arr;
      this.changed(\routing);
      ^this
    } {
      var sessionOutChannels = outChannels;//Server.default.options.numOutputBusChannels;
      var sessionInChannels = inChannels;//Server.default.options.numInputBusChannels;
      var sessionOutBus = outbus.index;//0;
      var sessionInBus = inbus.index;//Server.default.options.numOutputBusChannels;

      var normals = [];
      var ioRouting;

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
            ("Nothing in slot " ++ fromIndex).warn;
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
            ("Nothing in slot " ++ toIndex).warn;
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

      if (arr.isNil) { arr = routing };

      routing = arr;

      routingSynths.do(_.free);
      ioSynths.do(_.free);

      routingSynths = [];
      ioSynths = [];

      patchDescs = [];
      inputDescs = [];
      outputDescs = [];

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

      // ----- I/O ROUTING ------
      ioRouting = inputRouting ?? [-1, -1];

      ioRouting.pairsDo { |from, to|
        var amp = 1;
        if (to.isKindOf(Association)) {
          amp = to.value;
          to = to.key;
        };
        if (from == -1) {
          from = (0..Server.default.options.numInputBusChannels - 1);
        };
        if (to == -1) {
          var size = min(inChannels, from.asArray.size);
          to = (0..size - 1);
        };
        from = from.asArray;
        to = to.asArray;
        to.do { |toI, i|
          var fromI = from.wrapAt(i);
          ioSynths = ioSynths.add(
            Synth(\ESThingPatch,
              [in: Server.default.options.numOutputBusChannels + fromI, out: sessionInBus + toI, amp: amp],
              inputGroup, \addToTail)
          );
          inputDescs = inputDescs.add([fromI, toI, amp]);
        }
      };

      ioRouting = outputRouting ?? [-1, -1];

      ioRouting.pairsDo { |from, to|
        var amp = 1;
        if (to.isKindOf(Association)) {
          amp = to.value;
          to = to.key;
        };
        if (from == -1) {
          from = (0..outChannels - 1);
        };
        if (to == -1) {
          var size = min(Server.default.options.numOutputBusChannels, from.asArray.size);
          to = (0..size - 1);
        };
        from = from.asArray;
        to = to.asArray;
        to.do { |toI, i|
          var fromI = from.wrapAt(i);
          ioSynths = ioSynths.add(
            Synth(\ESThingPatch,
              [in: sessionOutBus + fromI, out: toI, amp: amp],
              outputGroup, \addToTail)
          );
          outputDescs = outputDescs.add([fromI, toI, amp]);
        }
      };

      this.changed(\routing);
    }
  }

  // sugar: put a thing space directly
  // interacts directly with ESPhasor -- this won't work if you don't use this sugar
  put { |index, ts|
    Server.default.waitForBoot {
      if (inbus.isNil) {
        this.initBuses;
      };
      // make a group to play the session in
      if (group.isNil) { group = Group(Server.default) };
      if (topFadeGroup.isNil) { topFadeGroup = Group(group) };
      if (inputGroup.isNil) { inputGroup = Group(topFadeGroup, \addBefore) };
      if (outputGroup.isNil) { outputGroup = Group(group, \addToTail) };
      // set ESPhasor space id
      ESPhasor.spaceId = index * 100;
      // make sure arrays are big enough
      if (tps.size <= index) {
        tps = tps.extend(index + 1);
        while { groups.size <= index } {
          groups = groups.add(Group(outputGroup, \addBefore));
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
          // to avoid failure in server msgs
          routingSynths.do(_.free);
          routingSynths = [];
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
      this.route;
    };
  }
}