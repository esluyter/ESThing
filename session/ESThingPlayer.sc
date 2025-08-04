

//          ESThingPlayer
//       plays a thing space
//            handles MIDI


ESThingPlayer {
  // ts is the thing space we are playing
  var <ts,
  // these are misc to do with what devices control which things
  <>knobFunc, <>knobArr, <>ccExclude, <>modExclude, <>noteExclude;
  // an instance of ESPresets
  var <presets;
  // midi funcs
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  // GUI window, remembers its bounds on reeval
  var <win, <>winBounds;
  var <isPlaying = false;
  // tsbus so we have volume control etc
  var <tsbus, <amp = 1, <synths;

  *new { |ts, knobFunc, knobArr = ([]), ccExclude = ([]), modExclude = ([]), noteExclude = ([])|
    ts = ts ?? { ESThingSpace() };
    ^super.newCopyArgs(ts, knobFunc, knobArr, ccExclude, modExclude, noteExclude).initMidi.initPresets;
  }
  initPresets {
    presets = ESThingPresets(this);
  }


  // connect to MIDI and register midi funcs
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

  // called later, on play
  initTsbus {
    tsbus = tsbus ?? { Bus.audio(Server.default, ts.outChannels) };
    ts.outbus = tsbus;
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

  play { |soft = false|
    isPlaying = true;
    if (soft.not) {
      Server.default.waitForBoot {
        // make sure the output bus is initialized
        this.initTsbus;
        // play ts
        if (ts.isPlaying) {
          ts.stop;
          ts.free;
        };
        ts.init;
        Server.default.sync;
        ts.play;
        // patch to output
        synths.do(_.free);
        synths = tsbus.numChannels.collect { |i|
          Synth(\ESThingPatch,
            [in: tsbus.index + i, out: i, amp: amp],
            ts.group, \addAfter)
        };
        // make window
        win !? { win.close };
        win = ts.makeWindow(winBounds, "Space", this);
      };
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
    // remember window bounds on close
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

  // smoothly replace ts on reeval
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

  // params can be divided into included and excluded params,
  // for randomization et al
  params { ^ts.params }
  excludedParams { ^ts.excludedParams; }
  includedParams { ^ts.includedParams; }

  things { ^ts.things }

  // modulation patches can also be excluded
  modPatches { ^ts.modPatches }
  excludedModPatches { ^ts.excludedModPatches }
  includedModPatches { ^ts.includedModPatches }

  // automatically assign all params to sequential MIDI ccs
  assignAllKnobs { |ccStart = 0|
    knobArr = ts.params.collect { |param|
      var ret = [ccStart, param.parentThing.name->param.name];
      ccStart = ccStart + 1;
      ret
    } .flat;
  }

  // exclude a MIDI device from mod, note, and cc control
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

  // get params and vars from thing space
  at { |sym| ^ts.at(sym) }


  // generates a func to be used for 2D parameter randomization
  makeXYFunc {
    ^ts.makeXYFunc(this.presets.affectModAmps)
  }
}