# ESThing

An experimental live performance framework:

A "thing" is a container for any possible SC code that can be played (a bit similar to NodeProxy et al). This framework provides some nice features like built-in routing with mute/solo/bypass; MIDI keyboard polyphony and MPE; parameter control via MIDI, GUI, and Open Stage Control; buffer management; and presets.

<br />
<br />

<img width="50%" alt="Screenshot 2025-05-02 at 3 56 36 AM" src="https://github.com/user-attachments/assets/2917f52b-9e17-4926-b949-b9ded546001a" /><img width="50%" alt="Screenshot 2025-05-02 at 3 56 06 AM" src="https://github.com/user-attachments/assets/ee428329-a161-4c2f-b221-825d6752f4e9" />



<img width="1049" alt="Screenshot 2025-04-25 at 3 46 28 AM" src="https://github.com/user-attachments/assets/5c5babfd-a1c8-4715-a900-ba71ea53d2ad" />

<details>
  <summary>code (beware old syntax)</summary>

```supercollider
// prep
(
~tp = ESThingPlayer();
~tp.play;
~bufs = ESBufList('bufs', [ ( 'name': 'testing', 'buf': Platform.resourceDir +/+ "sounds/a11wlk01.wav" ) ]).makeWindow;
~tp.presets.makeWindow;
// exclude these params from e.g. randomization
~tp.paramExclude = [\ADTspace->\gate_4, \ADTspace->\amt_4, \ADTspace->\bypass_0];
)

// main (reevaluate to update graph, thing parameters will remember their current values)
(
~tsadt = ESThingSpace(
  things: [
    \dummyIn->({ |thing| { |in, bypass = 1|
      PlayBuf.ar(2, thing[\buf], BufRateScale.kr(thing[\buf].postln), loop: 1)[0] * 0.5 * (1 - bypass) + (In.ar(in) * bypass);
    } }: [1, 1]),
    \extendedDelay->({ |in, amt = 0|
      var inSig = In.ar(in, 1) * 0.1;
      var localIn = LocalIn.ar(2);
      var sig = inSig * amt + (localIn * (amt * 0.1 + 0.9));
      var delSig = Allpass1.ar(DelayC.ar(sig, 0.2, 0.06666));
      LocalOut.ar(delSig);
      delSig;
    }: [1, 1], top: 150, left: -100),
    \extendedDelay2->({ |in|
      var amt = \amt.kr(0, spec: ControlSpec(0, 1, -2));
      var amt2 = \amt2.kr(0, spec: ControlSpec(0, 1, -2));
      var inSig = In.ar(in, 1);
      var delSig, delSig2;
      var chain = FFT(LocalBuf(512), inSig, 0.25);
      chain = PV_Freezish(chain, amt, amt);
      delSig = IFFT(chain);

      chain = FFT(LocalBuf(4096), inSig, 0.25);
      chain = PV_Freezish(chain, amt2, amt2);
      delSig2 = IFFT(chain);

      delSig * amt + (delSig2 * amt2) + (inSig * (1 - (amt + amt2).clip));
    }: [1, 1], top: 150, left: 50),
    \adt->({ |in, wetness, decay=10, adtAmt = 1|
      var inSig = In.ar(in, 1);
      var localIn = LocalIn.ar(2);
      var delay = DelayC.ar(inSig, 0.2, 0.03 + LFDNoise3.kr(1, 0.01));
      var localDelay = DC.ar(0!2);
      var farDelay = DC.ar(0!2);
      var sig;
      3.do {
        localDelay = localDelay + AllpassC.ar((localIn * wetness.linlin(0, 1, 0, 0.5)), 0.2, {exprand(0.05, 0.2)}!2, decay, 0.12);
      };
      3.do {
        farDelay = farDelay + AllpassC.ar(localDelay * wetness.linlin(1, 2, 0, 0.5), 1, {exprand(0.2, 1.0)}!2, decay * 2);
      };
      localDelay = localDelay.tanh; // * wetness.linlin(0, 1, 0, 5);
      farDelay = farDelay.tanh;
      sig = inSig + delay + localDelay;
      LocalOut.ar(sig);
      localDelay + farDelay + (delay * adtAmt);
    }: [1, 2], params: [
      \wetness->ControlSpec(0, 2, default: 0.5),
      \decay->ControlSpec(4, 10, default: 10),
      \adtAmt->ControlSpec(0, 1, \amp, default: 1)
    ], top: 50, left: 50),
    \grains->({ |thing| { |in, gate, amt|
      var inSig = In.ar(in, 1);
      //var gate = MouseButton.kr(0, 1, 0);
      var duration = Latch.kr(Sweep.kr(gate), 1 - gate);
      RecordBuf.ar(inSig * gate, thing[\recbuf], loop: 0, trigger: gate);
      (
        GrainBuf.ar(2, Dust.kr(max(duration, 0.1).reciprocal), duration, thing[\recbuf], 1, pan: LFDNoise1.kr(1))
        + GrainBuf.ar(2, Dust2.kr(LFDNoise3.kr(1).exprange(1, 10)), LFDNoise3.kr(1).exprange(0.1, 1), thing[\recbuf], 1, LFDNoise3.kr(1).range(0, duration / 10))
      ) * amt;
    } }: [1, 2], params:[
      \gate->\amp,
      \amt->\amp
    ], top: -100),
  ],

  patches: [
    (\in->0 : \dummyIn),
    (\dummyIn : \extendedDelay),
    (\dummyIn : \extendedDelay2),
    (\dummyIn : \grains),
    //(\dummyIn : \out, amp: 0.3), // omit for live
    (\extendedDelay : \extendedDelay2),
    (\extendedDelay : \adt),
    (\extendedDelay2 : \adt),
    (\adt : \out),
    (\grains : \out)
  ],

  initFunc: { |space|
    space[\buf] = Buffer.read(s, "/Users/ericsluyter/Downloads/Standing Trees Vocal Solo.wav");
    space[\recbuf] = Buffer.alloc(s, s.sampleRate * 10);
  },
  freeFunc: { |space|
    space[\buf].free;
    space[\recbuf].free;
  },

  //oldSpace: ~tp.ts // comment out to refresh all values
);
//s.record;

~tp.ts = ~ts = ESThingSpace(
  things: [
    \lfo->({
      SinOsc.ar(\lofreq.kr(29.45))
    }: [0, 1]),
    \lfo2->({
      SinOsc.ar(\lofreq.kr(0.45))
    }: [0, 1], top: 100, left: -90),
    \lfo3->({
      [
        LFPulse.ar(\pulsefreq.kr(0.4, spec: \lofreq) * 0.25, width: LFDNoise1.kr(1).range(0.1, 0.5)),
        LFDNoise3.ar(\noisefreq.kr(0.4, spec: \lofreq)).range(0, 1)
      ]
    }: [0, 2], top: 200, left: -130),
    \osc->({
      Pan2.ar(SinOsc.ar(\freq.ar(440)), \pan.kr(0), \amp.kr)
    }: [1, 2], left: -10, top: -20),
    \pb->({
      var buf = ~bufs[\testing];
      PlayBuf.ar(1, buf, BufRateScale.kr(buf) * \rate.kr(0.27), loop:1) * \amp.kr(0.5);
    }: [0, 1], top: 250, left: -120),
    \verb->({ |in|
      in = In.ar(in, 2);
      NHHall.ar(in, \size.kr(4.12, spec: [1, 10])) * \amp.kr(0.24)
    }: [2, 2], left: 0, top: 20),
    \out->({ |in| In.ar(in) }:[2, 2], top: -100, left: -100),
    \ADTspace->((~tsadt): [2, 2], top: -130, left: -20, width: [4, 3, 2])
  ],
  patches: [
    (\in->0: \osc),
    (\lfo: \osc->\freq, amp: 0.1),
    (\lfo2: \osc->\pan, amp: 1),
    (\lfo : \pb->\rate, amp: 0.1),
    (\lfo2 : \lfo->\lofreq, amp: 0.35),
    (\osc : \out),
    (\pb : \out),
    (\osc : \verb),
    (\pb : \verb),

    (\lfo : \verb->\amp, amp: 0.1),
    (\verb : \out2),

    //(\out : \out2),
    (\out : \ADTspace),
    (\lfo3->0 : \ADTspace->'gate_4'),
    (\lfo3->1 : \ADTspace->'amt_4'),
    (\ADTspace : \out2),
  ],

  oldSpace: ~tp.ts // comment out to refresh all values
);
)
```

