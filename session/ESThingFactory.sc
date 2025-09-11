

//          ESThingFactory
//     all syntax sugar in one place and more?
//       aka quarantine the dirty stuff


+ ESThingParam {
  *initClass {
    // add extra ControlSpecs on startup
    StartUp.add {
      ControlSpec.specs[\push] = ControlSpec(0, 1, \lin, 1, units: \push);
      ControlSpec.specs[\toggle] = ControlSpec(0, 1, \lin, 1, units: \toggle);
      ControlSpec.specs[\bend] = ControlSpec(-1, 1);
      ControlSpec.specs[\atk] = ControlSpec(0, 5, 4, default: 0.6);
      ControlSpec.specs[\dec] = ControlSpec(0, 5, 4, default: 1);
      ControlSpec.specs[\sus] = ControlSpec(0, 1, default: 0.5);
      ControlSpec.specs[\rel] = ControlSpec(0, 10, 3, default: 1);
      ControlSpec.specs[\at] = ControlSpec.specs[\atk];
      ControlSpec.specs[\dt] = ControlSpec.specs[\dec];
      ControlSpec.specs[\sl] = ControlSpec.specs[\sus];
      ControlSpec.specs[\rt] = ControlSpec.specs[\rel];
    };
  }

  *newFrom { |obj|
    if (obj.class == ESThingParam) {
      ^obj
    };
    if (obj.class == ESThingPhasorParam) {
      ^obj
    };
    if (obj.isKindOf(Symbol)) {
      ^ESThingParam(obj, obj.asSpec ?? { ControlSpec() })
    };
    if (obj.class == Association) {
      ^ESThingParam(obj.key, obj.value)
    };
  }
}


+ ESThingPatch {
  *initClass {
    ServerBoot.add {
      var warpModFuncs = ();

      // add necessary SynthDefs for patching and solo/mute/bypass
      SynthDef(\ESThingPatch, { |in, out, soloMuteGate = 1, amp|
        Out.ar(out, InFeedback.ar(in) * amp * soloMuteGate);
      }).add;
      SynthDef(\ESThingReply, { |in, freq = 100, id|
        SendReply.ar(Impulse.ar(freq), '/ESThingReply', InFeedback.ar(in), id);
      }).add;
      SynthDef(\ESThingSMBPatch, { |out, muteGate = 1, bypassGate = 1|
        ReplaceOut.ar(out, In.ar(out) * muteGate * bypassGate);
      }).add;

      // add server-side control spec mapping
      [CosineWarp, SineWarp, LinearWarp, ExponentialWarp].do { |warp|
        warpModFuncs[warp] = { |val, modVal, minval, maxval, step|
          var spec = ControlSpec(minval, maxval, warp, step);
          var unmappedVal = spec.unmap(val);
          var moddedVal = unmappedVal + modVal;
          spec.map(moddedVal);
        };
      };
      warpModFuncs[DbFaderWarp] = { |val, modVal, minval, maxval, step|
        var valAmp = val.dbamp;
        var minvalAmp = minval.dbamp;
        var rangeAmp = maxval.dbamp - minvalAmp;
        var unmappedVal = Select.kr(rangeAmp > 0, [
          1 - sqrt(1 - ((valAmp - minvalAmp) / rangeAmp)),
          ((valAmp - minvalAmp) / rangeAmp).sqrt
        ]);
        var moddedVal = (unmappedVal + modVal).clip(0, 1);
        Select.ar(K2A.ar(rangeAmp > 0), [
          (1 - (1 - moddedVal).squared) * rangeAmp + minvalAmp,
          moddedVal.squared * rangeAmp + minvalAmp
        ]).ampdb;
      };
      warpModFuncs[FaderWarp] = { |val, modVal, minval, maxval, step|
        var range = maxval - minval;
        var unmappedVal = Select.kr(range > 0, [
          1 - sqrt(1 - ((val - minval) / range)),
          ((val - minval) / range).sqrt
        ]);
        var moddedVal = (unmappedVal + modVal).clip(0, 1);
        Select.ar(K2A.ar(range > 0), [
          (1 - (1 - moddedVal).squared) * range + minval,
          moddedVal.squared * range + minval
        ]);
      };
      warpModFuncs.keysValuesDo { |warp, func|
        var defName = ("ESmodulate" ++ warp.asString).asSymbol;
        SynthDef(defName, { |out, in, amp, val, minval, maxval, step|
          var modVal = InFeedback.ar(in) * amp;
          Out.ar(out, func.(val, modVal, minval, maxval, step));
        }).add;
      };
      SynthDef(\ESmodulateCurveWarp, { |out, in, amp, val, minval, maxval, step, curve|
        var range = maxval - minval;
        var grow = exp(curve);
        var a = range / (1.0 - grow);
        var b = minval + a;
        var modVal = InFeedback.ar(in) * amp;
        var unmappedVal = log((b - val) / a) / curve;
        var moddedVal = unmappedVal + modVal;
        Out.ar(out, b - (a * pow(grow, moddedVal)));
      }).add;
    };
  }

  // this might return an array!
  *newFrom { |obj, things, inChannels, outChannels|
    var n;
    var fromInput = false, toOutput = false;
    var cs = obj.asCompileString;
    if (obj.isKindOf(Symbol)) {
      // patch a symbol directly to the output
      obj = (obj : -1);
    };
    if (obj.isKindOf(Dictionary)) {
      var amp = obj.removeAt(\amp) ?? 1;
      if (obj.size == 1) {
        var arr = obj.asKeyValuePairs;
        obj.from = arr[0];
        obj.to = arr[1];
      };
      if ((obj.from.class == Symbol) or: (obj.from.class == Integer)) {
        var fromThing = ESThingSpace.thingAt(obj.from, things);
        if (fromThing.isNil) {
          fromInput = true;
          fromThing = (outChannels: inChannels);
        };
        obj.from = obj.from->(fromThing.outChannels.collect{ |n| n });
      };
      if ((obj.to.class == Symbol) or: (obj.to.class == Integer)) {
        var toThing = ESThingSpace.thingAt(obj.to, things);
        if (toThing.isNil) {
          toOutput = true;
          toThing = (inChannels: outChannels);
        };
        obj.to = obj.to->(toThing.inChannels.collect{ |n| n });
        if (fromInput and: toOutput) {
          // post warning here
          "% -- (% : %)  -- neglected to patch input directly to output".format(cs, obj.from, obj.to).warn;
        };
      };
      // accept arrays of indices
      if (obj.from.value.isArray) {
        obj.from = obj.from.value.collect { |index| obj.from.key->index };
      } {
        obj.from = obj.from.asArray;
      };
      if (obj.to.value.isArray) {
        obj.to = obj.to.value.collect { |index| obj.to.key->index };
      } {
        obj.to = obj.to.asArray;
      };
      if (obj.from.value.isNil or: obj.to.value.isNil) {
        obj = []
      } {
        obj = max(obj.from.size, obj.to.size).collect { |i|
          // guard against patching ins to outs
          var from = obj.from.wrapAt(i);
          var to = obj.to.wrapAt(i);
          if (fromInput and: toOutput) {
            nil
          } {
            ESThingPatch(obj.name, from, to, amp ?? 1)
          }
        };
      };
    };
    ^obj
  }
}




