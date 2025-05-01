ESThingParam {
  var <name, <spec, <>func, <val;
  var <>parentThing;
  var moddedVal;
  var <modPatch, <modSynth, <modBus;
  var rout;
  var <>hue;
  storeArgs { ^[name, spec, func, val] }
  *new { |name, spec, func, val|
    spec = (spec ?? { name } ?? { ControlSpec() }).asSpec;
    func = func ? { |name, val, synthVal, thing|
      if (thing[\synth].class == Synth) {
        thing[\synth].set(name, synthVal);
      };
      thing[\synths].do({ |synth| synth.set(name, synthVal) });
    };
    val = val ?? { spec.default };
    ^super.newCopyArgs(name, spec, func, val);
  }
  synthVal {
    ^if (modBus.notNil and: { modBus.index.notNil }) {
      modBus.asMap;
    } {
      val;
    }
  }
  val_ { |argval, stopRout = true|
    if (stopRout) { rout.stop; rout = nil; };
    val = argval;
    if (modSynth.notNil) { modSynth.set(\val, val) };
    //[name, val].postln;
    func.value(name, val, this.synthVal, parentThing);
    this.changed(val);
  }
  valNorm_ { |argval, stopRout = true|
    this.val_(spec.map(argval), stopRout);
  }
  val127_ { |midival|
    this.valNorm_(midival / 127);
  }
  valNorm {
    ^spec.unmap(val);
  }
  val127 {
    ^this.valNorm * 127;
  }
  fadeTo { |value = 0, dur = 1, curve = \sin, hz = 30, clock|
    var fromValNorm = this.valNorm;
    var toValNorm = spec.unmap(value);
    clock = clock ?? SystemClock;
    rout.stop;
    rout = nil;
    if (dur == 0) {
      this.val_(value, false);
    } {
      var waittime = hz.reciprocal;
      var env = Env([fromValNorm, toValNorm], [dur], curve);
      var iterations = (dur * hz).floor;
      rout = {
        iterations.do { |i|
          this.valNorm_(env.at((i + 1) * waittime), false);
          waittime.wait;
        };
      }.fork(clock);
    }
  }
  setModPatch { |patch, inBus|
    var args;
    modPatch = patch;
    modBus = Bus.audio(Server.default);
    args = [out: modBus, in: inBus, amp: patch.amp, val: val, minval: spec.minval, maxval: spec.maxval, step: spec.step];
    if (spec.warp.class == CurveWarp) {
      args = args ++ [curve: spec.warp.curve];
    };
    modSynth = Synth(("ESmodulate" ++ spec.warp.class.asString).asSymbol, args, patch.synth, \addAfter);
    this.val_(val);
  }
  setModulator { |modVal|
    moddedVal = spec.map(spec.unmap(val) + modVal);
    if (parentThing.callFuncOnParamModulate) {
      Server.default.bind {
        func.value(name, moddedVal, moddedVal, parentThing);
      };
    };
  }
  value { ^val }
  moddedVal { ^moddedVal ? val }
  asPattern { ^Pfunc { this.moddedVal } }
  embedInStream { arg inval;
    this.moddedVal.embedInStream(inval);
    ^inval;
  }
  asStream {
    ^Routine({ arg inval;
      loop {
        this.embedInStream(inval)
      }
    })
  }
  free {
    modBus.free;
    modSynth.free;
    this.release;
  }
}

