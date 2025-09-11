

//           ESThingSpace
//       a container for many things



ESThingSpace {
  // these are arrays of ESThing and ESThingPatch
  var <>things, <>patches;
  // user functions, take argument { |space| }
  // environment variables here will propagate
  // e.g. space[\buf] = ... , child things can find at thing[\buf]
  var <>initFunc, <>freeFunc, <>playFunc, <>stopFunc,
  // i/o specs
  <inChannels, <outChannels,
  // where to add group
  <>target, <>addAction;

  // top level spaces get an index
  var <>index, <>session, <>parentThing;

  // buses are created on .init / destroyed on .free
  // group is created on .play / destroyed on .stop
  var <>inbus, <>outbus, <>group, <>soloGroup;
  var <>environment, <>soloThings;

  storeArgs { ^[things, patches,
    initFunc, freeFunc, playFunc, stopFunc,
    inChannels, outChannels, target, addAction]}

  *new { |things, patches,
    initFunc, playFunc, stopFunc, freeFunc,
    inChannels = 2, outChannels = 2, target, addAction = \addToHead,
    // oldSpace is for remembering parameter values when reevaluating
    oldSpace|

    // the cycle of colors for child things
    var hueList = [ 0.55, 0.3, 0.0, 0.8, 0.5, 0.1, 0.65, 0.35, 0.9, 0.2 ];
    var hueIndex = 0;

    // process array of possible things or associations
    // possibly interleaved with patch information
    // (See ESThingFactory for implementation)
    var newThings = [];
    var newPatches = [];
    var latestThing;
    var makeThing = { |thing|
      thing = ESThing.newFrom(thing);
      thing.hue = hueList[hueIndex];
      hueIndex = hueIndex + 1 % hueList.size;
      newThings = newThings.add(thing);
      latestThing = thing;
    };
    var patchThing = { |patch|
      var translatePatchPoint = { |patchPoint|
        if (patchPoint.indices.isNil) {
          patchPoint.symbol
        } {
          patchPoint.symbol->patchPoint.indices
        };
      };
      var indices = nil;
      var putPatch = { |patch|
        var patchFrom, patchTo, amp = 1; // each is symbol with indices
        patchFrom = if (latestThing.notNil) {
          latestThing.name.asESSymbolWithIndices
        } {
          ''.asESSymbolWithIndices;
        };
        patchFrom.indices = indices;
        if (patch.isKindOf(Association)) {
          patchTo = patch.key.asESSymbolWithIndices;
          amp = if (patch.value.isNumber) { patch.value } { patch.value.amp } ?? 1;
        } {
          patchTo = patch.asESSymbolWithIndices;
        };
        newPatches = newPatches.add((translatePatchPoint.(patchFrom) : translatePatchPoint.(patchTo), amp: amp));
      };
      patch.asArray.do { |item|
        if (item.isInteger or: item.isArray) {
          indices = item.asArray;
        } {
          putPatch.(item);
          indices = nil;
        };
      };
    };

    things.asArray.do { |item|
      if (item.isKindOf(Association)) {
        if ((item.key.isKindOf(Symbol) or: item.key.isKindOf(ESSymbolWithIndices) or: item.key.isInteger) and: (item.value.isKindOf(Dictionary) or: item.value.isNumber)) {
          // apply as patching to previous thing
          patchThing.(item);
        } {
          // make a thing
          makeThing.(item);
        };
      } {
        if (item.isKindOf(ESThing)) {
          // make a thing
          makeThing.(item);
        } {
          // apply as patching to previous thing
          patchThing.(item);
        };
      };
    };
    things = newThings;

    // process array of patches
    // (see ESThingFactory for implementation)
    patches = (patches.asArray ++ newPatches).collect { |patch|
      ESThingPatch.newFrom(patch, things, inChannels, outChannels)
    } .flat.reject { |item| item.isNil };

    // default to target the default server
    target = target ?? { Server.default };

    ^super.newCopyArgs(things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, target, addAction).prInit.restoreOldSpace(oldSpace);
  }
  prInit { |oldSpace|
    environment = ();
    soloThings = [];
    things.do(_.parentSpace_(this));
    patches.do(_.parentSpace_(this));
    //inbus = Server.default.options.numOutputBusChannels.asBus('audio', inChannels, Server.default);
    //outbus = 0.asBus('audio', outChannels, Server.default);
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
  }


  // main order of operations
  // usually initiated by thing player
  init {
    initFunc.value(this);
    things.do(_.init);
  }
  play {
    group.free;
    group = Group(target, addAction);
    soloGroup.free;
    soloGroup = Group(group, \addToTail);
    forkIfNeeded {
      playFunc.value(this);
      Server.default.sync;
      things.reverse.do(_.play); // assumes they each add to head
      Server.default.sync;
      patches.do(_.play);
    };
  }
  stop {
    stopFunc.value(this);
    things.do(_.stop);
    patches.do(_.stop);
    group.free;
    group = nil;
    soloGroup.free;
    soloGroup = nil;
  }
  free {
    freeFunc.value(this);
    things.do(_.free);
    inbus.free;
    outbus.free;
  }

  outPatches {
    ^patches.select({ |patch| patch.toThing.class == Event })
  }

  prSoloThing { |thing, solo = true|
    soloThings.remove(thing);
    if (solo.asBoolean) {
      soloThings = soloThings.add(thing);
      this.outPatches.do { |patch| patch.synth.set(\soloMuteGate, 0) };
    } {
      if (soloThings.size == 0) {
        this.outPatches.do { |patch| patch.synth.set(\soloMuteGate, 1) };
      };
    };
  }

  // environment and thing access
  at { |sym|
    // default to 0 if symbol not found
    // this is so it's ok to use thing[\buf] in a play func
    ^(this.thingAt(sym) ? environment.at(sym)) ? 0;
  }
  put { |key, val|
    environment.put(key, val);
    ^this;
  }

  // expose as a class method because of ESThingPatch.newFrom
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
  includedParams {
    ^things.collect(_.includedParams).flat;
  }
  excludedParams {
    var in = this.includedParams;
    ^this.params.reject({ |param| in.indexOf(param).notNil })
  }
  modPatches {
    ^patches.select { |patch| patch.to.index.isKindOf(Symbol) };
  }
  excludedModPatches {
    var modPatches = this.modPatches;
    ^this.excludedParams.collect { |param|
      modPatches.select { |modPatch|
        (modPatch.toThing == param.parentThing) and: (modPatch.to.index == param.name);
      };
    } .flat
  }
  includedModPatches {
    var ex = this.excludedModPatches;
    ^this.modPatches.reject({ |modPatch| ex.indexOf(modPatch).notNil })
  }

  //syntactic sugar
  value { |sym| ^this.thingAt(sym) }

  asTarget {
    ^this.group
  }

  // usually done automatically when you reevaluate
  restoreOldSpace { |oldSpace|
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
      // restore mod amps
      oldSpace.modPatches.do { |oldPatch|
        this.modPatches.do { |patch|
          if ((patch.to.thingIndex == oldPatch.to.thingIndex) and: patch.to.index == oldPatch.to.index) {
            patch.amp = oldPatch.amp;
          };
        };
      };
    };
  }