</details>



<br />
<br />
<br />
<br />

# Syntax

A "space" is a bunch of "things" that make sound, have parameters, and are patched together. A thing can be a space, if you want.

A session is a bunch of spaces with independent volume control.

The idea is that you build these spaces iteratively by reevaluating your code, the GUI shows you what's going on and gives you knobs which maintain their state when you reevaluate your code.

Trying to abstract away all the boring repetitive stuff like MIDI and signal routing, with terse syntax.

<br />

## Sessions and spaces

A session holds as many spaces as you want. To add or update a space, you give its arguments as an Array and the session will take care of building and playing the space for you.

Recommended to put in your startup file:

```supercollider
~session = ESThingSession();
```

Usually you start somewhere like:

```supercollider
(
~session[0] = [
  things: [
    // make a thing called sine
    \sine->{ SinOsc.ar(\freq.kr(440)) * \amp.kr(0.1) }
  ],
  patches: [
    // patch sine thing to the output
    \sine
  ]
];
)
```

<img width="652" height="368" alt="Screen Shot 2025-07-21 at 02 53 49" src="https://github.com/user-attachments/assets/26fc6e05-4540-47e9-982c-8824dd1c9eb1" />

There is no need to free a space before replacing it -- it is encouraged to continuously reevaluate your `~session[0] = [...]` block as you work on it, the session will take care of all cleanup.

