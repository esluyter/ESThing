

//           ESThingSpace



ESThingSpace {
  var <>things, <>patches;
  var <>initFunc, <>playFunc, <>stopFunc, <>freeFunc, <inChannels, <outChannels, <>target;
  var <>inbus, <>outbus, <>group;
  var <>environment;

  storeArgs { ^[things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, target]}
  modPatches {
    ^patches.select { |patch| patch.to.index.isKindOf(Symbol) };
  }
  postModPatches {
    this.modPatches.do { |patch|
      // this doesn't work with backslash directly, so use question mark temp
      "(?% : ?%->?%, amp: %)".format(
        if (patch.fromThing.outChannels == 1) {
          patch.from.thingIndex
        } {
          "%->?%".format(patch.from.thingIndex, patch.from.index)
        },
        patch.to.thingIndex,
        patch.to.index,
        patch.amp
      ).tr($?, $\\).postln;
    };
  }
  *new { |things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels = 2, outChannels = 2, target, oldSpace|
    // syntactic sugar
    var hueList = [ 0.55, 0.3, 0.0, 0.8, 0.5, 0.1, 0.65, 0.35, 0.9, 0.2 ], hueIndex = 0;
    things = things.asArray.collect { |thing|
      thing = if (thing.isKindOf(ESThing)) {
        thing
      } {
        if (thing.isKindOf(Association)) {
          var name = thing.key;
          var value = thing.value;
          var ret;
          if (value.isKindOf(ESThingSpace)) {
            ret = ESThing.space(name, value);
          };
          if (value.isKindOf(Function)) {
            ret = ESThing.playFuncSynth(name, value);
          };
          if (value.isKindOf(Ref)) {
            ret = ESThing.droneSynth(name, value.dereference);
          };
          if (value.isKindOf(Dictionary)) {
            var thisKey = value.keys.select(_.isKindOf(Symbol).not).pop;
            var thisValue = value[thisKey];
            var inChannels = 2, outChannels = 2;
            var kind = \drone;
            if (thisValue.isKindOf(Symbol)) {
              kind = thisValue
            };
            if (thisValue.isKindOf(Association)) {
              kind = thisValue.key;
              thisValue = thisValue.value;
            };
            if (thisValue.isInteger) {
              inChannels = outChannels = thisValue
            };
            if (thisValue.isArray) {
              #inChannels, outChannels = thisValue
            };
            if (thisKey.isKindOf(ESThingSpace)) {
              ret = ESThing.space(name, thisKey, inChannels, outChannels, value[\top] ? 0, value[\left] ? 0, value[\width] ? 1);
            };
            if (thisKey.isKindOf(Function)) {
              ret = ESThing.playFuncSynth(name, thisKey, value[\params], inChannels, outChannels, value[\top] ? 0, value[\left] ? 0, value[\width] ? 1, value[\midiChannel], value[\srcID])
            };
            if (thisKey.isKindOf(Ref)) {
              var method = switch (kind)
              { \drone } { \droneSynth }
              { \mono } { \monoSynth }
              { \poly } { \polySynth }
              { \mpe } { \mpeSynth };
              ret = ESThing.perform(method, name, thisKey.dereference, value[\args], value[\params], inChannels, outChannels, value[\midicpsFunc], value[\velampFunc], value[\top] ? 0, value[\left] ? 0, value[\width] ? 1, value[\midiChannel], value[\srcID]);
            };
          };
          ret;
        } {
          // don't know what to do with this
          thing
        };
      };
      thing.hue = hueList[hueIndex];
      hueIndex = hueIndex + 1 % hueList.size;
      thing;
    };
    patches = patches.asArray.collect { |patch|
      var n;
      var fromInput = false, toOutput = false;
      var cs = patch.asCompileString;
      if (patch.isKindOf(Symbol)) {
        // patch a symbol directly to the output
        patch = (patch : -1);
      };
      if (patch.isKindOf(Dictionary)) {
        var amp = patch.removeAt(\amp) ?? 1;
        if (patch.size == 1) {
          var arr = patch.asKeyValuePairs;
          patch.from = arr[0];
          patch.to = arr[1];
        };
        if ((patch.from.class == Symbol) or: (patch.from.class == Integer)) {
          var fromThing = this.thingAt(patch.from, things);
          if (fromThing.isNil) {
            fromInput = true;
            fromThing = (outChannels: inChannels);
          };
          patch.from = patch.from->(fromThing.outChannels.collect{ |n| n });
        };
        if ((patch.to.class == Symbol) or: (patch.to.class == Integer)) {
          var toThing = this.thingAt(patch.to, things);
          if (toThing.isNil) {
            toOutput = true;
            toThing = (inChannels: outChannels);
          };
          patch.to = patch.to->(toThing.inChannels.collect{ |n| n });
          if (fromInput and: toOutput) {
            // post warning here
            "% -- (% : %)  -- neglected to patch input directly to output".format(cs, patch.from, patch.to).warn;
          };
        };
        // accept arrays of indices
        if (patch.from.value.isArray) {
          patch.from = patch.from.value.collect { |index| patch.from.key->index };
        } {
          patch.from = patch.from.asArray;
        };
        if (patch.to.value.isArray) {
          patch.to = patch.to.value.collect { |index| patch.to.key->index };
        } {
          patch.to = patch.to.asArray;
        };
        if (patch.from.value.isNil or: patch.to.value.isNil) {
          patch = []
        } {
          patch = max(patch.from.size, patch.to.size).collect { |i|
            // guard against patching ins to outs
            var from = patch.from.wrapAt(i);
            var to = patch.to.wrapAt(i);
            if (fromInput and: toOutput) {
              nil
            } {
              ESThingPatch(patch.name, from, to, amp ?? 1)
            }
          };
        };
      };
      patch
    } .flat.reject { |item| item.isNil };
    target = target ?? { Server.default };
    ^super.newCopyArgs(things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, target).prInit(oldSpace);
  }
  prInit { |oldSpace|
    environment = ();
    things.do(_.parentSpace_(this));
    patches.do(_.parentSpace_(this));
    inbus = Server.default.options.numOutputBusChannels.asBus('audio', inChannels, Server.default);
    outbus = 0.asBus('audio', outChannels, Server.default);

    // for all named things,
    // set all knob values to their previous (i.e. current) position
    // if oldSpace is provided
    if (oldSpace.notNil) {
      oldSpace.things.do { |oldThing|
        if (oldThing.name.notNil) {
          var thisThing = this.thingAt(oldThing.name);
          if (thisThing.notNil) {
            oldThing.params.do { |oldParam|
              var newParam = thisThing.paramAt(oldParam.name);
              if (newParam.notNil and: {newParam.val != oldParam.val}) {
                if (newParam.val.size == oldParam.val.size) {
                  newParam.val_(oldParam.val);
                };
              };
              if (newParam.isNil) {
                // to catch params created after thing is created
                // i.e. playFuncSynth
                thisThing.oldParams[oldParam.name] = oldParam;
              };
            };
          };
        };
      };
      // post the mod patches, in case we forgot to save them
      // TODO: simply restore values
      "Restoring mod amps not yet figured out... meanwhile:".postln;
      oldSpace.postModPatches;
    };
  }

  init {
    initFunc.value(this);
    things.do(_.init);
  }
  play {
    group = Group(target);
    forkIfNeeded {
      playFunc.value(this);
      Server.default.sync;
      things.reverse.do(_.play); // assumes they each add to head
      Server.default.sync;
      patches.do(_.play);
    }
  }
  stop {
    stopFunc.value(this);
    things.do(_.stop);
    patches.do(_.stop);
    group.free;
  }
  free {
    freeFunc.value(this);
    things.do(_.free);
  }

  at { |sym|
    // default to 0 if symbol not found
    // this is so it's ok to use thing[\buf] in a play func
    ^(this.thingAt(sym) ? environment.at(sym)) ? 0;
  }
  put { |key, val|
    environment.put(key, val);
    ^this;
  }

  *thingAt { |sym, things|
    things = things.asArray;
    if (sym.isInteger) {
      ^things[sym]
    };
    things.do { |thing|
      if (thing.name == sym) {
        ^thing
      };
    };
    ^nil;
  }
  thingAt { |sym|
    ^this.class.thingAt(sym, this.things);
  }
  params {
    ^things.collect(_.params).flat;
  }
  //syntactic sugar
  value { |sym| ^this.thingAt(sym) }

  asTarget {
    ^this.group
  }
}