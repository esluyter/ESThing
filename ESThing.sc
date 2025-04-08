ESThingParam {
  var <name, <spec, <func, <val;
  var <>parentThing;
  storeArgs { ^[name, spec, func, val] }
  *new { |name, spec, func, val|
    spec = (spec ?? { name } ?? { ControlSpec() }).asSpec;
    func = func ? { |name, val, thing|
      thing[\synth].set(name, val);
      thing[\synths].do({ |synth| synth.set(name, val) });
    };
    val = val ?? { spec.default };
    ^super.newCopyArgs(name, spec, func, val);
  }
  val_ { |argval|
    val = argval;
    //[name, val].postln;
    func.value(name, val, parentThing);
    this.changed(val);
  }
  val127_ { |midival|
    this.val_(spec.map(midival / 127));
  }
  setModulator { |modVal|
    Server.default.bind {
      func.value(name, spec.map(spec.unmap(val) + modVal), parentThing);
    };
  }
  value { ^val }
}

ESThingPatch {
  var <name, <from, <to, <amp;
  var <synth, <oscFunc;
  var <>parentSpace;
  *initClass {
    ServerBoot.add {
      SynthDef(\ESThingPatch, { |in, out, amp|
        Out.ar(out, InFeedback.ar(in) * amp);
      }).add;
      SynthDef(\ESThingReply, { |in, freq = 100, id|
        SendReply.ar(Impulse.ar(freq), '/ESThingReply', InFeedback.ar(in), id);
      }).add;
    };
  }
  storeArgs { ^[name, from, to, amp] }
  *new { |name, from, to, amp = 1|
    if (from.class == Association) {
      from = (thingIndex: from.key, index: from.value);
    };
    if (to.class == Association) {
      to = (thingIndex: to.key, index: to.value);
    };
    ^super.newCopyArgs(name, from, to, amp);
  }
  prGetThing { |thingIndex|
    ^parentSpace.thingAt(thingIndex) ?? {
      (inbus: parentSpace.outbus, outbus: parentSpace.inbus, inChannels: parentSpace.outbus.numChannels, outChannels: parentSpace.inbus.numChannels)
    }
  }
  play {
    var things = parentSpace.things;
    var addAction = \addBefore;
    // if fromThing or toThing is nil, make a dummy to direct to this space's input or output
    var fromThing = this.prGetThing(from.thingIndex);
    var toThing = this.prGetThing(to.thingIndex);
    var target = toThing.asTarget ?? { addAction = \addAfter; things.last.asTarget };
    if (to.index.isSymbol) {
      synth = Synth(\ESThingReply, [
        in: fromThing.outbus.index + from.index,
        id: this.index
      ], target, addAction);
      oscFunc = OSCFunc({ |msg|
        var modVal = msg[3] * amp;
        toThing.(to.index).setModulator(modVal);
      }, '/ESThingReply');
    } {
      synth = Synth(\ESThingPatch, [
        in: fromThing.outbus.index + from.index,
        out: toThing.inbus.index + to.index,
        amp: amp
      ], target, addAction);
    }
  }
  stop {
    synth.free;
    oscFunc.free;
  }
  amp_ { |val|
    amp = val;
    synth.set(\amp, val);
    this.changed(\amp, val);
  }
  amp127_ { |midival|
    this.amp_(\amp.asSpec.map(midival / 127));
  }

  index {
    ^parentSpace.patches.indexOf(this);
  }
}




//           ESThing



ESThing {
  var <>name, <>initFunc, <>playFunc, <>noteOnFunc, <>noteOffFunc, <>bendFunc, <>touchFunc, <>polytouchFunc, <>stopFunc, <>freeFunc, <inChannels, <outChannels, <>midicpsFunc, <>velampFunc, <>defName, <>args, <>func, <>top, <>left, <>width, <>midiChannel, <>srcID;
  var <params, <oldParams;
  var <>inbus, <>outbus, <>group;
  var <>environment, <>parentSpace;

  classvar <>defaultMidicpsFunc, <>defaultVelampFunc;
  *initClass {
    defaultMidicpsFunc = { |num| num.midicps };
    defaultVelampFunc = { |vel| vel.linexp(0, 1, 0.05, 1) };
  }

  storeArgs { ^[name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID] }
  *new { |name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, defName, args, func, top = 0, left = 0, width = 1, midiChannel, srcID|
    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;
    ^super.newCopyArgs(name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID).prInit.params_(params);
  }
  prInit {
    environment = ();
    oldParams = ();
  }
  params_ { |arr|
    params = arr.asArray.collect { |param|
      if (param.isSymbol) {
        param = ESThingParam(param, param.asSpec ?? { ControlSpec() })
      };
      if (param.class == Association) {
        param = ESThingParam(param.key, param.value);
      };
      param
    };
    // to catch params created late i.e. playFuncSynth
    params.do { |param|
      param.parentThing_(this);
      if (oldParams[param.name].notNil) {
        param.val = oldParams[param.name].val;
      };
    };
    oldParams = ();
  }

  init {
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
    initFunc.value(this);
  }
  play {
    group = Group(parentSpace.group);
    forkIfNeeded {
      // do whatever the playfunc says
      playFunc.value(this);
      // then activate all parameters
      Server.default.sync;
      params.do { |param|
        param.val_(param.val);
      };
    };
  }
  noteOn { |num = 69, vel = 64|
    noteOnFunc.value(this, num, vel/127);
  }
  noteOff { |num = 69, vel = 0|
    noteOffFunc.value(this, num, vel/127);
  }
  bend { |val = 8192|
    bendFunc.value(this, val/8192 - 1);
  }
  touch { |val = 64|
    touchFunc.value(this, val/127);
  }
  polytouch { |val = 64, num = 69|
    polytouchFunc.value(this, val/127, num);
  }
  stop {
    stopFunc.value(this);
    group.free;
  }
  free {
    freeFunc.value(this);
    inbus.free;
    outbus.free;
  }

  at { |sym|
    ^this.paramAt(sym).value ?? { environment.at(sym) ?? { if (parentSpace.notNil) { parentSpace.environment.at(sym) } } };
  }
  put { |key, val|
    var param = this.paramAt(key);
    if (param.notNil) {
      param.val = val;
    } {
      environment.put(key, val);
    };
    ^this;
  }

  // syntactic sugar
  value { |sym|
    ^this.paramAt(sym);
  }
  paramAt { |name|
    params.do { |param|
      if (param.name == name) { ^param }
    };
    ^nil;
  }
  set { |what, val|
    this.paramAt(what).val_(val);
  }
  set127 { |what, val|
    this.paramAt(what).val127_(val)
  }

  index {
    ^parentSpace.things.indexOf(this);
  }
  asTarget {
    ^this.group
  }
}