The full space interface is:

```supercollider
~session = ESThingSession();

(
// syntax to make a thing space, with all default args
// arg labels are optional
~session[0] = [
  things: [], 
  patches: [],
  initFunc: nil,
  playFunc: nil,
  stopFunc: nil,
  freeFunc: nil,
  inChannels: 2,
  outChannels: 2,
  target: nil,
  oldSpace: //(current space in slot 0 -- set to nil to override)
]
)

// set to empty space
~session[0] = [];
// free
~session[0] = nil
```

<br />
<br />

## Things and patching

A thing is just an instance of ESThing. For convenience there are some "factory" things you can make with brief syntax.

Here is a sort of overview of how the patching works, and syntax for playing functions a la `{}.play`

```supercollider
(
~session[0] = [
  things: [
    // extra, of note:
    // multichannel output
    \lfos->{
      LFDNoise3.ar(
        [\lofreq1, \lofreq2].collect(
          _.kr(1, spec: \lofreq)
      ))
    },
    // to provide more parameters, give a dict
    // (type:, channels: [numIns, numOuts], params:, top:, left:, width:, midiChannel:, srcID:)
    \mod->{
      SinOsc.ar(\freq.kr(440))
    }->(top: 200, left: -50),



    // basic playable function
    // (every Thing is audio rate)
    \lfo->{
      LFDNoise3.ar(\lofreq.kr(1))
    },

    // use ar controls for audio rate modulation
    \car->{
      SinOsc.ar(\freq.ar(440))
    }->(top: 150, left: -100),



    // to process input, provide an 'ESIn' control
    // (just a shortcut for In.ar(\in.kr(numChannels)))
    \dist->{
      var in = ESIn(2);
      BuchlaFoldOS.ar(in,
        \gain.kr(1, spec: [0.5, 10, 4])
      ) * \amp.kr(0.1)
      // use paramExclude to leave parameters out of e.g.randomization with the x/y pad
    }->(paramExclude: [\amp])
  ],

  patches: [
    // patch a Thing to the space's output:
    // just use the thing's name
    \dist,
    // alternatively,
    // (\dist : -1) or (\dist : \out)
    // any invalid thing name like \in or \out will patch to space input/output

    // patch a Thing into another Thing
    (\car : \dist),
    // control gain like
    // (\sine: \dist, amp: 1.5)

    // mod a parameter with a Thing
    (\lfo : \dist->\gain, amp: 0.25),

    // parameter modulation at audio rate
    // (just provide ar control in your synthdef)
    (\mod : \car->\freq, amp: 0.5),

    // patching output channels independently
    (\lfos->0 : \mod->\freq, amp: 0.5),
    (\lfos->1 : \lfo->\lofreq, \amp: 0.5),

    (\lfo : \lfos->\lofreq1, amp: 0.25)
  ]
]
)
```

