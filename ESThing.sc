ESThingParam {
  var <name, <spec, <func, <val;
  var <>parentThing;
  storeArgs { ^[name, spec, func, val] }
  *new { |name, spec, func, val|
    spec = spec ?? { name.asSpec } ?? { ControlSpec() };
    func = func ? { |name, val, thing|
      thing[\synth].set(name, val);
      thing[\synths].do({ |synth| synth.set(name, val) });
    };
    val = val ?? { spec.default };
    ^super.newCopyArgs(name, spec, func, val);
  }
  val_ { |argval|
    val = argval;
    [name, val].postln;
    func.value(name, val, parentThing);
  }
  val127_ { |midival|
    this.val_(spec.map(midival / 127));
  }
}

ESThingPatch {
  var <from, <to, <amp;
  var <synth;
  var <>parentSpace;
  *initClass {
    ServerBoot.add {
      SynthDef(\ESThingPatch, { |in, out, amp|
        // TODO: change to InFeedback when ready
        Out.ar(out, In.ar(in) * amp);
      }).add;
    };
  }
  *new { |from, to, amp|
    ^super.newCopyArgs(from, to, amp);
  }
  play {
    var things = parentSpace.things;
    var addAction = \addBefore;
    // if fromThing or toThing is nil, make a dummy to direct to this space's input or output
    var fromThing = things[from.thingIndex] ?? {
      (outbus: parentSpace.inbus)
    };
    var toThing = things[to.thingIndex] ?? {
      (inbus: parentSpace.outbus);
    };
    var target = toThing.asTarget ?? { addAction = \addAfter; things.last.asTarget };
    synth = Synth(\ESThingPatch, [
      in: fromThing.outbus.index + from.index,
      out: toThing.inbus.index + to.index,
      amp: amp
    ], target, addAction);
  }
  stop {
    synth.free;
  }
  amp_ { |val|
    amp = val;
    synth.set(\amp, val);
  }
  amp127_ { |midival|
    this.amp_(\amp.asSpec.map(midival / 127));
  }
}




//           ESThing



ESThing {
  var <>initFunc, <>playFunc, <>noteOnFunc, <>noteOffFunc, <>bendFunc, <>touchFunc, <>polytouchFunc, <>stopFunc, <>freeFunc, <>params, <inChannels, <outChannels, <>target;
  var <>inbus, <>outbus, <>group;
  var <>environment, <>parentSpace;

  classvar <>defaultMidicpsFunc, <>defaultVelampFunc;
  *initClass {
    defaultMidicpsFunc = { |num| num.midicps };
    defaultVelampFunc = { |vel| vel.linexp(0, 1, 0.05, 1) };
  }

  *new { |initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels = 0, outChannels = 2, target|
    target = target ?? { Server.default };
    ^super.newCopyArgs(initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, stopFunc, freeFunc, params, inChannels, outChannels, target).prInit;
  }
  prInit {
    environment = ();
    params.do({|param| param.parentThing_(this) });
  }

  init {
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
    initFunc.value(this);
  }
  play {
    group = Group(target);
    fork {
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
    ^environment.at(sym) ?? { if (parentSpace.notNil) { parentSpace.environment.at(sym) } };
  }
  put { |key, val|
    environment.put(key, val);
    ^this;
  }

  getParam { |name|
    params.do { |param|
      if (param.name == name) { ^param }
    };
  }
  set { |what, val|
    this.getParam(what).val_(val);
  }
  set127 { |what, val|
    this.getParam(what).val127_(val)
  }

  asTarget {
    ^this.group
  }
}





//           ESThingSpace



ESThingSpace {
  var <>things, <>patches;
  var <>initFunc, <>playFunc, <>stopFunc, <>freeFunc, <>useADC, <>useDAC, <>inbus, <>outbus;
  var <>environment;

  *new { |things, patches, initFunc, playFunc, stopFunc, freeFunc, inChannels = 2, outChannels = 2, useADC = true, useDAC = true|
    things = things ? [];
    patches = patches ? [];
    ^super.newCopyArgs(things, patches, initFunc, playFunc, stopFunc, freeFunc, useADC, useDAC).prInit(inChannels, outChannels);
  }
  prInit { |inChannels, outChannels|
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
  }

  init {
    initFunc.value(this);
    things.do(_.init);
  }
  play {
    fork {
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
  }
  free {
    freeFunc.value(this);
    things.do(_.free);
  }

  at { |sym|
    ^environment.at(sym);
  }
  put { |key, val|
    environment.put(key, val);
    ^this;
  }
}