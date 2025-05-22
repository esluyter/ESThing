ESThingPlayer {
  var <ts, <>knobFunc, <>knobArr, <>ccExclude, <>modExclude, <>noteExclude, <>paramExclude;
  var <presets;
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  var <win, <>winBounds;
  var <isPlaying = false;
  var <tsbus, <amp = 1, <synths;

  *new { |ts, knobFunc, knobArr = ([]), ccExclude = ([]), modExclude = ([]), noteExclude = ([]), paramExclude = ([])|
    ts = ts ?? { ESThingSpace() };
    ^super.newCopyArgs(ts, knobFunc, knobArr, ccExclude, modExclude, noteExclude, paramExclude).initMidi.initPresets;
  }

  initTsbus {
    tsbus = tsbus ?? { Bus.audio(Server.default, ts.outChannels) };
    ts.outbus = tsbus;
  }

  initPresets {
    presets = ESThingPresets(this);
  }

  initMidi {
    var checkChans = { |chan, src, func|
      ts.things.do({ |thing|
        if (thing.midiChannel.isNil or: (chan == thing.midiChannel)) {
          if (thing.srcID.isNil or: (thing.srcID == src)) {
            func.(thing);
          };
        };
      });
    };
    MIDIClient.init;
    MIDIIn.connectAll;
    noteOnMf = MIDIFunc.noteOn({ |vel, num, chan, src|
      if (noteExclude.indexOf(src).isNil) {
        checkChans.(chan, src, { |thing| thing.noteOn(num, vel, chan); });
      };
    });
    noteOffMf = MIDIFunc.noteOff({ |vel, num, chan, src|
      if (noteExclude.indexOf(src).isNil) {
        checkChans.(chan, src, { |thing| thing.noteOff(num, vel, chan); });
      };
    });
    bendMf = MIDIFunc.bend({ |val, chan, src|
      checkChans.(chan, src, { |thing| thing.bend(val, chan); });
    });
    touchMf = MIDIFunc.touch({ |val, chan, src|
      checkChans.(chan, src, { |thing| thing.touch(val, chan) });
    });
    polytouchMf = MIDIFunc.polytouch({ |val, num, chan, src|
      checkChans.(chan, src, { |thing| thing.polytouch(val, num); });
    });
    ccMf = MIDIFunc.cc({ |val, num, chan, src|
      // mod wheel
      if ((num == 1) and: modExclude.indexOf(src).isNil) {
        checkChans.(chan, src, { |thing| thing.set127(\mod, val) });
      };
      // slide
      if ((num == 74) and: modExclude.indexOf(src).isNil) {
        checkChans.(chan, src, { |thing| thing.slide(val, chan) });
      };
      // check that src is an accepted src, then do the control action
      if (ccExclude.indexOf(src).isNil) {
        this.control(chan, num, val);
        knobFunc.(ts, val, num, chan, src);
      };
    });
  }

  // this is so it can be used as out device by controller...
  control { |chan = 0, num = 0, val = 0|
    knobArr.pairsDo { |key, value|
        if (num == key) {
          // modulate parameter
          ts.(value.key).set127(value.value, val);
        };
      };
  }

  play {
    isPlaying = true;
    Server.default.waitForBoot {
      this.initTsbus;
      ts.init;
      Server.default.sync;
      ts.play;
      synths = tsbus.numChannels.collect { |i| Synth(\ESThingPatch, [in: tsbus.index + i, out: i, amp: amp], ts.group, \addAfter) };
      win !? { win.close };
      win = ts.makeWindow(winBounds, "Space", this);
    };
  }

  amp_ { |val|
    amp = val;
    synths.do(_.set(\amp, amp));
    this.changed(\amp, amp);
  }

  stop {
    synths.do(_.free);
    synths = nil;
    ts.stop;
    ts.free;
    win !? {
      try {
        winBounds = win.bounds;
      };
      win.close;
      win = nil;
    };
    isPlaying = false;
  }

  free {
    [noteOnMf, noteOffMf, bendMf, touchMf, polytouchMf, ccMf].do(_.free);

    tsbus.free;
  }

  ts_ { |val|
    if (isPlaying) {
      this.stop;
      // in case of an error, we still want to play on reeval
      isPlaying = true;
      ts = val;
      ts.outbus = tsbus;
      this.play;
    } {
      ts = val;
    }
  }

  params { ^ts.params }
  excludedParams { ^paramExclude.collect { |ass| ts.(ass.key).(ass.value) } }
  includedParams { var ex = this.excludedParams; ^this.params.reject({ |param| ex.indexOf(param).notNil }) }

  modPatches { ^ts.modPatches }
  excludedModPatches {
    ^this.excludedParams.collect { |param|
      ts.modPatches.select { |modPatch|
        (modPatch.toThing == param.parentThing) and: (modPatch.to.index == param.name);
      };
    } .flat
  }
  includedModPatches {
    var ex = this.excludedModPatches;
    ^this.modPatches.reject({ |modPatch| ex.indexOf(modPatch).notNil })
  }

  assignAllKnobs { |ccStart = 0|
    knobArr = ts.params.collect { |param|
      var ret = [ccStart, param.parentThing.name->param.name];
      ccStart = ccStart + 1;
      ret
    } .flat;
  }

  exclude { |device, excludeMod = true, excludeCc = true, excludeNote = true|
    var uid = if (device.isInteger) { device } { device.uid };
    if (excludeCc) {
      ccExclude = ccExclude.add(uid);
    };
    if (excludeMod) {
      modExclude = modExclude.add(uid);
    };
    if (excludeNote) {
      noteExclude = noteExclude.add(uid)
    };
  }

  at { |sym| ^ts.at(sym) }


  makeXYFunc {
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
          if (this.presets.affectModAmps) {
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