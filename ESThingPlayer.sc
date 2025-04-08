ESThingPlayer {
  var <ts, <>knobFunc, <>knobArr;
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  var <win, <winBounds;
  var <isPlaying = false;

  *new { |ts, knobFunc, knobArr = ([])|
    ts = ts ?? { ESThingSpace() };
    ^super.newCopyArgs(ts, knobFunc, knobArr).initMidi;
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
      if (num == 1) {
        checkChans.(chan, src, { |thing| thing.set127(\mod, val) });
      };
      knobFunc.(ts, val, num, chan, src);
      knobArr.pairsDo { |key, value|
        if (num == key) {
          // modulate parameter
          ts.(value.key).set127(value.value, val);
        };
      };
    });
  }

  play {
    Server.default.waitForBoot {
      ts.init;
      Server.default.sync;
      ts.play;
      win = ts.makeWindow(winBounds);
      isPlaying = true;
    };
  }

  stop {
    ts.stop;
    ts.free;
    win !? {
      winBounds = win.bounds;
      win.close
    };
    isPlaying = false;
  }

  free {
    [noteOnMf, noteOffMf, bendMf, touchMf, polytouchMf, ccMf].do(_.free);
  }

  ts_ { |val|
    if (isPlaying) {
      this.stop;
      ts = val;
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
}