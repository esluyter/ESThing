

//      ESThingPatch
//         signal routing
//       from thing to thing, or thing to param
//          each patch is one channel
//              use syntax sugar for multichannel, see ESThingFactory


ESThingPatch {
  // to and from are events with
  // thingIndex (identifying thing) and index (identifying channel number)
  var <name, <from, <to, <amp;
  // synth to play patch, osc func to recieve info back
  var <synth, <oscFunc;
  var <>parentSpace;
  // for fadeTo
  var rout;

  storeArgs { ^[name, from, to, amp] }
  *new { |name, from, to, amp = 1|
    // accept association syntax to and from
    if (from.class == Association) {
      from = (thingIndex: from.key, index: from.value);
    };
    if (to.class == Association) {
      to = (thingIndex: to.key, index: to.value);
    };
    ^super.newCopyArgs(name, from, to, amp);
  }

  // get thing from parent space
  // if thing not found, use a dummy event for space i/o
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

  // either play a synth patching outbus to inbus,
  // or play a synth sending OSC back for language-side modulation
  play {
    var things = parentSpace.things;
    var addAction = \addBefore;
    // if fromThing or toThing is nil, make a dummy to direct to this space's input or output
    var fromThing = this.fromThing;
    var toThing = this.toThing;
    var target = toThing.asTarget ?? { addAction = \addAfter; fromThing.smbGroup };
    synth.free;
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
    synth = nil;
    oscFunc.free;
  }

  // set the patch volume
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

  // fade patch volume
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