//           ESThingSpace



ESThingSpace {
  var <>things, <>patches;
  var <>initFunc, <>playFunc, <>stopFunc, <>freeFunc, <inChannels, <outChannels, <>useADC, <>useDAC, <>target;
  var <>inbus, <>outbus, <>group;
  var <>environment;

  storeArgs { ^[things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, useADC, useDAC, target]}
  *new { |things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels = 2, outChannels = 2, useADC = true, useDAC = true, target, oldSpace|
    // syntactic sugar
    things = things.asArray.collect { |thing|
      if (thing.isKindOf(ESThing)) {
        thing
      } {
        if (thing.isKindOf(Association)) {
          var name = thing.key;
          var value = thing.value;
          var ret;
          if (value.isKindOf(Function)) {
            ret = ESThing.playFuncSynth(name, value);
          };
          if (value.isKindOf(Ref)) {
            ret = ESThing.droneSynth(name, value.dereference);
          };
          if (value.isKindOf(Dictionary)) {
            var thisKey = value.keys.select(_.isSymbol.not).pop;
            var thisValue = value[thisKey];
            var inChannels = 2, outChannels = 2;
            var kind = \drone;
            if (thisValue.isSymbol) {
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
            if (thisKey.isKindOf(Function)) {
              ret = ESThing.playFuncSynth(name, thisKey, value[\params], inChannels, outChannels, value[\top] ? 0, value[\left] ? 0, value[\width] ? 1, value[\midiChannel], value[\srcID])
            };
            if (thisKey.isKindOf(Ref)) {
              var method = switch (kind)
              { \drone } { \droneSynth }
              { \mono } { \monoSynth }
              { \poly } { \polySynth };
              ret = ESThing.perform(method, name, thisKey.dereference, value[\args], value[\params], inChannels, outChannels, value[\midicpsFunc], value[\velampFunc], value[\top] ? 0, value[\left] ? 0, value[\width] ? 1, value[\midiChannel], value[\srcID]);
            };
          };
          ret;
        } {
          // don't know what to do with this
          thing
        };
      };
    };
    patches = patches.asArray.collect { |patch|
      var n;
      if (patch.isKindOf(Dictionary)) {
        var amp = patch.removeAt(\amp) ?? 1;
        if (patch.size == 1) {
          var arr = patch.asKeyValuePairs;
          patch.from = arr[0];
          patch.to = arr[1];
        };
        if ((patch.from.class == Symbol) or: (patch.from.class == Integer)) {
          var fromThing = this.thingAt(patch.from, things) ?? {
            (outChannels: inChannels)
          };
          patch.from = patch.from->(fromThing.outChannels.collect{ |n| n });
        };
        if ((patch.to.class == Symbol) or: (patch.to.class == Integer)) {
          var toThing = this.thingAt(patch.to, things) ?? {
            (inChannels: outChannels)
          };
          patch.to = patch.to->(toThing.inChannels.collect{ |n| n });
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
            ESThingPatch(patch.name, patch.from.wrapAt(i), patch.to.wrapAt(i), amp ?? 1)
          };
        };
      };
      patch
    } .flat;
    target = target ?? { Server.default };
    ^super.newCopyArgs(things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels, outChannels, useADC, useDAC, target).prInit(oldSpace);
  }
  prInit { |oldSpace|
    environment = ();
    things.do(_.parentSpace_(this));
    patches.do(_.parentSpace_(this));
    if (useADC) {
      inbus = Server.default.options.numOutputBusChannels.asBus;
    } {
      inbus = Bus.audio(Server.default, inChannels);
    };
    if (useDAC) {
      outbus = 0.asBus;
    } {
      outbus = Bus.audio(Server.default, outChannels);
    };

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
                [oldParam.name, oldParam.value].postln;
                newParam.val_(oldParam.val);
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
    ^this.thingAt(sym) ?? environment.at(sym);
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
  //syntactic sugar
  value { |sym| ^this.thingAt(sym) }

  asTarget {
    ^this.group
  }
}