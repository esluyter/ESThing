# ESThing

A container for any possible SC code that can be played, with built-in routing, parameter control via MIDI, GUI, code, etc.

### ESThing
- Provides a dedicated environment, group, inbus, and outbus.
- Hooks for
  - init/free
  - play/stop
  - noteOn/noteOff/bend
  - touch/polytouch
- Allows you to define `params` with custom hooks
  - by default these send `set` messages to whatever is in the environment under `synth` and `synths`
- Provides special templates
  - playFuncSynth (similar to {}.play)
  - Playing a SynthDef with e.g. a midi controller, using `in`, `out`, `freq`, `amp`, `bend`, `touch`, `portamento`/`gate` controls
    - monoSynth
    - polySynth

### ESThingSpace
- A container for many ESThings, as well as patched connections between them
- Provides a dedicated environment inherited by its things, as well as a group
- Either allocates dedicated inbus and outbus, or uses ADC and DAC
- Hooks for
  - init/free
  - play/stop
- Allows you to define `patches`, i.e. 1x1 connections between a specific output of one thing and a specific input of another, with gain control

## working examples

### hello world
read a buffer, make two sound generators, and patch outputs
```
(
~ts = ESThingSpace(
  things: [
    // give things names to reference them later
    ESThing.playFuncSynth(\osc, { SinOsc.ar }),
    // wrap func in a func to access the thing's environment
    ESThing.playFuncSynth(\playbuf, { |thing|
      { PlayBuf.ar(1, thing[\buf], BufRateScale.kr(thing[\buf]), loop: 1) }
    })
  ],

  patches: [
    // patch each thing to one speaker
    // syntax is terse so this hopefully won't get tiresome
    // (fromThingName->outletNumber : toThingName->inletNumber)
    // -1 means output
    (\osc->0 : -1->0, amp: 0.2),
    (\playbuf->0 : -1->1, amp: 0.2),
  ],
  
  // allocate and free shared resources for the space
  initFunc: { |space|
    space[\buf] = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");
  },
  freeFunc: { |space|
    space[\buf].free;
  }
);

// start it up
s.waitForBoot {
  ~ts.init;
  s.sync;
  ~ts.play;
};
)


// stop it and free resources
~ts.stop;
~ts.free;
```

### monophony and polyphony with midi

with portamento, note on / off, pitch bend, aftertouch, and full parameter control

```
/*
       1. Prep: SynthDefs, MIDI, overall on/off switch
*/
(
s.waitForBoot {
  SynthDef(\sinNote, { |out, amp=0.1, freq=440, bend=0, touch=0, gate=1, pregain=4, modFreq = 4, modAmt = 0.01, amFreq = 1, amAmt = 0.01, portamento = 0|
    var env = Env.adsr.ar(2, gate);
    var mod = SinOsc.ar(modFreq.lag2(0.1)) * (modAmt.lag2(0.1) * freq);
    var amod = 1 - (SinOsc.ar(amFreq.lag2(0.1), pi/2) * amAmt.lag2(0.1));
    var sig = SinOsc.ar(freq.lag2(portamento) * (bend.lag2(0.05) * 12).midiratio + mod) * env;
    var gaincomp = (10 - (pregain + amAmt)).clip(0, 10).linexp(0, 10, 1, 4);
    amp = amp * touch.lag2(0.05).linexp(0, 1, 1, 10);
    sig = (sig * amp * pregain.lag2(0.05) * amod).fold(-10, 10).tanh;
    Out.ar(out, sig * amp * gaincomp);
  }).add;
};

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
  { 5 } { ~ts.things[0].set127(\portamento, val) }

  { 7 } { ~ts.things[1].set127(\size, val) }

  { 15 } { ~ts.patches[2..3].do { |patch| patch.amp127_(val) } }
});

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
)



/*
      2a. monophonic synth
*/

(
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing.monoSynth(\sinNote,
      defName: \sinNote,
      args: [],
      params: [
        \pregain->ControlSpec(1, 300, 4),
        \modAmt->ControlSpec(0, 100, 8, default: 0.01),
        \modFreq->ControlSpec(1, 1000, \exp, default: 4),
        \amAmt->ControlSpec(0, 100, 8, default: 0.01),
        \amFreq->ControlSpec(0.1, 100, \exp, default: 1),
        \portamento->ControlSpec(0, 5, 6)
      ],
      inChannels: 0,
      outChannels: 1
    ),
    ESThing.playFuncSynth(\verb,
      func: { |in|
        FreeVerb.ar(In.ar(in), \size.kr(1), 0.7)
      },
      params: [
        ESThingParam(\size)
      ],
      inChannels: 1,
      outChannels: 1
    )
  ],

  patches: [
    (-1->0 : \verb->0, amp: 1),  // mic in to verb
    (\sinNote->0 : \verb->0, amp: 0.9),  // oscillator to verb
    (\sinNote->0 : -1->0, amp: 0.2), // oscillator to left out
    (\verb-> : -1->1, amp: 0.2), // verb to right out
  ]
);
~play.();
)

~stop.();


/*
      2b. polyphonic synth with note on / off, pitch bend, aftertouch, and full parameter control
*/

(
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing.polySynth(
      defName: \sinNote,
      args: [],
      params: [
        \pregain->ControlSpec(1, 300, 4),
        \modAmt->ControlSpec(0, 100, 8, default: 0.01),
        \modFreq->ControlSpec(1, 1000, \exp, default: 4),
        \amAmt->ControlSpec(0, 100, 8, default: 0.01),
        \amFreq->ControlSpec(0.1, 100, \exp, default: 1)
      ],
      inChannels: 0,
      outChannels: 1
    ),
    ESThing.playFuncSynth(
      func: { |in|
        FreeVerb.ar(In.ar(in), \size.kr(1), 0.7)
      },
      params: [
        ESThingParam(\size)
      ],
      inChannels: 1,
      outChannels: 1
    )
  ],

  patches: [
    (-1->0 : \verb->0, amp: 1),  // mic in to verb
    (\sinNote->0 : \verb->0, amp: 0.9),  // oscillator to verb
    (\sinNote->0 : -1->0, amp: 0.2), // oscillator to left out
    (\verb-> : -1->1, amp: 0.2), // verb to right out
  ]
);
~play.();
)

~stop.();
```

<details>

<summary>Proof of concept</summary>
  
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

</details>
