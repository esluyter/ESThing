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