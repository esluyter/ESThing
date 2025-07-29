ESPhasor {
  *trig { |freq, id|
    var updatePhase = "eSPhasor%Phase".format(id).asSymbol.kr(0);
    var updateTrig = "eSPhasor%Trig".format(id).asSymbol.tr(0);
    var phase = Phasor.ar(updateTrig, SampleDur.ir * freq, resetPos: updatePhase);
    SendReply.kr(Impulse.kr(24), '/ESPhasor', phase, id);
    ^[phase, updateTrig]
  }

  *env { |freq, id, fadeDur = 0.01|
    var phase, trig, env;
    #phase, trig = ESPhasor.trig(freq, id);
    env = 1 - Env.linen(fadeDur, 0, fadeDur).ar(0, trig);
    phase = DelayN.ar(phase, fadeDur, fadeDur);
    ^[phase, env]
  }

  *buf { |buf, id, rate = 1|
    var phase, env;
    #phase, env = ESPhasor.env(BufDur.kr(buf).reciprocal * rate, 1);
    ^BufRd.ar(1, buf, phase * BufFrames.kr(buf)) * env
  }

  *new { |freq, id|
    ^ESPhasor.trig(freq, id)[0]
  }
}