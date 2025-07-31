/*Pparam {
  classvar <thing;
  classvar <params;

  *init { |argthing|
    thing = argthing;
    params = [];
  }

  *done {
    var p = params;
    thing = nil;
    params = nil;
    ^p;
  }

  *new { |name, value, spec|
    var thisThing = thing;
    if (thing.isNil) { "No thing specified -- please init first".error };
    params = params ++ ESThingParam(name, spec, val: value);
    ^Pfunc { thisThing.(name).moddedVal };
  }
}*/

Pparam : Pfunc {
  var <>thing;
  var <param, <useBus;

  *findIn { |pattern|
    var patterns = [];
    var findPatterns = { |pattern, class|
      if (pattern.class == class) {
        patterns = patterns.add(pattern);
      };
      pattern.class.instVarNames.do { |name|
        var vals = pattern.slotAt(name);
        vals.asArray.do { |val|
          if (val.isKindOf(PatternProxy)) {
            findPatterns.(val.pattern, class);
          } {
            if (val.isKindOf(Pattern)) {
              findPatterns.(val, class);
            };
          };
        };
      };
    };
    findPatterns.(pattern, Pparam);
    ^patterns
  }

  *new { |name, value, spec, bus = false|
    ^super.new.init(ESThingParam(name, spec, val: value), bus)
  }

  init { |argparam, argbus|
    useBus = argbus;
    param = argparam;
    thing = (moddedVal: param.val);
    nextFunc = {
      var thingParam = thing.(param.name);
      if (useBus) {
        if (thingParam.modBus.isNil) {
          thingParam.playModSynth;
        };
        thingParam.modBus.asMap
      } {
        thingParam.moddedVal
      };
    };
  }
}

/*+ Pattern {
  findSubpatterns { |class|

  }
}*/







Ptest : Pfunc {
  var <>thing;
  var <param;

  *new { |name, value, spec|
    ^super.new.init(ESThingParam(name, spec, val: value))
  }

  init { |argparam|
    param = argparam;
    thing = (moddedVal: param.val);
    nextFunc = { thing.(param.name).moddedVal };
  }
}