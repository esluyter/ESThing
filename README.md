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
- Allows you to define `patches`, i.e. connections between outputs of one thing and inputs of another, with gain control

## working examples

### hello world
read a buffer, make two sound generators, and patch outputs

<img width="762" alt="Screen Shot 2025-02-14 at 06 59 39" src="https://github.com/user-attachments/assets/d3e0e6c5-f072-4d19-b973-3c3210b5ec80" />


```
(
~ts = ESThingSpace(
  things: [
    // give things names to reference them later, add a frequency knob
    ESThing.playFuncSynth(\osc, { SinOsc.ar(\freq.kr(440)) }, [\freq]),
    // wrap func in a func to access the thing's environment
    ESThing.playFuncSynth(\playbuf, { |thing|
      { PlayBuf.ar(1, thing[\buf], BufRateScale.kr(thing[\buf]) * \rate.kr(1), loop: 1) }
    }, [\rate])
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

// show GUI
~win = ~ts.makeWindow;
)

// stop it and free resources
~ts.stop;
~ts.free;
~win.close;
```

### monophony and polyphony with midi

with portamento, note on / off, pitch bend, aftertouch, and full parameter control

<img width="1112" alt="Screen Shot 2025-02-14 at 05 43 52" src="https://github.com/user-attachments/assets/af4d06fa-1b2f-4d27-9739-d33ccbb5a896" />


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
  }, metadata: (specs: (
    pregain: ControlSpec(1, 300, 4),
    modAmt: ControlSpec(0, 100, 8, default: 0.01),
    modFreq: ControlSpec(1, 1000, \exp, default: 4),
    amAmt: ControlSpec(0, 100, 8, default: 0.01),
    amFreq: ControlSpec(0.1, 100, \exp, default: 1),
    portamento: ControlSpec(0, 5, 6)
  ))).add;
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
      inChannels: 0,
      outChannels: 1
    ),
    ESThing.playFuncSynth(\verb,
      func: { |in|
        FreeVerb.ar(In.ar(in), \size.kr(1), 0.7)
      },
      params: [
        \size->ControlSpec(0, 10, default: 1)
      ],
      inChannels: 1,
      outChannels: 1,
      top: 50,
      left: 50
    )
  ],

  patches: [
    (-1->0 : \verb->0, amp: 1),  // mic in to verb
    (\sinNote->0 : \verb->0, amp: 0.9),  // oscillator to verb
    (\sinNote->0 : -1->0, amp: 0.2), // oscillator to left out
    (\verb->0 : -1->1, amp: 0.2), // verb to right out
  ]
);
~play.();
)

~stop.();


/*
      2b. polyphonic synth
*/

(
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing.polySynth(\sinNote,
      defName: \sinNote,
      inChannels: 0,
      outChannels: 1
    ),
    ESThing.playFuncSynth(\verb,
      func: { |in|
        FreeVerb.ar(In.ar(in), \size.kr(1), 0.7)
      },
      params: [
        \size->ControlSpec(0, 10, default: 1)
      ],
      inChannels: 1,
      outChannels: 1,
      top: 50,
      left: 50
    )
  ],

  patches: [
    (-1->0 : \verb->0, amp: 1),  // mic in to verb
    (\sinNote->0 : \verb->0, amp: 0.9),  // oscillator to verb
    (\sinNote->0 : -1->0, amp: 0.2), // oscillator to left out
    (\verb->0 : -1->1, amp: 0.2), // verb to right out
  ]
);
~play.();
)

~stop.();
```


### sinmod : knobs keep their value when you change and reevaluate the space

some dirty working code showing latest practice

<img width="1112" alt="Screen Shot 2025-02-14 at 06 00 48" src="https://github.com/user-attachments/assets/801f6852-9797-4555-967f-c5862cfa9aa2" />


```
( // server and general prep
~play = {
  s.waitForBoot {
    ~ts.init;
    s.sync;
    ~ts.play;
    ~win = ~ts.makeWindow(~winBounds);
  };
};
~stop = {
  ~ts.stop;
  ~ts.free;
  ~win !? {
    ~winBounds = ~win.bounds;
    ~win.close
  };
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
  { 1 } { ~ts.(\sinmod).set127(\mod, val); ~ts.(\sinesyn).set127(\mod, val); ~ts.(\laughsyn).set127(\mod, val) }
  { 24 } { ~ts.(\sinmod).set127(\grainFreq, val); ~ts.(\sinesyn).set127(\grainFreq, val) }
  { 25 } { ~ts.(\sinmod).set127(\grainFreqKbd, val); ~ts.(\sinesyn).set127(\grainFreqKbd, val) }
  { 26 } { /*~ts.(\sinmod).set127(\grainAtk, val);*/ }
  { 27 } { /*~ts.(\sinmod).set127(\grainRel, val);*/ }
  { 28 } { /*~ts.(\sinmod).set127(\atk, val);*/ }
  { 29 } { /*~ts.(\sinmod).set127(\dec, val);*/ }
  { 30 } { /*~ts.(\sinmod).set127(\sus, val);*/ }
  { 31 } { /*~ts.(\sinmod).set127(\rel, val);*/ }
});

