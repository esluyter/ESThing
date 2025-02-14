+ ESThing {
  *playFuncSynth { |name, func, params, inChannels = 0, outChannels = 1, top = 0, left = 0, width = 1|
    ^ESThing(name,
      playFunc: { |thing|
        var funcToPlay = if (thing.func.def.argNames.first == \thing) {
          thing.func.value(thing)
        } {
          thing.func
        };
        thing[\synth] = funcToPlay.play(thing.group, thing.outbus, fadeTime: 0, args: [in: thing.inbus]);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: params.asArray,
      inChannels: inChannels,
      outChannels: outChannels,
      func: func,
      top: top,
      left: left,
      width: width
    )
  }

  *prMakeParams { |defName|
    var synthDesc = SynthDescLib.global[defName];
    var params = synthDesc.controls.select { |control|
      var name = control.name.asSymbol;
      // filter out the controls used internally by mono/poly synths
      var exposeControl = ['?', \gate, \bend, \touch, \freq, \in, \out, \amp].indexOf(name).isNil;
      // filter out any arrayed controls
      var isArray = control.defaultValue.isArray;
      // TODO: have different sort of param and GUI element for arrays
      exposeControl and: isArray.not
    } .collect({ |control|
      var spec;
      // look for spec in metadata, otherwise use name.asSpec
      if ((spec = synthDesc.metadata.tryPerform(\at, \specs).tryPerform(\at, control.name)).notNil) {
        spec = spec.asSpec
      } {
        spec = control.name.asSpec;
      };
      // filter out nils
      spec = spec ?? { ControlSpec() };
      spec = spec.copy.default_(control.defaultValue);
      ESThingParam(control.name, spec);
    });
    ^params;
  }

  *polySynth { |name, defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc, top = 0, left = 0, width = 1|
    ^ESThing(name,
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing[\synths][num].free;
        thing[\synths][num] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, freq: freq, amp: amp, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
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
      params: params ?? { this.prMakeParams(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args,
      top: top,
      left: left,
      width: width
    )
  }

  *monoSynth { |name, defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc, top = 0, left = 0, width = 1|
    ^ESThing(name,
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        thing[\synth] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, freq: 440, amp: 0, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing[\synth].set(\freq, freq, \amp, amp);
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num, vel|
        thing[\noteStack].remove(num);
        if (thing[\noteStack].size > 0) {
          thing[\synth].set(\freq, thing.midicpsFunc.(thing[\noteStack].last));
        } {
          thing[\synth].set(\amp, 0)
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
        if (num == thing[\noteStack].last) {
          thing[\synth].set(\touch, val);
        };
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: params ?? { this.prMakeParams(defName) },
      inChannels: inChannels,
      outChannels: outChannels,
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args,
      top: top,
      left: left,
      width: width
    )
  }
}