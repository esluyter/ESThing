

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
  <>target;

  // buses are created on .init / destroyed on .free
  // group is created on .play / destroyed on .stop
  var <>inbus, <>outbus, <>group, <>soloGroup;
  var <>environment, <>soloThings;

  storeArgs { ^[things, patches,
    initFunc, freeFunc, playFunc, stopFunc,
    inChannels, outChannels, target]}

  *new { |things, patches,
    initFunc, playFunc, stopFunc, freeFunc,
    inChannels = 2, outChannels = 2, target,
    // oldSpace is for remembering parameter values when reevaluating
    oldSpace|

    // the cycle of colors for child things
    var hueList = [ 0.55, 0.3, 0.0, 0.8, 0.5, 0.1, 0.65, 0.35, 0.9, 0.2 ];
    var hueIndex = 0;

    // process array of possible things or associations
    // (See ESThingFactory for implementation)
    things = things.asArray.collect { |thing|
      thing = ESThing.newFrom(thing);
      thing.hue = hueList[hueIndex];
      hueIndex = hueIndex + 1 % hueList.size;
      thing;
    };
    // process array of patches
    // (see ESThingFactory for implementation)
    patches = patches.asArray.collect { |patch|
      ESThingPatch.newFrom(patch, things, inChannels, outChannels)
    } .flat.reject { |item| item.isNil };

    // default to target the default server
    target = target ?? { Server.default };

    ^super.newCopyArgs(things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, target).prInit.restoreOldSpace(oldSpace);
  }
  prInit { |oldSpace|
    environment = ();
    soloThings = [];
    things.do(_.parentSpace_(this));
    patches.do(_.parentSpace_(this));
    inbus = Server.default.options.numOutputBusChannels.asBus('audio', inChannels, Server.default);
    outbus = 0.asBus('audio', outChannels, Server.default);
  }


  // main order of operations
  // usually initiated by thing player
  init {
    initFunc.value(this);
    things.do(_.init);
  }
  play {
    group.free;
    group = Group(target);
    forkIfNeeded {
      playFunc.value(this);
      Server.default.sync;
      things.reverse.do(_.play); // assumes they each add to head
      Server.default.sync;
      patches.do(_.play);
      soloGroup = Group(group, \addToTail);
    };
  }
  stop {
    stopFunc.value(this);
    things.do(_.stop);
    patches.do(_.stop);
    group.free;
    group = nil;
  }
  free {
    freeFunc.value(this);
    things.do(_.free);
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
}