

//         ESThingParam
//      a named value, with a spec and a func


ESThingParam {
  var <name, <spec, <>func, <val;
  var <>parentThing;

  // when modulated client-side, val retains knob position
  // and modded val is the current "sounding" / modulated value,
  // updated frequently
  var moddedVal;
  // when modulated server-side, this is mapped by a Synth
  var <modPatch, <modSynth, <modBus;
  // for fadeTo
  var rout;
  // so that e.g. in a space thing, the params can be color coded
  // back to their original thing
  var <>hue;

  storeArgs { ^[name, spec, func, val] }
  *new { |name, spec, func, val|
    // default to name, otherwise default spec
    // need two asSpecs because symbol will return nil
    spec = (spec ?? { name.asSpec }).asSpec;
    if (val.notNil) {
      spec = spec.copy.default_(val);
    };

    // default func updates synth parameter
    func = func ? { |name, val, synthVal, thing|
      if (thing[\synth].class == Synth) {
        thing[\synth].set(name, synthVal);
      };
      thing[\synths].do({ |synth| synth.set(name, synthVal) });
    };
    // default to spec default value
    val = val ?? { spec.default };
    ^super.newCopyArgs(name, spec, func, val);
  }

  // this is the thing that should be sent to synth via .set message
  // if parameter is being modulated, this is a bus mapping
  synthVal {
    ^if (modBus.notNil and: { modBus.index.notNil }) {
      modBus.asMap;
    } {
      val;
    }
  }

  valQuiet_ { |argval|
    // notify dependents but don't set synth
    val = argval;
    this.changed(val);
  }
  // sets the parameter value, by default stopping any fade routine
  val_ { |argval, stopRout = true|
    if (stopRout) { rout.stop; rout = nil; };
    val = argval;
    // if parameter is being modulated, update modulator mapping
    if (modSynth.notNil) { modSynth.set(\val, val) };
    // call the provided func
    func.value(name, val, this.synthVal, parentThing);
    this.changed(val);
  }
  // sets the parameter value, normalized from 0-1
  valNorm_ { |argval, stopRout = true|
    this.val_(spec.map(argval), stopRout);
  }
  // normalized from 0-127
  val127_ { |midival|
    this.valNorm_(midival / 127);
  }
  valNorm {
    ^spec.unmap(val);
  }
  val127 {
    ^this.valNorm * 127;
  }

  // fades parameter over a duration with a curve
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

  // play a synth to modulate this param
  // (n.b the patch will always be playing a ReplyFunc.....)
  setModPatch { |patch, inBus|
    var args;
    modPatch = patch;
    modBus = Bus.audio(Server.default);
    args = [out: modBus, in: inBus, amp: patch.amp, val: val, minval: spec.minval, maxval: spec.maxval, step: spec.step];
    if (spec.warp.class == CurveWarp) {
      args = args ++ [curve: spec.warp.curve];
    };
    modSynth.free;
    modSynth = Synth(("ESmodulate" ++ spec.warp.class.asString).asSymbol, args, patch.synth, \addAfter);
    this.val_(val);
  }

  // update the moddedVal from the reply func
  // (this holds a language-side approximation of the current sounding value)
  setModulator { |modVal|
    moddedVal = spec.map(spec.unmap(val) + modVal);
    if (parentThing.callFuncOnParamModulate) {
      Server.default.bind {
        func.value(name, moddedVal, moddedVal, parentThing);
      };
    };
  }

  // syntax sugar
  value { ^val }

  // for use language-side
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

  // clean up
  free {
    modBus.free;
    if (modSynth.notNil) {
      Server.default.sendBundle(nil, ["/error", -1], modSynth.freeMsg);
    };
    modSynth = nil;
    this.release;
  }
}








ESThingPhasorParam {
  var <name, <val, <id, <func, <oscFunc;
  var <>parentThing;

  // for fadeTo
  var rout;
  // so that e.g. in a space thing, the params can be color coded
  // back to their original thing
  var <>hue;

  storeArgs { ^[name, val] }
  *new { |name, val = 0|
    var func = { |val, id, thing|
      if (thing[\synth].class == Synth) {
        thing[\synth].set("eSPhasor%Phase".format(id), val, "eSPhasor%Trig".format(id), 1);
      };
      thing[\synths].do({ |synth| synth.set("eSPhasor%Phase".format(id), val, "ESPhasor%Trig".format(id), 1); });
    };
    var id = name.asString[8..].asInteger;
    name = "phasor_%".format(id).asSymbol;
    ^super.newCopyArgs(name, val, id, func).initOSC;
  }
  initOSC {
    oscFunc = OSCFunc({ |msg|
      var thisId = msg[2];
      var value = msg[3];
      if (thisId == id) {
        this.valQuiet_(value)
      }
    }, "/ESPhasor");
  }

  valQuiet_ { |argval|
    // notify dependents but don't set synth
    val = argval;
    this.changed(val);
  }
  // sets the parameter value, by default stopping any fade routine
  val_ { |argval, stopRout = true|
    if (stopRout) { rout.stop; rout = nil; };
    val = argval;
    func.value(val, id, parentThing);
    this.changed(val);
  }
  // sets the parameter value, normalized from 0-1
  valNorm_ { |argval, stopRout = true|
    this.val_(argval, stopRout);
  }
  // normalized from 0-127
  val127_ { |midival|
    this.valNorm_(midival / 127);
  }
  valNorm {
    ^val;
  }
  val127 {
    ^this.valNorm * 127;
  }

  // fades parameter over a duration with a curve
  fadeTo { |value = 0, dur = 1, curve = \sin, hz = 30, clock|
    var fromValNorm = this.valNorm;
    var toValNorm = value;//spec.unmap(value);
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

  // syntax sugar
  value { ^val }

  // for use language-side
  moddedVal { ^val }
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

  spec { ^ControlSpec(units: \phasor) }

  // clean up
  free {
    oscFunc.free;
    this.release;
  }
}