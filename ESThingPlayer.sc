ESThingPlayer {
  var <>ts, <>knobFunc;
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  var <win, <winBounds;

  *new { |ts, knobFunc|
    ^super.newCopyArgs(ts, knobFunc).initMidi;
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
      knobFunc.(ts, val, num, chan, src)
    });
  }

  play {
    Server.default.waitForBoot {
      ts.init;
      Server.default.sync;
      ts.play;
      win = ts.makeWindow(winBounds);
    };
  }

  stop {
    ts.stop;
    ts.free;
    win !? {
      winBounds = win.bounds;
      win.close
    };
  }

  free {
    [noteOnMf, noteOffMf, bendMf, touchMf, polytouchMf, ccMf].do(_.free);
  }
}