s.waitForBoot {
  SynthDef(\sinmod, {
    var freq = \freq.kr(140) * \bend.kr(0, 0.1).midiratio + [0, 1];
    var gate = \gate.kr(1) + Impulse.kr(0);

    var in = LocalIn.ar(2);
    var env = Env.adsr(\atk.kr(2), \dec.kr(5), \sus.kr(0.5), \rel.kr(2)).ar(2, gate);

    var grainFreqKbd = \grainFreqKbd.kr(0.5, 0.1);
    var grainFreq = (\grainFreq.kr(0, 0.1) * LinSelectX.kr(grainFreqKbd, [DC.kr(300), freq]));
    var grainTrig = Impulse.ar(grainFreq);
    var grainTilt = LFDNoise3.kr(1.0) * \grainTilt.kr(0);
    var grainAtk = \grainAtk.kr(0.3) + (grainTilt * [0.2, -0.2]).max(0);
    var grainRel = \grainRel.kr(0.5) + (grainTilt * [-0.3, 0.3]).max(0);
    var grainDur = grainFreq.reciprocal * grainRel.linlin(0, 0.7, 0.5, 1);
    var grainEnv = Env.linen(grainAtk * grainDur, (1 - (grainAtk + grainRel) * grainDur), grainRel * grainDur).ar(0, grainTrig);
    var modDecay = \modDecay.kr(7.5);
    var sat = \sat.kr(0);
    var mod = \mod.kr(0.4).linexp(0.0, 1.0, 0.1, 20.0) * LFDNoise3.ar(in.exprange(0.0001, 1)) * Line.ar(1.0, 0.5, modDecay).lincurve(0, 1, 0, 1, -4);
    var sig = SinOsc.ar(freq + (in * mod * freq));

    var grainEnvDC = (grainFreq < 0.1).lag2(0.2);
    grainEnv = grainEnv + grainEnvDC;

    sig = sig * env;
    sig = sig * grainEnv;
    sig = (XFade2.ar(sig, DFM1.ar(sig, LFDNoise3.kr(0.1).exprange(1000, 10000)), LFDNoise3.kr(0.1).range(-1, -1 + sat)));
    sig = XFade2.ar(sig, sig.tanh, -1 + (sat * 10));
    LocalOut.ar(sig);
    Out.ar(\out.kr(0), sig * \amp.kr(0.1));
  }, metadata: (specs: (
    \grainFreq: ControlSpec(0, 10, 10),
    \grainFreqKbd: ControlSpec(default: 0.5),
    \grainAtk: ControlSpec(0, 0.5, default: 0.3),
    \grainRel: ControlSpec(0, 0.7, default: 0.5),
    \atk: ControlSpec(0, 5, 4, default: 2),
    \dec: ControlSpec(0, 5, 4, default: 5),
    \sus: ControlSpec(0, 1, default: 0.5),
    \rel: ControlSpec(0, 10, default: 2),
  ))).add;
};
)


