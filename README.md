# ESThing

A container for any possible SC code that can be played, with built-in routing, parameter control via MIDI, GUI, code, etc.

Supports "play" / "stop" type code, as well as "noteOn" / "noteOff" type code.

## working examples

### polyphony with midi
```
/*
midi polyphonic synth with note on / off, pitch bend, aftertouch, and full parameter control
*/

(
~play = {
  s.waitForBoot {
    ~ts.init;
    s.sync;
    ~ts.play;
  };
};
~stop = {
  ~ts.stop;
  ~ts.free;
};
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing(
      initFunc: { |thing|
        SynthDef(\sinNote, { |out, amp=0.1, freq=440, bend=0, touch=0, gate=1, pregain=4, modFreq = 4, modAmt = 0.01, amFreq = 1, amAmt = 0.01|
          var env = Env.adsr.ar(2, gate);
          var mod = SinOsc.ar(modFreq.lag2(0.1)) * (modAmt.lag2(0.1) * freq);
          var amod = 1 - (SinOsc.ar(amFreq.lag2(0.1), pi/2) * amAmt.lag2(0.1));
          var sig = SinOsc.ar(freq * (bend.lag2(0.05) * 12).midiratio + mod) * env;
          var gaincomp = (10 - (pregain + amAmt)).clip(0, 10).linexp(0, 10, 1, 4);
          amp = amp * touch.linexp(0, 1, 1, 10);
          sig = (sig * amp * pregain.lag2(0.05) * amod).fold(-10, 10).tanh;
          Out.ar(out, sig * amp * gaincomp);
        }).add;
      },
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        var freq = num.midicps;
        var amp = vel.linexp(0, 1, 0.05, 1);
        thing[\synths][num].free;
        thing[\synths][num] = Synth(\sinNote, [out: thing.outbus, freq: freq, amp: amp, bend: thing[\bend]] ++ defaults, thing.group);
      },
      noteOffFunc: { |thing, num = 69, vel = 0|
        thing[\synths][num].release;
        thing[\synths][num] = nil;
      },
      bendFunc: { |thing, val| // note: val is mapped -1 to 1
        thing[\bend] = val;
        thing[\synths].do { |synth| synth.set(\bend, val) };
      },
      touchFunc: { |thing, val|
        thing[\synths].do { |synth| synth.set(\touch, val) };
      },
      polytouchFunc: { |thing, val, num|
        thing[\synths][num].set(\touch, val);
      },
      stopFunc: { |thing|
        thing[\group].free;
      },
      params: [
        ESThingParam(\pregain, ControlSpec(1, 300, 4)),
        ESThingParam(\modAmt, ControlSpec(0, 100, 8, default: 0.01)),
        ESThingParam(\modFreq, ControlSpec(1, 1000, \exp, default: 4)),
        ESThingParam(\amAmt, ControlSpec(0, 100, 8, default: 0.01)),
        ESThingParam(\amFreq, ControlSpec(0.1, 100, \exp, default: 1))
      ],
      inChannels: 0,
      outChannels: 1
    ),
    ESThing(
      playFunc: { |thing|
        thing[\synth] = {
          var size = \size.kr(1);
          FreeVerb.ar(In.ar(thing.inbus), \size.kr(1), 0.7) * (size + 1)
        }.play(thing.group, thing.outbus);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: [
        ESThingParam(\size)
      ],
      inChannels: 1,
      outChannels: 1
    )
  ],

  patches: [
    ESThingPatch(from: (thingIndex: -1, index: 0), to: (thingIndex: 1, index: 0), amp: 1),
    ESThingPatch(from: (thingIndex: 0, index: 0), to: (thingIndex: 1, index: 0), amp: 0.9),
    ESThingPatch(from: (thingIndex: 0, index: 0), to: (thingIndex: -1, index: 0), amp: 0.2),
    ESThingPatch(from: (thingIndex: 1, index: 0), to: (thingIndex: -1, index: 1), amp: 0.2),
  ],

  initFunc: { |space|
    space[\buf] = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");
  },
  freeFunc: { |space|
    space[\buf].free;
  },

  inChannels: 2,
  outChannels: 2,
  useADC: true,
  useDAC: true
);
~play.();
)

~stop.();

(
MIDIClient.init;
MIDIIn.connectAll;
MIDIdef.noteOn(\noteOn, { |vel, num|
  ~ts.things[0].noteOn(num, vel.postln);
});
MIDIdef.noteOff(\noteOff, { |vel, num|
  ~ts.things[0].noteOff(num, vel);
});
MIDIdef.bend(\bend, { |val|
  ~ts.things[0].bend(val);
});
MIDIdef.touch(\touch, { |val|
  ~ts.things[0].touch(val);
});
MIDIdef.polytouch(\polytouch, { |val, num|
  ~ts.things[0].polytouch(val, num);
});
MIDIdef.cc(\knobs, { |val, num|
  switch (num)
  { 0 } { ~ts.things[0].set127(\pregain, val) }
  { 1 } { ~ts.things[0].set127(\modAmt, val) }
  { 2 } { ~ts.things[0].set127(\modFreq, val) }
  { 3 } { ~ts.things[0].set127(\amAmt, val) }
  { 4 } { ~ts.things[0].set127(\amFreq, val) }

  { 7 } { ~ts.things[1].set127(\size, val) }

  { 15 } { ~ts.patches[2..3].do { |patch| patch.amp127_(val) } }
});
)
```