+ ESThingSpace {
  *newFrom { |ts, oldSpace|
    // sugar: convert array to ESThingSpace
    if (ts.isArray) {
      var argNames = ['things', 'patches', 'initFunc', 'playFunc', 'stopFunc', 'freeFunc', 'inChannels', 'outChannels', 'target', 'addAction', 'oldSpace'];
      var args = [[], [], nil, nil, nil, nil, 2, 2, nil, \addToHead, oldSpace];
      var i = 0;
      ts.do { |item|
        var argIndex = argNames.indexOf(item);
        if (argIndex.notNil) {
          i = argIndex;
        } {
          args[i] = item;
          i = i + 1;
        };
      };
      ts = ESThingSpace(*args);
    };
    ^ts;
  }
}

+ ESThing {
  // syntax sugar, create a new thing from an Association
  *newFrom { |obj|
    if (obj.isKindOf(ESThing)) {
      ^obj
    } {
      if (obj.isKindOf(Association)) {
        var name, value, args, ret;
        var type = \drone;

        // supports \name->{...}, \name->`\defname->\type, \name->{...}->(...)
        // name will be first key, value will be the object to make the thing from
        // args will be nil, a Symbol, or an Event
        if (obj.key.isKindOf(Association)) {
          name = obj.key.key;
          value = obj.key.value;
          args = obj.value;
        } {
          name = obj.key;
          value = obj.value;
        };

        if (args.isKindOf(Symbol)) {
          type = args;
          args = nil;
        };

        if (args.isKindOf(Dictionary)) {
          if (args[\type].notNil) {
            type = args[\type];
            args[\type] = nil;
          };

          if (args[\channels].notNil) {
            var thisValue = args[\channels];
            if (thisValue.isInteger) {
              args[\inChannels] = thisValue;
              args[\outChannels] = thisValue;
            };
            if (thisValue.isArray) {
              args[\inChannels] = thisValue[0];
              args[\outChannels] = thisValue[1];
            };
            args[\channels] = nil;
          };
        };

        // because of keyValuePairs later args needs to be an Event
        if (args.isNil) {
          args = ();
        };

        if (value.isKindOf(Function)) {
          var method = \playFuncSynth;
          ^ESThing.performArgs(method, [name, value], args.asKeyValuePairs);
        };
        if (value.isKindOf(Ref)) {
          var method = switch (type)
            { \drone } { \droneSynth }
            { \mono } { \monoSynth }
            { \mono0 } { \mono0Synth }
            { \poly } { \polySynth }
            { \mpe } { \mpeSynth };
          ^ESThing.performArgs(method, [name, value.dereference], args.asKeyValuePairs);
        };
        if (value.isKindOf(Pattern)) {
          ^ESThing.performArgs(\patternSynth, [name, value], args.asKeyValuePairs);
        };
        if (value.isKindOf(Array)) {
          value = ESThingSpace.newFrom(value);
        };
        if (value.isKindOf(ESThingSpace)) {
          ^ESThing.performArgs(\space, [name, value], args.asKeyValuePairs);
        };

        ^ret;
      } {
        // don't know what to do with this
        "Don't know what to do with this %".format(obj).error
        ^nil;
      };
    };
  }

  *prMakeParams { |controls, hideMidiControls = true, synthDesc|
    var params = controls.select { |control|
      var name = control.name;
      // filter out the controls used internally by mono/poly synths
      var isQ = ['?', \in, \out, \i_out].indexOf(name).notNil;
      var exposeControl = [\gate, \bend, \touch, \slide, \freq, \amp].indexOf(name).isNil;
      // filter out any arrayed controls
      var isArray = control.defaultValue.isArray;
      // TODO: have different sort of param and GUI element for arrays
      (exposeControl or: hideMidiControls.not) /*and: isArray.not*/ and: isQ.not
    } .collect({ |control|
      var spec, name;
      name = control.name;
      if (name.asString.beginsWith("eSPhasor")) {
        // if a phasor param: this is double control, just make single param
        if (name.asString.endsWith("Phase")) {
          ESThingPhasorParam(name);
        } {
          nil
        }
      } {
        // if a regular param:
        // look for spec in metadata, otherwise use name.asSpec
        if (synthDesc.notNil and: { (spec = synthDesc.metadata.tryPerform(\at, \specs).tryPerform(\at, control.name)).notNil }) {
          spec = spec.asSpec
        } {
          spec = control.name.asSpec;
        };
        // filter out nils
        spec = spec ?? { ControlSpec() };
        spec = spec.copy.default_(control.defaultValue);
        ESThingParam(name, spec)
      }
    }) .reject(_.isNil);
    ^params;
  }

  *prMakeParamsDefName { |defName, hideMidiControls = true|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      var params = this.prMakeParams(synthDesc.controls, hideMidiControls, synthDesc);
      ^params;
    } {
      "SynthDef '%' not found".format(defName).error;
      ^nil;
    }
  }



  // additional keys: inChannels, outChannels, args,  top = 0, left = 0, width = 1,  midicpsFunc, velampFunc, midiChannel, srcID
  *space { |name, space ...arrgs, kwargs|
    var thing = ESThing.performArgs(\new, [name], [
      initFunc: { |thing|
        thing[\space] = space;
        thing[\space].inbus = thing.inbus;
        thing[\space].outbus = thing.outbus;
        thing[\space].init;
      }, playFunc:  { |thing|
        thing[\space].target = thing.asTarget;
        thing[\space].play;
      }, stopFunc: { |thing|
        thing[\space].stop;
      }, freeFunc: { |thing|
        thing[\space].free;
      },
      params: space.params.collect { |param|
        var newParam = ESThingParam((param.name ++ "_" ++ param.parentThing.index.asCompileString).asSymbol, param.spec, { |name, val| param.parentThing.(param.name).val = val }, param.val).hue_(param.parentThing.hue);
        param.addDependant({ |param, val|
          if (newParam.modPatch.isNil) {
            newParam.valQuiet = val;
          };
        });
        newParam;
      },
      inChannels: space.inChannels,
      outChannels: space.outChannels,
      callFuncOnParamModulate: true
    ] ++ kwargs);
    space.parentThing = thing;
    ^thing
  }



  *playFuncSynth { |name, func, params, inChannels, outChannels ...arrgs, kwargs|
    ^ESThing.performArgs(\new, [name], [
      prInitFunc: { |thing|
        var funcToPlay = if (func.def.argNames.first == \thing) {
          func.value(thing)
        } {
          func
        };
        var def = funcToPlay.asSynthDef;
        thing.params = params ?? {
          this.prMakeParams(def.allControlNames, false, def)
        };
        // infer in and out channels from func spec
        thing.inChannels = inChannels ?? {
          def.asSynthDesc.inputs.collect { |io|
            if (io.type == LocalIn) {
              0
            } {
              io.numberOfChannels
            }
          } .sum
        };
        thing.outChannels = outChannels ?? {
          def.asSynthDesc.outputs.last.numberOfChannels
        };
      },
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var funcToPlay = if (func.def.argNames.first == \thing) {
          func.value(thing)
        } {
          func
        };
        thing[\synth] = funcToPlay.play(thing.group, thing.outbus, fadeTime: 0, args: [in: thing.inbus]);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing.set(\freq, freq);
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num, vel|
        thing[\noteStack].remove(num);
        if (thing[\noteStack].size > 0) {
          thing.set(\freq, thing.midicpsFunc.(thing[\noteStack].last));
        } {
        };
      },
      bendFunc: { |thing, val| // note: val is mapped -1 to 1
        thing.set(\bend, val);
      },
      touchFunc: { |thing, val|
        thing.set(\touch, val);
      },
      polytouchFunc: { |thing, val, num|
        //if (num == thing[\noteStack].last) {
          thing.set(\touch, val);
        //};
      },
      func: func,
      inChannels: inChannels,
      outChannels: outChannels
    ] ++ kwargs)
  }

  *mpeSynth { |name, defName, params, inChannels, outChannels ...arrgs, kwargs|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      // infer in and out channels from func spec
      inChannels = inChannels ?? {
        synthDesc.inputs.collect { |io|
          io.numberOfChannels
        } .sum;
      };
      outChannels = outChannels ?? {
        synthDesc.outputs.last.numberOfChannels
      };
    };
    ^ESThing.performArgs(\new, [name], [
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel, chan| // note: vel is mapped 0-1
        var defaults = [];
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing.params.do({ |param| defaults = defaults ++ [param.name, param.synthVal] });
        thing[\synths][chan].free;
        thing[\synths][chan] = Synth(thing.defName, [
          out: thing.outbus,
          in: thing.inbus,
          doneAction: 2,
          freq: freq,
          amp: amp
        ] ++ thing.args ++ defaults, thing.group);
      },
      noteOffFunc: { |thing, num, vel, chan|
        // TODO: use note off vel
        thing[\synths][chan].release;
        thing[\synths][chan] = nil;
      },
      bendFunc: { |thing, val, chan| // note: val is mapped -1 to 1
        thing[\synths][chan].set(\bend, val);
      },
      touchFunc: { |thing, val, chan|
        thing[\synths][chan].set(\touch, val);
      },
      slideFunc: { |thing, val, chan|
        thing[\synths][chan].set(\slide, val);
      },
      stopFunc: { |thing|
        thing[\group].free;
      },
      params: params ?? { this.prMakeParamsDefName(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      defName: defName,
    ] ++ kwargs)
  }

  *polySynth { |name, defName, params, inChannels, outChannels ...arrgs, kwargs|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      // infer in and out channels from func spec
      inChannels = inChannels ?? {
        synthDesc.inputs.collect { |io|
          io.numberOfChannels
        } .sum;
      };
      outChannels = outChannels ?? {
        synthDesc.outputs.last.numberOfChannels
      };
    };
    ^ESThing.performArgs(\new, [name], [
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var defaults = [];
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing.params.do({ |param| defaults = defaults ++ [param.name, param.synthVal] });
        thing[\synths][num].free;
        thing[\synths][num] = Synth(thing.defName, [
          out: thing.outbus,
          in: thing.inbus,
          doneAction: 2,
          freq: freq,
          amp: amp,
          bend: thing[\bend]
        ] ++ thing.args ++ defaults, thing.group);
      },
      noteOffFunc: { |thing, num, vel|
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
      params: params ?? { this.prMakeParamsDefName(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      defName: defName,
    ] ++ kwargs)
  }

  *monoSynth { |name, defName, params, inChannels, outChannels ...arrgs, kwargs|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      // infer in and out channels from func spec
      inChannels = inChannels ?? {
        synthDesc.inputs.collect { |io|
          io.numberOfChannels
        } .sum;
      };
      outChannels = outChannels ?? {
        synthDesc.outputs.last.numberOfChannels
      };
    };
    ^ESThing.performArgs(\new, [name], [
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|

      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        if (thing[\synth] == 0) {
          var defaults = [];
          thing.params.do({ |param| defaults = defaults ++ [param.name, param.synthVal] });
          thing[\synth] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, doneAction: 2, freq: freq, amp: amp, gate: 1, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
        } {
          thing[\synth].set(\freq, freq, \amp, amp);
        };
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num, vel|
        var synth = thing[\synth];
        thing[\noteStack].remove(num);
        if (synth != 0) {
          if (thing[\noteStack].size > 0) {
            synth.set(\freq, thing.midicpsFunc.(thing[\noteStack].last));
          } {
            synth.set(\gate, 0);
            thing[\synth] = nil;
          };
        };
      },
      bendFunc: { |thing, val| // note: val is mapped -1 to 1
        thing[\bend] = val;
        thing[\synth].set(\bend, val);
      },
      touchFunc: { |thing, val|
        thing[\synth].set(\touch, val);
      },
      polytouchFunc: { |thing, val, num|
        //if (num == thing[\noteStack].last) {
          thing[\synth].set(\touch, val);
        //};
      },
      params: params ?? { this.prMakeParamsDefName(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      defName: defName
    ] ++ kwargs)
  }

  *mono0Synth { |name, defName, params, inChannels, outChannels ...arrgs, kwargs|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      // infer in and out channels from func spec
      inChannels = inChannels ?? {
        synthDesc.inputs.collect { |io|
          io.numberOfChannels
        } .sum;
      };
      outChannels = outChannels ?? {
        synthDesc.outputs.last.numberOfChannels
      };
    };
    ^ESThing.performArgs(\new, [name], [
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = [];
        thing.params.do({ |param| defaults = defaults ++ [param.name, param.synthVal] });
        thing[\synth] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, doneAction: 0, amp: 0, gate: 0, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing[\synth].set(\freq, freq, \amp, amp, \gate, 1);
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num, vel|
        thing[\noteStack].remove(num);
        if (thing[\noteStack].size > 0) {
          thing[\synth].set(\freq, thing.midicpsFunc.(thing[\noteStack].last));
        } {
          thing[\synth].set(\gate, 0)
        };
      },
      bendFunc: { |thing, val| // note: val is mapped -1 to 1
        thing[\bend] = val;
        thing[\synth].set(\bend, val);
      },
      touchFunc: { |thing, val|
        thing[\synth].set(\touch, val);
      },
      polytouchFunc: { |thing, val, num|
        //if (num == thing[\noteStack].last) {
          thing[\synth].set(\touch, val);
        //};
      },
      params: params ?? { this.prMakeParamsDefName(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      defName: defName,
    ] ++ kwargs)
  }

  *droneSynth { |name, defName, params, inChannels, outChannels ...arrgs, kwargs|
    var synthDesc = SynthDescLib.global[defName];
    if (synthDesc.notNil) {
      // infer in and out channels from func spec
      inChannels = inChannels ?? {
        synthDesc.inputs.collect { |io|
          io.numberOfChannels
        } .sum;
      };
      outChannels = outChannels ?? {
        synthDesc.outputs.last.numberOfChannels
      };
    };
    ^ESThing.performArgs(\new, [name], [
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = [];
        thing.params.do({ |param| defaults = defaults ++ [param.name, param.synthVal] });
        thing[\synth] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing[\synth].set(\freq, freq);
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num, vel|
        thing[\noteStack].remove(num);
        if (thing[\noteStack].size > 0) {
          thing[\synth].set(\freq, thing.midicpsFunc.(thing[\noteStack].last));
        } {
        };
      },
      bendFunc: { |thing, val| // note: val is mapped -1 to 1
        thing[\bend] = val;
        thing[\synth].set(\bend, val);
      },
      touchFunc: { |thing, val|
        thing[\synth].set(\touch, val);
      },
      polytouchFunc: { |thing, val, num|
        //if (num == thing[\noteStack].last) {
          thing[\synth].set(\touch, val);
        //};
      },
      params: params ?? { this.prMakeParamsDefName(defName, false) },
      inChannels: inChannels,
      outChannels: outChannels,
      defName: defName,
    ] ++ kwargs)
  }

  *patternSynth { |name, pattern, params ...arrgs, kwargs|
    ^ESThing.performArgs(\new, [name], [
      prInitFunc: { |thing|
        thing[\pattern] = pattern;
        thing.params = params ?? {
          // make params from all Pparams
          Pparam.findIn(thing[\pattern]).collect { |pparam|
            pparam.thing_(thing);
            pparam.param
          };
        };
      },
      playFunc: { |thing|
        thing[\player] = Pbindf(
          thing[\pattern],
          \in, thing.inbus,
          \out, thing.outbus,
          \group, thing.group
        ).play(quant: 1)
      },
      stopFunc:  { |thing|
        thing[\player].stop
      },
    ] ++ kwargs)
  }
}







