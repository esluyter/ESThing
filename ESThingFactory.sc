+ ESThing {
  *playFuncSynth { |func, params, inChannels = 0, outChannels = 1|
    ^ESThing(
      playFunc: { |thing|
        thing[\synth] = func.play(thing.group, thing.outbus, fadeTime: 0, args: [in: thing.inbus]);
      },
      stopFunc: { |thing|
        thing[\synth].free;
      },
      params: params.asArray,
      inChannels: inChannels,
      outChannels: outChannels
    )
  }

  *polySynth { |defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc|
    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;

    ^ESThing(
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        var freq = midicpsFunc.(num);
        var amp = velampFunc.(vel);
        thing[\synths][num].free;
        thing[\synths][num] = Synth(defName, [out: thing.outbus, in: thing.inbus, freq: freq, amp: amp, bend: thing[\bend]] ++ args ++ defaults, thing.group);
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
      params: params.asArray,
      inChannels: inChannels,
      outChannels: outChannels
    )
  }

  *monoSynth { |defName, args, params, inChannels = 0, outChannels = 1, midicpsFunc, velampFunc|
    midicpsFunc = midicpsFunc ? defaultMidicpsFunc;
    velampFunc = velampFunc ? defaultVelampFunc;

    ^ESThing(
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        thing[\synth] = Synth(defName, [out: thing.outbus, in: thing.inbus, freq: 440, amp: 0, bend: thing[\bend]] ++ args ++ defaults, thing.group);
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var freq = midicpsFunc.(num);
        var amp = velampFunc.(vel);
        thing[\synth].set(\freq, freq, \amp, amp);
        thing[\noteStack] = thing[\noteStack].add(num);
      },
      noteOffFunc: { |thing, num = 69, vel = 0|
        thing[\noteStack].remove(num);
        if (thing[\noteStack].size > 0) {
          thing[\synth].set(\freq, midicpsFunc.(thing[\noteStack].last));
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
      outChannels: outChannels
    )
  }
}