<img width="654" height="481" alt="Screen Shot 2025-07-21 at 02 05 01" src="https://github.com/user-attachments/assets/db7d9b82-01c1-44e5-8a74-b78e682f596a" />

<br />
<br />

### Randomizing and adjusting parameters

By default the x/y pad will randomize all parameters not explicitly excluded via `exclude` as in this case `\dist->\amp` is

GUI knobs function as expected, and via code:

```supercollider
~session[0][\lfo][\lofreq].val = 2
```

Later we will add clients to control via MIDI or over OSC.

### Scoping

Shift-click on the name of a Thing to scope its output bus. Alt-click to scope its input bus.

<br />

### ESPhasor: 2-way position updates

If you use ESPhasor in your function or SynthDef, you will get a playhead slider that updates in real time and is also interactive (move it to change the current position)

(This example also includes miscellany like space init and free funcs, inherited environment variables, and `\buf->{ |thing| {} }` syntax.)

```supercollider
(
~session[0] = [
  initFunc: { |space|
    space[\buf] = Buffer.read(s, ExampleFiles.child);
  },
  freeFunc: { |space|
    space[\buf].free;
  },
  
  things: [
    \lfo->{ LFDNoise3.ar(\lofreq.kr(1)) },
    \buf->{ |thing| {
      var phase, env;
      var rate = \rate.kr(1, spec: [0.1, 10000, \exponential]);
      ESPhasor.buf(thing[\buf], 1, rate) * \amp.kr(0.5);
    }}->(paramExclude: [\amp])
  ],
  patches: [
    (\lfo: \buf->\rate, amp: 0.3),
    \buf
  ]
]
)
```

<img width="651" height="370" alt="Screen Shot 2025-07-29 at 05 14 49" src="https://github.com/user-attachments/assets/eb74f1ed-ecc0-42d2-b7c6-42d1ab92aebf" />

<br />
<br />
<br />
<br />

## Other kinds of Things: SynthDef/MIDI, Pattern, Space

### Playing a SynthDef

Provide a Ref to a symbol to play it as a Synth:

