

//           ESThing



ESThing { // n.b. width can be array of knobs per column
  var <>name, <>initFunc, <>playFunc, <>noteOnFunc, <>noteOffFunc, <>bendFunc, <>touchFunc, <>polytouchFunc, <>slideFunc, <>stopFunc, <>freeFunc, <inChannels, <outChannels, <>midicpsFunc, <>velampFunc, <>defName, <>args, <>func, <>top, <>left, >width, <>midiChannel, <>srcID, <>callFuncOnParamModulate, <>prInitFunc;
  var <params, <oldParams;
  var <>inbus, <>outbus, <>group;
  var <>environment, <>parentSpace;
  var <>hue = 0.5;

  classvar <>defaultMidicpsFunc, <>defaultVelampFunc;
  *initClass {
    defaultMidicpsFunc = { |num| num.midicps };
    defaultVelampFunc = { |vel| vel.linexp(0, 1, 0.05, 1) };
  }

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


  storeArgs { ^[name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, slideFunc, stopFunc, freeFunc, params, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID, callFuncOnParamModulate, prInitFunc] }
  *new { |name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, slideFunc, stopFunc, freeFunc, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, defName, args, func, top = 0, left = 0, width = 1, midiChannel, srcID, callFuncOnParamModulate = false, prInitFunc|
    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;
    ^super.newCopyArgs(name, initFunc, playFunc, noteOnFunc, noteOffFunc, bendFunc, touchFunc, polytouchFunc, slideFunc, stopFunc, freeFunc, inChannels, outChannels, midicpsFunc, velampFunc, defName, args, func, top, left, width, midiChannel, srcID, callFuncOnParamModulate, prInitFunc).prInit(params);
  }
  prInit { |params|
    environment = ();
    oldParams = ();
    this.params_(params);
    prInitFunc.value(this);
  }
  params_ { |arr|
    params = arr.asArray.collect { |param|
      if (param.isKindOf(Symbol)) {
        param = ESThingParam(param, param.asSpec ?? { ControlSpec() })
      };
      if (param.class == Association) {
        param = ESThingParam(param.key, param.value);
      };
      param
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

  init {
    inbus = Bus.audio(Server.default, inChannels);
    outbus = Bus.audio(Server.default, outChannels);
    initFunc.value(this);
  }
  play {
    group = Group(parentSpace.group);
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
  stop {
    stopFunc.value(this);
    group.free;
  }
  free {
    freeFunc.value(this);
    inbus.free;
    outbus.free;
    params.do(_.free);
  }

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
}