s.record
( // main
~stop.();
~ts = ESThingSpace(
  things: [
    ESThing.polySynth(\sinmod,
      defName: \sinmod,
      inChannels: 0,
      outChannels: 2,
      width: 2
    ),

    ESThing.playFuncSynth(\sinesyn, { |in|
      var localIn = LocalIn.ar(2);
      var inA = InFeedback.ar(in, 2) * \grainFreq.kr(1);
      var freq = \freq.kr(440, 0.05) * \bend.kr(0, 0.1).midiratio;//MouseX.kr(20, 1000, \exponential);
      var amp = \amp.kr(0.1, 0.05);
      var mod = \mod.kr(0, 0.05).lincurve(0, 1, 0.0, 1.0, 3);
      var sig = SinOsc.ar(((localIn + inA) * mod.linexp(0, 1, 0.08, 1.0)/*MouseY.kr(0.08, 1.0, \exponential)*/).linexp(-1, 1, 0.01, 100) * freq);
      sig = VAMoogLadderOS.ar(sig, (freq * 50 + 500) * (sig * \grainFreqKbd.kr(0).linlin(0, 1, 0, 10)).linexp(-1, 1, 0.5, 2, nil), (inA * Latch.ar(inA, Impulse.ar(inA.linexp(-1, 1, 0.1, 10)))).linexp(-1, 1, 0.001, 0.2));
      sig = sig + ([0.000001, 0] * LFDNoise3.ar(localIn.linexp(-1, 1, 0.1, 1.0)).range(0.0, 1.0)) + (0.5 * sig.reverse);
      LocalOut.ar(sig);
      Limiter.ar(sig * 1, 1.5) * 0.2 * amp;
    }, [
      \mod,
      \freq,
      \grainFreq->ControlSpec(0, 10, 10),
      \grainFreqKbd->ControlSpec(default: 0.5)
    ], inChannels: 2, outChannels: 2, top: 250, left: 20),

    ESThing.playFuncSynth(\laughsyn, { |thing|
      var buf = thing[\laughbuf];
      { |in|
        var buf = \buf.kr(buf);
        var localIn = LocalIn.ar(2);
        var inB = InFeedback.ar(in, 2);
        var freq = \freq.kr(440, 0.5);
        var amp = \amp.kr(0.1, 0.05);
        var mod = \mod.kr(0, 0.05).lincurve(0, 1, 0.0, 1.0, 3);
        var phs = Phasor.ar(DC.ar(0.0), freq.expexp(20, 1000, 0.01, 10, nil)/*MouseX.kr(0.01, 10, \exponential)*/ * BufRateScale.kr(buf) * Latch.ar(localIn, Impulse.ar(localIn.linexp(-1, 1, 0.1, 50))).linlin(-1, 1, -2, 4) + (localIn), 0.3, 5.4 * BufSampleRate.kr(buf));
        var sig = BufRd.ar(2, buf, phs, interpolation: Latch.ar(localIn.linlin(-1, 1, 1, 4), Impulse.ar(localIn.linexp(-1, 1, 0.1, 10))));
        sig = sig + ([0.000001, 0] * LFDNoise3.ar(localIn.linexp(-1, 1, 0.1, 1.0)).range(0.0, 1.0)) + (0.5 * sig.reverse);
        sig = VAMoogLadderOS.ar(sig, localIn.linexp(-1, 1, 500, 10000), mod);
        LocalOut.ar(sig + ([0.000001, 0] * Line.ar(1.0, 0.0, 10)) + (0.9 * sig.reverse));
        sig * amp;
      }
    }, [
      \mod,
      \freq
    ], inChannels: 2, outChannels: 2, top:50),

    ESThing.playFuncSynth(\verb, { |in| NHHall.ar(In.ar(in, 2), \size.kr(5)) }, [\size->ControlSpec(0, 10, default: 5)], inChannels: 2, outChannels: 2, top: 350),
  ],

  patches: [
    (\sinmod->[0, 1] : -1->[0, 1], amp: 0.2),

    (\laughsyn->[0, 1] : \sinesyn->[0, 1], amp: 10),
    (\sinesyn->[0, 1] : \laughsyn->[0, 1], amp: 10),

    (\sinesyn->[0, 1] : -1->[0, 1], amp: 0),

    (\laughsyn->[0, 1] : \verb->[0, 1]),
    (-1->[0] : \verb->[0, 1], amp: 0.1),
    (\verb->[0, 1] : -1->[0, 1]),
  ],

  initFunc: { |space|
    space[\laughbuf] = Buffer.read(s, "/Users/ericsluyter/Music/Logic/Bounces/pw laugh.wav");
  },
  freeFunc: { |space|
    space[\laughbuf].free;
  },

  oldSpace: ~ts // comment out to refresh all values
);
~play.();
)
```


<details>

<summary>Proofs of concept</summary>

### fledgling GUI

```
(
var left = 50;
var inlets = [];
var outlets = [];
var adc = ~ts.inChannels.collect { |i| 0@(i * 50 + 25) };
var dac = ~ts.outChannels.collect { |i| 1000@(i * 50 + 25) };

Window.closeAll;
w = Window("Space", Rect(0, 40, 1000, 800)).background_(Color.gray(0.95)).front;

~patchView = UserView(w, w.bounds.copy.origin_(0@0)).drawFunc_({
  ~ts.patches.collect { |patch|
    var fromI = ~ts.(patch.from.thingIndex).tryPerform(\index);
    var toI = ~ts.(patch.to.thingIndex).tryPerform(\index);
    var fromPoint = if (fromI.isNil) { adc[patch.from.index] } { outlets[fromI][patch.from.index] };
    var toPoint = if (toI.isNil) { dac[patch.to.index] } { inlets[toI][patch.to.index] };
    
    var p1 = fromPoint;
    var p2 = toPoint;
    var offset = Point(0, max(((p2.y - p1.y) / 2), max((p1.y - p2.y) / 3, if (p2.y < p1.y) { 80 } { 40 })));
    var sideoffset = Point(max((p2.x - p1.x) / 2, max((p1.x - p2.x) / 4, 80)), 0);
    
    Pen.moveTo(p1);
    Pen.curveTo(p2, p1 + sideoffset, p2 - sideoffset);
    Pen.color_(Color.gray(1 - (patch.amp.curvelin(0, 10, 0.1, 1, 4))));
    Pen.stroke;
    
    //Pen.line(fromPoint, toPoint);
    //Pen.stroke;
  }
});

adc.do { |point|
  View(w, Rect(point.x, point.y - 2, 7, 5)).background_(Color.black);
};
dac.do { |point|
  View(w, Rect(point.x - 7, point.y - 2, 7, 5)).background_(Color.black);
};

~thingView = { |thing, parentView, left|  
  var top = thing.top + 50 + (50 * thing.index);
  var width = 90 * thing.width;
  var height = thing.params.size / thing.width * 75 + 30;
  var view = View(parentView, Rect(left, top, width, height)).background_(Color.gray(1));
  var newInlets = [];
  var newOutlets = [];
  if (thing.name.notNil) {
    StaticText(view, Rect(2, 0, width, 20)).string_(thing.name).font_(Font.sansSerif(14, true));
  };
  thing.params.do { |param, i|
    EZKnob(view, Rect(2 + (90 * (i % thing.width)), 75 * (i / thing.width).floor + 20, 80, 70), param.name, param.spec, { |knob| thing.set(param.name, knob.value) }, param.val, labelWidth: 100, labelHeight: 15)
  };
  thing.inChannels.do { |i|
    var thisLeft = left - 5;
    var thisTop = top + (i * 30) + 15;
    newInlets = newInlets.add(left@thisTop);
    View(parentView, Rect(thisLeft, thisTop, 5, 3)).background_(Color.black);
  };
  thing.outChannels.do { |i|
    var thisLeft = left + width;
    var thisTop = top + (i * 30) + 15;
    newOutlets = newOutlets.add(thisLeft@thisTop);
    View(parentView, Rect(thisLeft, thisTop, 5, 3)).background_(Color.black);
  };
  inlets = inlets.add(newInlets);
  outlets = outlets.add(newOutlets);
  view;
};

~thingViews = ~ts.things.collect { |thing| 
  left = left + thing.left.postln;
  ~thingView.(thing, w, left); 
  left = left + (90 * thing.width) + 10;
};
)
```

### saving presets

proof of concept

```
(
var preset = ();
~ts.things.do  { |thing|
  if (thing.name.notNil) {
    preset[thing.name] = ();
    thing.params.do { |param|
      preset[thing.name][param.name] = param.val;
    };
  };
};
preset;
)

/*
( 'sinmod': ( 'sus': 0.5, 'rel': 1.2267136495451, 'atk': 0.0, 'dec': 0.41654477973629,
  'grainTilt': 0.48412119436425, 'modDecay': 0.99482871683896, 'grainRel': 0.089130044531846, 'grainFreqKbd': 0.5, 'grainAtk': 0.039954377691494,
  'sat': 1.0, 'grainFreq': 0.59216264354641, 'mod': 0.60668461069361 ) )
*/
```
  
### original proof of concept: continuous synths with patching between them and midi knob control of parameters

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
