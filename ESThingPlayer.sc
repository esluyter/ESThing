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
    MIDIdef.noteOn(\noteOn, { |vel, num|
      ts.things[0].noteOn(num, vel.postln);
    });
    MIDIdef.noteOff(\noteOff, { |vel, num|
      ts.things[0].noteOff(num, vel);
    });
    MIDIdef.bend(\bend, { |val|
      ts.things[0].bend(val);
    });
    MIDIdef.touch(\touch, { |val|
      ts.things[0].touch(val);
    });
    MIDIdef.polytouch(\polytouch, { |val, num|
      ts.things[0].polytouch(val, num);
    });
    MIDIdef.cc(\knobs, { |val, num| knobFunc.(val, num, ts) });
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
}