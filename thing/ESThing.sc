

//           ESThing
//        the basic unit of sound creation
//             (see ESThingFactory for its direct usage)
//                 (and ESThingSession to begin properly)



ESThing { // n.b. width can be array of knobs per column
  var <>name,
  // these are user functions to be called at the described times
  // these take argument { |thing| }
  <>initFunc, <>freeFunc, <>playFunc, <>stopFunc,
  // these take { |thing, num, val, chan| }
  <>noteOnFunc, <>noteOffFunc,
  // these take { |thing, val, chan| }
  // except polytouch which takes { |thing, val, num, chan| }
  <>bendFunc, <>touchFunc, <>polytouchFunc, <>slideFunc,
  // i/o specs
  <>inChannels, <>outChannels,
  // exclude params from things like presets and randomization
  <>paramExclude,
  // exclude midi devices from controlling this thing in various ways
  <>midiExclude,
  // funcs to convert midi to cps and vel to amp
  <>midicpsFunc, <>velampFunc,
  // other special user data slots
  <>defName, <>args, <>func,
  // GUI specs
  <>top, <>left, >width,
  // MIDI specs
  <>midiChannel, <>srcID,
  // misc
  <>callFuncOnParamModulate, <>prInitFunc;

  // params is an array of ESThingParam
  // oldParams is an Event of paramName : param
  // just a hack used to defer setting default values until all params are created
  var <params, <oldParams;
  // buses are created on .init / destroyed on .free
  // group is created on .play / destroyed on .stop
  var <>inbus, <>outbus, <>group, <>smbGroup;
  // environment local to thing, so you can store e.g. thing[\buf]
  // these default to 0, not nil
  // by convention synths are stored in thing[\synth], thing[\synths]
  // and params behave sort of like environment variables
  // thing[\paramName] -> param
  var <>environment, <>parentSpace;
  var <>hue = 0.5;

  // for solo, mute, bypass
  var smbSynths, bypassSynths, soloSynths;

  // default midi translation functions
  classvar <>defaultMidicpsFunc, <>defaultVelampFunc;
  *initClass {
    defaultMidicpsFunc = { |num| num.midicps };
    defaultVelampFunc = { |vel| vel.linexp(0, 1, 0.05, 1) };
  }

  // there are too many variables :/
  storeArgs { ^[name, params, initFunc, freeFunc, playFunc, stopFunc,
    noteOnFunc, noteOffFunc, bendFunc,
    touchFunc, polytouchFunc, slideFunc,
    inChannels, outChannels, paramExclude, midiExclude,
    midicpsFunc, velampFunc, defName, args, func,
    top, left, width,
    midiChannel, srcID, callFuncOnParamModulate, prInitFunc] }

  *new { |name, params, initFunc, freeFunc, playFunc, stopFunc,
    noteOnFunc, noteOffFunc, bendFunc,
    touchFunc, polytouchFunc, slideFunc,
    inChannels = 2, outChannels = 2, paramExclude = #[], midiExclude = #[],
    midicpsFunc, velampFunc, defName, args, func,
    top = 0, left = 0, width = 1,
    midiChannel, srcID, callFuncOnParamModulate = false, prInitFunc|

    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;
    ^super.newCopyArgs(name, initFunc, freeFunc, playFunc, stopFunc,
      noteOnFunc, noteOffFunc, bendFunc,
      touchFunc, polytouchFunc, slideFunc,
      inChannels, outChannels, paramExclude.postln, midiExclude,
      midicpsFunc, velampFunc, defName, args, func,
      top, left, width,
      midiChannel, srcID, callFuncOnParamModulate, prInitFunc).prInit(params);
  }
  prInit { |params|
    environment = ();
    oldParams = ();
    this.params_(params);
    prInitFunc.value(this);
  }

  // main order of operations
  // usually initiated by parent space
  init {
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
    initFunc.value(this);
  }
  play {
    group.free;
    smbGroup.free;
    group = Group(parentSpace.group);
    smbGroup = Group(group, \addAfter);
    // patch to output
    smbSynths = outChannels.collect { |i|
      Synth(\ESThingSMBPatch,
        [out: outbus.index + i],
        smbGroup)
    };
    forkIfNeeded {
      // do whatever the playfunc says
      playFunc.value(this);
      // then activate all parameters
      Server.default.sync;
      params.do { |param|
        param.val_(param.val);
      };
    };
  }
  noteOn { |num = 69, vel = 64, chan|
    noteOnFunc.value(this, num, vel/127, chan);
  }
  noteOff { |num = 69, vel = 0, chan|
    noteOffFunc.value(this, num, vel/127, chan);
  }
  bend { |val = 8192, chan|
    bendFunc.value(this, val/8192 - 1, chan);
  }
  touch { |val = 64, chan|
    touchFunc.value(this, val/127, chan);
  }
  polytouch { |val = 64, num = 69|
    polytouchFunc.value(this, val/127, num);
  }
  slide { |val = 64, chan|
    slideFunc.value(this, val/127, chan);
  }
  reset {
    stopFunc.value(this);
    forkIfNeeded {
      // do whatever the playfunc says
      playFunc.value(this);
      // then activate all parameters
      Server.default.sync;
      params.do { |param|
        param.val_(param.val);
      };
    };
  }
  stop {
    stopFunc.value(this);
    group.free;
    smbGroup.free;
    group = nil;
    smbSynths = nil;
    smbGroup = nil;
  }
  free {
    freeFunc.value(this);
    inbus.free;
    outbus.free;
    params.do(_.free);
  }

  // environment and param access
  at { |sym|
    // default to 0
    // so it's ok to use thing[\buf] in a play func
    ^(this.paramAt(sym) ?? { environment.at(sym) ?? { if (parentSpace.notNil) { parentSpace.environment.at(sym) } } }) ? 0;
  }
  put { |key, val|
    var param = this.paramAt(key);
    if (param.notNil) {
      param.val = val;
    } {
      environment.put(key, val);
    };
    ^this;
  }

  // syntactic sugar
  value { |sym|
    ^this.paramAt(sym);
  }
  paramAt { |name|
    params.do { |param|
      if (param.name == name) { ^param }
    };
    ^nil;
  }
  set { |what, val|
    this.paramAt(what).tryPerform('val_', val);
  }
  set127 { |what, val|
    this.paramAt(what).tryPerform('val127_', val);
  }

  index {
    ^parentSpace.things.indexOf(this);
  }
  asTarget {
    ^this.group
  }

  // params can be given in forms:
  //ESThingParam(name, spec, func, val)
  //name->spec
  //name
  /* the default func in ESThingParam:
  { |name, val, synthVal, thing|
      if (thing[\synth].class == Synth) {
        thing[\synth].set(name, synthVal);
      };
      thing[\synths].do({ |synth| synth.set(name, synthVal) });
    }*/
  params_ { |arr|
    params = arr.asArray.collect { |param|
      ESThingParam.newFrom(param)
    };
    // to catch params created late i.e. playFuncSynth
    params.do { |param|
      param.parentThing_(this);
      if (oldParams[param.name].notNil) {
        param.val = oldParams[param.name].val;
      };
    };
    oldParams = ();
  }

  // width is really number of columns
  // if width is array, treat it as a specification for how many params per column
  width { ^if (width.isArray) { width.size } { width } }
  columnSpec {
    if (width.isArray) {
      ^width
    } {
      var sizes = 0.dup(width);
      params.size.do { |i|
        var indx = i % width;
        sizes[indx] = sizes[indx] + 1;
      };
      ^sizes
    };
  }

  mute { |mute = true|
    smbSynths.do(_.set(\muteGate, 1 - mute.asInteger));
  }
  bypass { |bypass = true|
    smbSynths.do(_.set(\bypassGate, 1 - bypass.asInteger));
    bypassSynths.do(_.free);
    bypassSynths = nil;
    if (bypass.asBoolean) {
      bypassSynths = min(inChannels, outChannels).collect { |i|
        Synth(\ESThingPatch,
          [in: inbus.index + i, out: outbus.index + i, amp: 1],
          smbGroup, \addToTail);
      };
    };
  }
  solo { |solo = true|
    parentSpace.prSoloThing(this, solo);
    soloSynths.do(_.free);
    soloSynths = nil;
    if (solo.asBoolean) {
      soloSynths = outChannels.collect { |i|
        Synth(\ESThingPatch,
          [in: outbus.index + i, out: parentSpace.outbus.index + i, amp: 1],
          parentSpace.soloGroup, \addToTail);
      };
    };
  }

  includedParams {
    ^params.select { |param|
      paramExclude.indexOf(param.name).isNil
    }
  }
}