+ESThingSpace {
  makeWindow { |winBounds, title = "Space", tp|
    var bounds = winBounds ?? { Rect(0, 80, 800, 800) };
    var winWidth = bounds.width;
    var left = 20;
    var inlets = [];
    var outlets = [];
    var knobPoints = [];
    var adc = inChannels.collect { |i| i * 50 + 25 };
    var dac = outChannels.collect { |i| i * 50 + 25 };

    var w = Window(title, bounds).background_(Color.gray(0.95)).front;

    // xy randomizer
    var func = if (tp.notNil) { tp.makeXYFunc } { this.makeXYFunc };
    var pad = Slider2D(w, Rect(w.bounds.width - 220, w.bounds.height - 220, 200, 200)).resize_(9).x_(0.5).y_(0.5).background_(Color.gray(0.95)).action_{ |view|
      func.(view.x, view.y);
    }.mouseUpAction_{ |view|
      view.x = 0.5;
      view.activey = 0.5;
    };

    // for knobs later
    var redPatches = patches.select { |patch| patch.to.index.isKindOf(Symbol) };
    var redParams = redPatches.collect { |patch| patch.toThing.(patch.to.index) };

    var patchView = UserView(w, w.bounds.copy.origin_(0@0)).resize_(5).acceptsMouse_(false).drawFunc_({ |v|
      patches.collect { |patch|
        var fromI = this.(patch.from.thingIndex).tryPerform(\index);
        var toI = this.(patch.to.thingIndex).tryPerform(\index);
        var toPoint = if (patch.to.index.isKindOf(Symbol)) {
          knobPoints[toI][patch.to.index];
        } {
          if (toI.isNil) { v.bounds.width@dac[patch.to.index] } { inlets[toI][patch.to.index] };
        };
        var fromPoint = if (fromI.isNil) { 0@adc[patch.from.index] } { outlets[fromI][patch.from.index] };
        var colorVal = (patch.amp.curvelin(0, 10, 0.1, 1, 4));
        var hue = patch.fromThing.hue;
        var color = if (patch.to.index.isKindOf(Symbol)) {
          Pen.lineDash = FloatArray[3, 1];
          Pen.width = 1.5;
          // default to red if hue is nil
          Color.hsv(hue ?? 0, 1, 0.5, colorVal * 1.25);
        } {
          Pen.lineDash = FloatArray[2, 0];
          // wider for input
          Pen.width = if (hue.notNil) { 2 } { 3 };
          // black if hue is nil (i.e. input)
          Color.hsv(hue ?? 0, 1, if (hue.notNil) { 0.5 } { 0 }, colorVal)
        };

        var p1 = fromPoint;
        var p2 = toPoint;
        var offset = Point(0, max(((p2.y - p1.y) / 2), max((p1.y - p2.y) / 3, if (p2.y < p1.y) { 80 } { 40 })));
        var sideoffset = Point(max((p2.x - p1.x) / 4, max((p1.x - p2.x) / 8, 5)), 0);

        Pen.moveTo(p1);
        Pen.curveTo(p2, p1 + sideoffset, p2 - sideoffset);
        Pen.color_(color);
        Pen.stroke;
      };
    });

    var thingView = { |thing, parentView, left|
      var columnSpec = thing.columnSpec;
      var columnMax = columnSpec.maxItem(_.value);
      var top = thing.top + 20 + (30 * thing.index);
      var width = 90 * thing.width;
      var height = max(columnMax, max(thing.inChannels, thing.outChannels) / 2.5 - 0.5) * 75 + 55;
      var view = UserView(parentView, Rect(left, top, width, height)).background_(Color.hsv(thing.hue, 0.05, 1, 0.8)).drawFunc_({ |view|
        Pen.use {
          Pen.addRect(view.bounds.copy.origin_(0@0));
          Pen.color = Color.hsv(thing.hue, 1, 0.5);
          Pen.width = 2;
          Pen.stroke;
        };
      });
      var newInlets = [];
      var newOutlets = [];
      var newKnobPoints = ();
      var w;
      var x = 0, y = 0;
      if (thing.name.notNil) {
        StaticText(view, Rect(5, 3, width, 20)).string_(thing.name).font_(Font.sansSerif(14, true)).stringColor_(Color.hsv(thing.hue, 1, 0.5)).mouseDownAction_{ |v, x, y, mods, buttNum, clickCount|
          if (mods.isAlt) {
            if (thing.inbus.numChannels > 0) {
              "Scoping inbus #%".format(thing.inbus.index).postln;
              thing.inbus.scope
            } {
              "No input - Press shift to scope outbus".warn;
            }
          };
          if (mods.isShift) {
            if (thing.outbus.numChannels > 0) {
              "Scoping outbus #%".format(thing.inbus.index).postln;
              thing.outbus.scope
            } {
              "No output - Press option to scope inbus".warn;
            }
          };
          if (clickCount == 2) {
            if (thing[\space].class == ESThingSpace) {
              w !? { w.close };
              w = thing[\space].makeWindow(Rect(850, 80, 650, 800), thing.name);
            };
          };
        };
      };
      thing.params.do { |param, i|
        var hue = param.hue ? thing.hue;
        var point = (left + 72 + (90 * x))@((75 * y) + 40 + top);
        var knobBounds = Rect(5 + (90 * x), (75 * y) + 30, 80, 70);

        if (param.val.isNumber) {
          // they won't be red anymore, unless modulated by an input
          var redIndex = redParams.indexOf(param);
          var patchKnob;
          var knob;

          switch(param.spec.units)
          { \push } {
            var dependantFunc = { |param, val|
              defer { butt.value = val };
            };
            var butt = Button(view, knobBounds.copy.insetBy(0, 10).moveBy(0, 10).width_(55)).states_([["PUSH", Color.hsv(hue, 0.5, 0.5), Color.hsv(hue, 0.2, 1)], ["PUSH", Color.white, Color.hsv(hue, 1, 0.675)]]).mouseDownAction_{ |view| view.valueAction = 1}.action_{ |view| param.val_(view.value) };
            StaticText(view, knobBounds.copy.height_(15)).string_(param.name).stringColor_(Color.hsv(hue, 1, 0.35)).acceptsMouse_(false);
            param.addDependant(dependantFunc);
            butt.onClose = { param.removeDependant(dependantFunc) };
          }
          { \toggle } {
            var dependantFunc = { |param, val|
              defer { butt.value = val };
            };
            var butt = Button(view, knobBounds.copy.insetBy(0, 10).moveBy(0, 10).width_(55)).states_([["OFF", Color.hsv(hue, 0.5, 0.5), Color.hsv(hue, 0, 1)], ["ON", Color.white, Color.hsv(hue, 1, 0.675, 0.65)]]).value_(param.val).action_{ |view| param.val_(view.value) };
            StaticText(view, knobBounds.copy.height_(15)).string_(param.name).stringColor_(Color.hsv(hue, 1, 0.35)).acceptsMouse_(false);
            param.addDependant(dependantFunc);
            butt.onClose = { param.removeDependant(dependantFunc) };
          }
          { \phasor } {
            var dependantFunc = { |param, val|
              defer { knob.value = val };
            };
            StaticText(view, knobBounds.copy.height_(15)).string_(param.name).stringColor_(Color.hsv(hue, 1, 0.35)).acceptsMouse_(false);
            knob = Slider(view, knobBounds.copy.insetBy(0, 20)).action_{ |knob| thing.set(param.name, knob.value) }.value_(param.val).background_(Color.hsv(hue, 1, 0.675, 0.2)).knobColor_(Color.hsv(hue, 1, 0.675)).thumbSize_(15);
            param.addDependant(dependantFunc);
            knob.onClose = { param.removeDependant(dependantFunc) };
          }
          {
            // if its a number and not a button or toggle, it's a knob!
            var dependantFunc = { |param, val|
              defer { knob.value = val };
            };
            knob = EZKnob(view, knobBounds, param.name, param.spec, { |knob| thing.set(param.name, knob.value) }, param.val, labelWidth: 100, labelHeight: 15, layout: 'vert').setColors(stringColor: Color.hsv(hue, 1, 0.35), knobColors: [Color.hsv(hue, 0.4, 1), Color.hsv(hue, 1, 0.675), Color.gray(0.5, 0.1), Color.black]);
            knob.knobView.mouseDownAction_{ |view, x, y, modifiers, buttonNumber, clickCount| if (clickCount == 2) {  param.val_(param.spec.default); true }; };
            param.addDependant(dependantFunc);
            knob.onClose = { param.removeDependant(dependantFunc) };
          };

          if (redIndex.notNil) {
            var patch = redPatches[redIndex];
            var spec = \amp.asSpec;
            var hue = patch.fromThing.hue ?? 0;
            var dependantFunc = { |patch, what, val|
              defer {
                patchKnob.value = spec.unmap(val);
                patchView.refresh;
                if (knob.notNil) {
                  knob.setColors(background: Color.hsv(hue, 0.5, 1, patch.amp.curvelin(0, 10, 0.02, 0.4, 4)));
                };
                patchKnob.color_([Color.hsv(hue, 1, 1, patch.amp.curvelin(0, 10, 0.1, 0.3, 4)), Color.hsv(hue, 0.8, 0.5), Color.gray(0.5, 0.1)]);
              };
            };
            patchKnob = Knob(view, Rect(knobBounds.left + 60, knobBounds.top, 20, 20)).action_{
              patch.amp = spec.map(patchKnob.value);
            };
            patch.addDependant(dependantFunc);
            patchKnob.onClose = { patch.removeDependant(dependantFunc) };
            dependantFunc.(patch, \amp, patch.amp);
          };
        } {
          // array control
          var dependantFunc = { |param, val|
            defer { msv.value = param.valNorm };
          };
          var msv = MultiSliderView(view, knobBounds.copy.insetBy(-6, 0)).elasticMode_(true).value_(param.valNorm).reference_(param.spec.unmap(0).dup(param.val.size)).valueThumbSize_(3).colors_(*Color.hsv(hue, 1, 0.675, 0.7).dup(2)).isFilled_(true).background_(Color.hsv(thing.hue, 0.05, 1, 0.8)).action_({ |view|
            param.valNorm = view.value;
          });
          StaticText(view, knobBounds.copy.height_(15)).string_(param.name).stringColor_(Color.hsv(hue, 1, 0.35)).acceptsMouse_(false);
          param.addDependant(dependantFunc);
          msv.onClose = { param.removeDependant(dependantFunc) };
        };
        newKnobPoints[param.name] = point;
        y = y + 1;
        if (y >= columnSpec[x]) {
          y = 0;
          x = x + 1;
        };
      };
      thing.inChannels.do { |i|
        var thisLeft = left - 1;
        var thisTop = top + (i * 30) + 15;
        newInlets = newInlets.add(left@thisTop);
        View(parentView, Rect(thisLeft, thisTop - 1.5, 4, 3)).background_(Color.black);
      };
      thing.outChannels.do { |i|
        var thisLeft = left + width;
        var thisTop = top + (i * 30) + 15;
        newOutlets = newOutlets.add(thisLeft@thisTop);
        View(parentView, Rect(thisLeft - 3, thisTop - 1.5, 4, 3)).background_(Color.black);
      };
      inlets = inlets.add(newInlets);
      outlets = outlets.add(newOutlets);
      knobPoints = knobPoints.add(newKnobPoints);

      Button(view, Rect(2, height - 19, 17, 17)).states_([["⟳", Color.hsv(thing.hue, 1, 0.5)]]).font_(Font.sansSerif(20, true)).action_{
        thing.reset;
      };
      Button(view, Rect(width - 59, height - 19, 17, 17)).states_([["S", Color.hsv(thing.hue, 0.5, 0.5, 0.5), Color.hsv(thing.hue, 0.1, 1.0)], ["S", Color.white, Color.hsv(thing.hue, 1, 0.5)]]).action_({ |view|
        thing.solo(view.value);
      });
      Button(view, Rect(width - 39, height - 19, 17, 17)).states_([["M", Color.hsv(thing.hue, 0.5, 0.5, 0.5), Color.hsv(thing.hue, 0.1, 1.0)], ["M", Color.white, Color.hsv(thing.hue, 1, 0.5)]]).action_{ |view|
        thing.mute(view.value);
      };
      Button(view, Rect(width - 19, height - 19, 17, 17)).states_([["B", Color.hsv(thing.hue, 0.5, 0.5, 0.5), Color.hsv(thing.hue, 0.1, 1.0)], ["B", Color.white, Color.hsv(thing.hue, 1, 0.5)]]).action_{ |view|
        thing.bypass(view.value);
      };
      view;
    };

    adc.do { |y|
      View(w, Rect(0, y - 2, 7, 5)).background_(Color.black);
    };
    dac.do { |y|
      View(w, Rect(winWidth - 7, y - 2, 7, 5)).background_(Color.black).resize_(3);
    };
    things.do { |thing|
      left = left + thing.left;
      thingView.(thing, w, left);
      // TODO: add space if thing to left runs into this
      left = left + (90 * thing.width) + if (thing.left.isNegative) { 30 } { 15 };
    };

    ^w;
  }
}

