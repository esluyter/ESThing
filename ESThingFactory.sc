+ ESThing {
  *playFuncSynth { |func, params, inChannels = 0, outChannels = 1|
    ^ESThing(
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
      func: func
    )
  }

  *polySynth { |defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc|
    ^ESThing(
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
      params: params.asArray,
      inChannels: inChannels,
      outChannels: outChannels,
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args
    )
  }

  *monoSynth { |defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc|
    ^ESThing(
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
      params: params.asArray,
      inChannels: inChannels,
      outChannels: outChannels,
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args
    )
  }
}