### continuous synths with patching between them and midi knob control of parameters

```
/*
proof of concept "patching" between "things", with midi controlled parameters that remember their position, sort of
*/

(
~play = {
  s.waitForBoot {
    ~ts.init;
    s.sync;
    ~ts.play;
  };
};
~stop = {
  ~ts.stop;
  ~ts.free;
};
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing(
      playFunc: { |thing|
        thing[\synth] = {
          PlayBuf.ar(1, thing[\buf], \rate.kr(1) * BufRateScale.kr(thing[\buf]), loop: 1)
          * Env.perc(0.001, 0.1).ar(0, Impulse.ar(\impulseFreq.kr(3)))
        }.play(s, thing.outbus);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: try { ~ts.things[0].params.collect({ |param| param.asCompileString.interpret }) } { [
        ESThingParam(\rate, ControlSpec(0.125, 128, 8, default: 1)),
        ESThingParam(\impulseFreq, ControlSpec(0.5, 30, \exp, default: 3))
      ] },
      inChannels: 0,
      outChannels: 1
    ),
    ESThing(
      playFunc: { |thing|
        thing[\synth] = {
          FreeVerb.ar(In.ar(thing.inbus), \size.kr(1), 0.7)
        }.play(s, thing.outbus);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: try { ~ts.things[0].params.collect({ |param| param.asCompileString.interpret }) } { [
        ESThingParam(\size)
      ] },
      inChannels: 1,
      outChannels: 1
    )
  ],

  patches: [
    ESThingPatch(from: (thingIndex: -1, index: 0), to: (thingIndex: 1, index: 0), amp: 1),
    ESThingPatch(from: (thingIndex: 0, index: 0), to: (thingIndex: 1, index: 0), amp: 0.9),
    ESThingPatch(from: (thingIndex: 0, index: 0), to: (thingIndex: -1, index: 0), amp: 0.2),
    ESThingPatch(from: (thingIndex: 1, index: 0), to: (thingIndex: -1, index: 1), amp: 0.2),
  ],

  initFunc: { |space|
    space[\buf] = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");
  },
  freeFunc: { |space|
    space[\buf].free;
  },

  inChannels: 2,
  outChannels: 2,
  useADC: true,
  useDAC: true
);
~play.();
)

~stop.();

(
MIDIClient.init;
MIDIIn.connectAll;
MIDIdef.cc(\knobs, { |val, num|
  switch (num)
  { 0 } { ~ts.things[0].set127(\rate, val) }
  { 1 } { ~ts.things[0].set127(\impulseFreq, val) }

  { 3 } { ~ts.things[1].set127(\size, val) }

  { 15 } { ~ts.patches[2..3].do { |patch| patch.amp127_(val) } }
});
)
```
