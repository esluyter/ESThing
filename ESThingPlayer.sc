ESThingPlayer {
  var <>ts, <>knobFunc;
  var <noteOnMf, <noteOffMf, <bendMf, <touchMf, <polytouchMf, <ccMf;
  var <win, <winBounds;

  *new { |ts, knobFunc|
    ^super.newCopyArgs(ts, knobFunc).initMidi;
  }

  initMidi {
    MIDIClient.init;
    MIDIIn.connectAll;
    noteOnMf = MIDIFunc.noteOn({ |vel, num|
      ts.things.do(_.noteOn(num, vel.postln));
    });
    noteOffMf = MIDIFunc.noteOff({ |vel, num|
      ts.things.do(_.noteOff(num, vel));
    });
    bendMf = MIDIFunc.bend({ |val|
      ts.things.do(_.bend(val));
    });
    touchMf = MIDIFunc.touch({ |val|
      ts.things.do(_.touch(val));
    });
    polytouchMf = MIDIFunc.polytouch({ |val, num|
      ts.things.do(_.polytouch(val, num));
    });
    ccMf = MIDIFunc.cc({ |val, num| knobFunc.(val, num, ts) });
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