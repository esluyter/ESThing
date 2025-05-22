ESThingParam {
  var <name, <spec, <>func, <val;
  var <>parentThing;
  var moddedVal;
  var <modPatch, <modSynth, <modBus;
  var rout;
  var <>hue;
  *initClass {
    StartUp.add {
      ControlSpec.specs[\button] = ControlSpec(0, 1, \lin, 1, units: \button);
      ControlSpec.specs[\toggle] = ControlSpec(0, 1, \lin, 1, units: \toggle);
      ControlSpec.specs[\bend] = ControlSpec(-1, 1);
      ControlSpec.specs[\atk] = ControlSpec(0, 5, 4, default: 0.6);
      ControlSpec.specs[\dec] = ControlSpec(0, 5, 4, default: 1);
      ControlSpec.specs[\sus] = ControlSpec(0, 1, default: 0.5);
      ControlSpec.specs[\rel] = ControlSpec(0, 10, 3, default: 1);
      ControlSpec.specs[\at] = ControlSpec.specs[\atk];
      ControlSpec.specs[\dt] = ControlSpec.specs[\dec];
      ControlSpec.specs[\sl] = ControlSpec.specs[\sus];
      ControlSpec.specs[\rt] = ControlSpec.specs[\rel];
    };
  }
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