```supercollider
(
~session[0] = [
  things: [
    \synth->`\default
  ],
  
  patches: [
    \synth
  ]
]
)
```

<img width="654" height="481" alt="Screen Shot 2025-07-21 at 02 21 53" src="https://github.com/user-attachments/assets/d5ee83a4-4379-4418-84bf-98cdce339ddf" />

This plays it as a "drone" kind of synth, and if you have a MIDI keyboard connected you should hear the keyboard is already controlling the frequency of the synth. For more typical MIDI cases:

### MIDI mono and poly synths

```supercollider
(
// instead make it a poly synth (if you have a midi keyboard connected, this should automatically work)
// relevant params like \freq and \amp are hidden from gui
~session[0] = [
  things: [
    \synth->`\default->\poly
    //\synth->`\default->\mono
    //\synth->`\default->\mono0 // this is analog-style, requires doneAction of 0
    //\synth->`\default->\drone
    //\synth->`\default->\mpe
  ],

  patches: [
    \synth
  ]
]
)
```

<img width="652" height="346" alt="Screen Shot 2025-07-21 at 02 24 50" src="https://github.com/user-attachments/assets/6b5a7957-181f-4567-9a4a-45fb400d8005" />

Now it plays the \default synth polyphonically. The parameters that are directly controlled by MIDI (such as `freq`) are hidden to avoid clutter.

Here is a default starting point for a polyphonic or mpe instrument SynthDef:

```supercollider
(
// basic starting synth template
SynthDef(\sineSynth, { |out, gate = 1|
  var touch = \touch.kr(0, 0.05);
  var slide = \slide.kr(0.5, 0.05);
  var bendRange = \bendRange.kr(2, spec: [0, 48, 4, 1.0]);
  var bend = \bend.kr(0, 0.05) * bendRange;
  var freq = \freq.kr(440, 0.1) * bend.midiratio;
  var amp = \amp.kr(0.1, 0.05);
  var env = Env.adsr(
    \atk.kr(0.01), \dec.kr(0.3), \sus.kr(0.5), \rel.kr(0.1)
  ).ar(2, gate + Impulse.kr(0));

  var sig = SinOsc.ar(freq);

  Out.ar(out, (sig * env * amp
    * touch.lincurve(0, 1, 1, amp.reciprocal * 4, 2)
    * 0.3).tanh);
}).add;

~session[0] = [
  things: [
    \synth->`\sineSynth->\poly
  ],
  
  patches: [
    \synth
  ]
]
)
```

```supercollider
(
SynthDef(\exquis, { |out, gate = 1|
  var touch = \touch.kr(0, 0.05);
  var slide = \slide.kr(0.5, 0.05);
  var bendRange = \bendRange.kr(2, spec: [0, 48, 4, 1.0]);
  var bend = \bend.kr(0, 0.05) * bendRange;
  var freq = \freq.kr(440, 0.1) * bend.midiratio;
  var amp = \amp.kr(0.1, 0.05);
  var env = Env.adsr(
    \atk.kr(0.01), \dec.kr(0.3), \sus.kr(0.5), \rel.kr(0.1)
  ).ar(2, gate + Impulse.kr(0));
  
  var sig = Pan2.ar(SVF.ar(Pulse.ar(freq), freq * slide.linexp(0, 1, 1, 10), LFDNoise3.kr(1).range(0, 1)));
  
  Out.ar(out, (sig * env * amp 
    * touch.lincurve(0, 1, 1, amp.reciprocal * 4, 6) 
    * 0.3).tanh);
}).add;
)

```

<img width="654" height="481" alt="Screen Shot 2025-07-21 at 02 24 12" src="https://github.com/user-attachments/assets/04458c9e-b96d-4444-a3ae-729617ba3d85" />

<br />
<br />

### Pattern things

```supercollider
(
~session[0] = [
  things: [
    \lfo2->{ SinOsc.ar(\lofreq.kr(1)) },
    \lfo->{ SinOsc.ar(\lofreq.kr(0.1)) },
    \pat->Pbind(
      \note, Pparam(\noteOffset, 0, [-12, 12]) + [-6, 0, 4, 9],
      \dur, Pparam(\dur, 0.1, [0.1, 10, \exp]) * Pwhite(0.1, 0.3) / Pwhite(1, 2),
      \amp, Pparam(\amp, 0, bus: true),
    )->(paramExclude: [\amp]),
  ],
  patches: [
    (\lfo2 : \pat->\amp),
    (\lfo : \pat->\noteOffset),
    \pat
  ]
]
)
```

<img width="651" height="411" alt="Screen Shot 2025-07-28 at 20 41 56" src="https://github.com/user-attachments/assets/dc332727-453f-4d46-9505-f5775598cff3" />

<br />
<br />

### Space things

Provide an Array to create a new space inside of a thing, with the same format `things: [], patches: [], ...`

```supercollider
(
~session[0] = [
  things: [
    \patSpace->[
      things: [
        \lfo2->{ SinOsc.ar(\lofreq.kr(1)) },
        \lfo->{ SinOsc.ar(\lofreq.kr(0.1)) },
        \pat->Pbind(
          \note, Pparam(\noteOffset, 0, [-12, 12]) + [-6, 0, 4, 9],
          \dur, Pparam(\dur, 0.1, [0.1, 10, \exp]) * Pwhite(0.1, 0.3) / Pwhite(1, 2),
          \amp, Pparam(\amp, 0.06, bus: true),
        )->(paramExclude: [\amp]),
      ],
      patches: [
        (\lfo2 : \pat->\amp, amp: 0.2),
        (\lfo : \pat->\noteOffset),
        \pat
      ]
    ]->(paramExclude: [\amp_2]),
    
    \verb->{
      NHHall.ar(ESIn(2), \time.kr(1, spec: [0, 10]), stereo: 1) * \amp.kr(0.5)
    }
  ],
  patches: [
    \patSpace,
    (\patSpace : \verb),
    \verb
  ]
]
)
```
<img width="651" height="501" alt="Screen Shot 2025-07-28 at 21 13 35" src="https://github.com/user-attachments/assets/a911f648-f0e4-4ee2-8c6b-faebdf278fc7" />

Double-click on the name of the space thing to open a new window for just that space.

<br />
<br />

## Using ESThing directly

Template for using ESThing directly to make a new kind of thing (in this case, a Pattern)

(all of the above examples use ESThing under the hood -- see `ESThingFactory.sc` file for implementation

```supercollider
(
~session[0] = [
  things: [
    \lfo2->{ SinOsc.ar(\lofreq.kr(1)) },
    \lfo->{ SinOsc.ar(\lofreq.kr(1)) },
    ESThing(\pat,
      playFunc: { |thing|
        thing[\player] = Pbind(
          \note, Prand([0, 2, 4, 5, 7, 9, 10, 12], inf) + thing.(\noteOffset),
          \dur, Pwhite(0.1, 0.3) / Pwhite(1, 2),
          \amp, thing.(\amp),
          \in, thing.inbus,
          \out, thing.outbus
        ).play
      },
      stopFunc:  { |thing|
        thing[\player].stop
      },
      params: [
        \noteOffset->[-12, 12],
        \amp->\amp
      ]
    )
  ],
  patches: [
    (\lfo2 : \pat->\amp),
    (\lfo : \pat->\noteOffset),
    \pat
  ]
]
)
```
<img width="652" height="349" alt="Screen Shot 2025-07-22 at 03 30 09" src="https://github.com/user-attachments/assets/a47213df-a334-45c5-ba55-00433f8be76e" />

<br />
<br />
<br />

### ESThing
- Provides a dedicated environment, group, inbus, and outbus.
- Hooks for
  - init/free
  - play/stop
  - noteOn/noteOff/bend
  - touch/polytouch
- Allows you to define `params` with custom hooks, or auto-generate them from synth controls
  - by default these send `set` messages to whatever is in the environment under `synth` and `synths`
- Provides special templates
  - playFuncSynth (similar to {}.play)
  - Playing a SynthDef with e.g. a midi controller, using `in`, `out`, `freq`, `amp`, `bend`, `touch`, `portamento`/`gate` controls
    - droneSynth
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

<br />
<br />
<br />

<details>

<summary>old examples</summary>
  
## starting template 
gain knob

```
// prep
(
~tp = ESThingPlayer();
~tp.play;
~bufs = ESBufList('bufs', [ ( 'name': 'testing', 'buf': Platform.resourceDir +/+ "sounds/a11wlk01.wav" ) ]).makeWindow;
)

// main (reevaluate to update graph, thing parameters will remember their current values)
(
// add synthdefs here

~tp.ts = ~ts = ESThingSpace(
  things: [
    \gain->{ |in| 
      In.ar(in, 2) * \gain.kr(0.1) 
    }
  ],
  patches: [
    // (fromThingName->outletNumber : toThingName->inletNumber, amp: 1)
    (\in->0 : \gain),
    (\gain : \out)
  ],
  
  oldSpace: ~tp.ts // comment out to refresh all values
);

// assign knob cc#s to parameters
~tp.knobArr = [
  //cc#: \thing->\param
];
)

// .. or, sequentially assign midi ccs to every parameter
~tp.assignAllKnobs;



// stop it
(
~tp.stop;
~tp.free;
)
```


<img width="762" alt="Screen Shot 2025-04-07 at 23 56 31" src="https://github.com/user-attachments/assets/dc15bba4-fae9-4996-b1a9-3d20ba01bccb" />


<br />
<br />

## more working examples


<br />
<br />

### hello world
read a buffer, make sound, and patch outputs

```
// prep
(
~tp = ESThingPlayer();
~tp.play;
)

(
~tp.ts = ~ts = ESThingSpace(
  things: [
    // wrap func in an event to specify num channels etc
    \osc->({ SinOsc.ar(\freq.kr(440)) }: [0, 1]),
    // wrap func in a func to access buffer from the thing's environment
    \playbuf->({ |thing|
      { PlayBuf.ar(1, thing[\buf], BufRateScale.kr(thing[\buf]) * \rate.kr(1), loop: 1) }
    }: [0, 1]),
    // use backtick to specify SynthDef name
    \synth->`\default
  ],

  patches: [
    (\osc : \out->0, amp: 0.2),
    (\playbuf : \out->1, amp: 0.2),
    (\synth : \out)
  ],
  
  // allocate and free shared resources for the space
  initFunc: { |space|
    space[\buf] = Buffer.read(s, Platform.resourceDir +/+ "sounds/a11wlk01.wav");
  },
  freeFunc: { |space|
    space[\buf].free;
  }
);
)
```

<img width="762" alt="Screen Shot 2025-04-08 at 22 06 14" src="https://github.com/user-attachments/assets/17f7582c-1594-4bff-a552-1b5c15a544de" />

<br />
<br />

### monophony and polyphony with midi

with portamento, note on / off, pitch bend, aftertouch, and full parameter control

all parameter values will persist through code reevaluations

```
// prep
(
~tp = ESThingPlayer();
~tp.play;
)

// main (reevaluate to update graph, thing parameters will remember their current values)
(
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

~tp.ts = ~ts = ESThingSpace(
  things: [
    \sinNote->(`\sinNote: \poly->[0, 1]),
    \verb->({ |in|
      FreeVerb.ar(In.ar(in), \size.kr(1), 0.7) * \amp.kr(0)
    }: 1)
  ],
  patches: [
    (\sinNote : \out),
    (\sinNote : \verb),
    (\verb : \out)
  ],
  
  oldSpace: ~tp.ts // comment out to refresh all values
);
)

// automatically assign all parameters to MIDI knobs
~tp.assignAllKnobs;
```

<img width="762" alt="Screen Shot 2025-04-07 at 23 52 52" src="https://github.com/user-attachments/assets/4d2175f5-2927-43b4-8103-ccbf92ffac71" />


<br />
<br />

</details>

<details>

<summary>Proofs of concept</summary>

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

### sinmod

some dirty working code showing latest practice

<img width="1112" alt="Screen Shot 2025-02-14 at 06 00 48" src="https://github.com/user-attachments/assets/801f6852-9797-4555-967f-c5862cfa9aa2" />


```
( // server and general prep
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

~tp = ESThingPlayer(knobFunc: { |val, num, ts|
  switch (num)
  { 1 } { ts.(\sinmod).set127(\mod, val); ts.(\sinesyn).set127(\mod, val); ts.(\laughsyn).set127(\mod, val) }
  { 24 } { ts.(\sinmod).set127(\grainFreq, val); ts.(\sinesyn).set127(\grainFreq, val) }
  { 25 } { ts.(\sinmod).set127(\grainFreqKbd, val); ts.(\sinesyn).set127(\grainFreqKbd, val) }
});
)


( // main
~tp.stop;
~tp.ts = ~ts = ESThingSpace(
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

    (\sinesyn->[0, 1] : -1->[0, 1], amp: 0.2),
    (\sinesyn->[0, 1] : \verb->[0, 1], amp: 0.15),

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

  oldSpace: ~tp.ts // comment out to refresh all values
);
~tp.play;
)
```

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