ESThingPatch {
  var <name, <from, <to, <amp;
  var <synth, <oscFunc;
  var <>parentSpace;
  var rout;
  *initClass {
    ServerBoot.add {
      var warpModFuncs = ();
      [CosineWarp, SineWarp, LinearWarp, ExponentialWarp].do { |warp|
        warpModFuncs[warp] = { |val, modVal, minval, maxval, step|
          var spec = ControlSpec(minval, maxval, warp, step);
          var unmappedVal = spec.unmap(val);
          var moddedVal = unmappedVal + modVal;
          spec.map(moddedVal);
        };
      };
      warpModFuncs[DbFaderWarp] = { |val, modVal, minval, maxval, step|
        var valAmp = val.dbamp;
        var minvalAmp = minval.dbamp;
        var rangeAmp = maxval.dbamp - minvalAmp;
        var unmappedVal = Select.kr(rangeAmp > 0, [
          1 - sqrt(1 - ((valAmp - minvalAmp) / rangeAmp)),
          ((valAmp - minvalAmp) / rangeAmp).sqrt
        ]);
        var moddedVal = (unmappedVal + modVal).clip(0, 1);
        Select.ar(K2A.ar(rangeAmp > 0), [
          (1 - (1 - moddedVal).squared) * rangeAmp + minvalAmp,
          moddedVal.squared * rangeAmp + minvalAmp
        ]).ampdb;
      };
      warpModFuncs[FaderWarp] = { |val, modVal, minval, maxval, step|
        var range = maxval - minval;
        var unmappedVal = Select.kr(range > 0, [
          1 - sqrt(1 - ((val - minval) / range)),
          ((val - minval) / range).sqrt
        ]);
        var moddedVal = (unmappedVal + modVal).clip(0, 1);
        Select.ar(K2A.ar(range > 0), [
          (1 - (1 - moddedVal).squared) * range + minval,
          moddedVal.squared * range + minval
        ]);
      };
      warpModFuncs.keysValuesDo { |warp, func|
        var defName = ("ESmodulate" ++ warp.asString).asSymbol;
        SynthDef(defName, { |out, in, amp, val, minval, maxval, step|
          var modVal = InFeedback.ar(in) * amp;
          Out.ar(out, func.(val, modVal, minval, maxval, step));
        }).add;
      };
      SynthDef(\ESmodulateCurveWarp, { |out, in, amp, val, minval, maxval, step, curve|
        var range = maxval - minval;
        var grow = exp(curve);
        var a = range / (1.0 - grow);
        var b = minval + a;
        var modVal = InFeedback.ar(in) * amp;
        var unmappedVal = log((b - val) / a) / curve;
        var moddedVal = unmappedVal + modVal;
        Out.ar(out, b - (a * pow(grow, moddedVal)));
      }).add;
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
  fromThing {
    ^this.prGetThing(from.thingIndex);
  }
  toThing {
    ^this.prGetThing(to.thingIndex);
  }
  play {
    var things = parentSpace.things;
    var addAction = \addBefore;
    // if fromThing or toThing is nil, make a dummy to direct to this space's input or output
    var fromThing = this.fromThing;
    var toThing = this.toThing;
    var target = toThing.asTarget ?? { addAction = \addAfter; things.last.asTarget };
    if (to.index.isKindOf(Symbol)) {
      var toParam = toThing.(to.index);
      var inBus = fromThing.outbus.index + from.index;
      synth = Synth(\ESThingReply, [
        in: inBus,
        id: this.index
      ], target, addAction);
      oscFunc = OSCFunc({ |msg|
        if (msg[2] == this.index) {
          var modVal = msg[3] * amp;
          toParam.setModulator(modVal);
        };
      }, '/ESThingReply');
      toParam.setModPatch(this, inBus);
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
    this.toThing.(to.index).modSynth.set(\amp, val);
    this.changed(\amp, val);
  }
  ampNorm_ { |val|
    this.amp_(\amp.asSpec.map(val));
  }
  amp127_ { |midival|
    this.ampNorm_(midival / 127);
  }
  ampNorm { ^\amp.asSpec.unmap(amp) }
  amp127 { ^this.ampNorm * 127 }
  fadeTo { |value = 0, dur = 1, curve = \sin, hz = 30, clock|
    var spec = \amp.asSpec;
    var fromValNorm = this.ampNorm;
    var toValNorm = spec.unmap(value);
    clock = clock ?? SystemClock;
    rout.stop;
    rout = nil;
    if (dur == 0) {
      this.amp_(value, false);
    } {
      var waittime = hz.reciprocal;
      var env = Env([fromValNorm, toValNorm], [dur], curve);
      var iterations = (dur * hz).floor;
      rout = {
        iterations.do { |i|
          this.ampNorm_(env.at((i + 1) * waittime), false);
          waittime.wait;
        };
      }.fork(clock);
    }
  }

  index {
    ^parentSpace.patches.indexOf(this);
  }
}




//           ESThing



ESThing { // n.b. width can be array of knobs per column
  var <>name, <>initFunc, <>playFunc, <>noteOnFunc, <>noteOffFunc, <>bendFunc, <>touchFunc, <>polytouchFunc, <>stopFunc, <>freeFunc, <inChannels, <outChannels, <>midicpsFunc, <>velampFunc, <>defName, <>args, <>func, <>top, <>left, >width, <>midiChannel, <>srcID, <>callFuncOnParamModulate, <>prInitFunc;
  var <params, <oldParams;
  var <>inbus, <>outbus, <>group;
  var <>environment, <>parentSpace;
  var <>hue = 0.5;

  classvar <>defaultMidicpsFunc, <>defaultVelampFunc;
  *initClass {
    defaultMidicpsFunc = { |num| num.midicps };
    defaultVelampFunc = { |vel| vel.linexp(0, 1, 0.05, 1) };
  }

  width { ^if (width.isArray) { width.size } { width } }
  columnSpec {
    if (width.isArray) {
      ^width
    } {
      var sizes = 0.dup(width);
      params.size.do { |i|
        var indx = i % width;
        sizes[indx] = sizes[indx] + 1;
      };
      ^sizes
    };
  }


  storeArgs { ^[name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID, callFuncOnParamModulate, prInitFunc] }
  *new { |name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, defName, args, func, top = 0, left = 0, width = 1, midiChannel, srcID, callFuncOnParamModulate = false, prInitFunc|
    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;
    ^super.newCopyArgs(name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID, callFuncOnParamModulate, prInitFunc).prInit(params);
  }
  prInit { |params|
    environment = ();
    oldParams = ();
    this.params_(params);
    prInitFunc.value(this);
  }
  params_ { |arr|
    params = arr.asArray.collect { |param|
      if (param.isKindOf(Symbol)) {
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
    params.do(_.free);
  }

  at { |sym|
    // default to 0
    // so it's ok to use thing[\buf] in a play func
    ^(this.paramAt(sym) ?? { environment.at(sym) ?? { if (parentSpace.notNil) { parentSpace.environment.at(sym) } } }) ? 0;
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
    this.paramAt(what).tryPerform('val_', val);
  }
  set127 { |what, val|
    this.paramAt(what).tryPerform('val127_', val);
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
  *new { |things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels = 2, outChannels = 2, useADC = true, useDAC = true, target, oldSpace|
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
      thing.hue = hueList[hueIndex];
      hueIndex = hueIndex + 1 % hueList.size;
      thing;
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
      inbus = Server.default.options.numOutputBusChannels.asBus('audio', inChannels, Server.default);
    } {
      inbus = Bus.audio(Server.default, inChannels);
    };
    if (useDAC) {
      outbus = 0.asBus('audio', outChannels, Server.default);
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