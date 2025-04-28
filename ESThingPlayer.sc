ESThingPlayer {
  var <ts, <>knobFunc, <>knobArr, <>ccExclude, <>modExclude, <presets;
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  var <win, <winBounds;
  var <isPlaying = false;
  var <tsbus, <amp = 1, <synths;

  *new { |ts, knobFunc, knobArr = ([]), ccExclude = ([]), modExclude = ([])|
    ts = ts ?? { ESThingSpace() };
    ^super.newCopyArgs(ts, knobFunc, knobArr, ccExclude, modExclude).initTsbus.initMidi.initPresets;
  }

  initTsbus {
    tsbus = Bus.audio(Server.default, ts.outChannels);
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
      checkChans.(chan, src, { |thing| thing.noteOn(num, vel); });
    });
    noteOffMf = MIDIFunc.noteOff({ |vel, num, chan, src|
      checkChans.(chan, src, { |thing| thing.noteOff(num, vel); });
    });
    bendMf = MIDIFunc.bend({ |val, num, chan, src|
      checkChans.(chan, src, { |thing| thing.bend(val); });
    });
    touchMf = MIDIFunc.touch({ |val, num, chan, src|
      checkChans.(chan, src, { |thing| thing.touch(val) });
    });
    polytouchMf = MIDIFunc.polytouch({ |val, num, chan, src|
      checkChans.(chan, src, { |thing| thing.polytouch(val, num); });
    });
    ccMf = MIDIFunc.cc({ |val, num, chan, src|
      // mod wheel
      if ((num == 1) and: modExclude.indexOf(src).isNil) {
        checkChans.(chan, src, { |thing| thing.set127(\mod, val) });
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
    Server.default.waitForBoot {
      ts.init;
      Server.default.sync;
      ts.play;
      synths = tsbus.numChannels.collect { |i| Synth(\ESThingPatch, [in: tsbus.index + i, out: i, amp: amp], ts.group, \addAfter) };
      win = ts.makeWindow(winBounds);
      isPlaying = true;
    };
  }

  amp_ { |val|
    amp = val;
    synths.do(_.set(\amp, amp));
  }

  stop {
    ts.stop;
    ts.free;
    synths.do(_.free);
    synths = nil;
    win !? {
      winBounds = win.bounds;
      win.close
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
      ts = val;
      ts.outbus = tsbus;
      this.play;
    } {
      ts = val;
    }
  }

  assignAllKnobs { |ccStart = 0|
    knobArr = ts.params.collect { |param|
      var ret = [ccStart, param.parentThing.name->param.name];
      ccStart = ccStart + 1;
      ret
    } .flat;
  }

  exclude { |device, excludeMod = true, excludeCc = true|
    var uid = if (device.isInteger) { device } { device.uid };
    if (excludeCc) {
      ccExclude = ccExclude.add(device.uid);
    };
    if (excludeMod) {
      modExclude = modExclude.add(device.uid);
    };
  }
}