/*
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
  */

  // generates a func to be used for 2D parameter randomization
  makeXYFunc { |affectModAmps = true|
    var vals, randVals, modAmps, randModAmps, mulFuncs, distX, distY;
    var includedParams = this.includedParams;
    var includedModPatches = this.includedModPatches;
    ^{ |x = 0.5, y = 0.5|
      distY = (y - 0.5);
      distX = (x - 0.5);
      if ((x == 0.5) and: (y == 0.5)) {
        vals = nil;
      } {
        if (vals == nil) {
          vals = includedParams.collect(_.valNorm);
          modAmps = includedModPatches.collect(_.ampNorm);
          randVals = includedParams.collect { |param| if (param.val.isArray) { {1.0.rand2}!param.val.size } { 1.0.rand2 } };
          randModAmps = includedModPatches.size.collect { 1.0.rand2 };
          mulFuncs = max(includedParams.size, this.includedModPatches.size).collect { [{ distX }, { distY }].choose };
        };
        includedParams.do { |param, i|
          var dist = mulFuncs[i].();
          param.valNorm = blend(vals[i], vals[i] + (randVals[i] * dist.sign), dist.abs * 2);
        };
        defer {
          if (affectModAmps) {
            includedModPatches.do { |patch, i|
              var dist = mulFuncs[i].();
              patch.ampNorm = blend(modAmps[i], modAmps[i] + (randModAmps[i] * dist.sign), dist.abs * 2);
            };
          };
        };
      };
    }
  }
}