+ ESThing {
  *playFuncSynth { |name, func, params, inChannels = 2, outChannels = 2, top = 0, left = 0, width = 1, midiChannel, srcID|
    ^ESThing(name,
      playFunc: { |thing|
        var funcToPlay = if (func.def.argNames.first == \thing) {
          func.value(thing)
        } {
          func
        };
        thing.params = params ?? { var def = funcToPlay.asSynthDef; this.prMakeParams(def.allControlNames, false, def) };
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
        thing[\bend] = val;
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
      inChannels: inChannels,
      outChannels: outChannels,
      func: func,
      top: top,
      left: left,
      width: width,
      midiChannel: midiChannel,
      srcID: srcID
    )
  }

  *prMakeParams { |controls, hideMidiControls = true, synthDesc|
    var params = controls.select { |control|
      var name = control.name;
      // filter out the controls used internally by mono/poly synths
      var isQ = ['?', \in, \out, \i_out].indexOf(name).notNil;
      var exposeControl = [\gate, \bend, \touch, \freq, \amp].indexOf(name).isNil;
      // filter out any arrayed controls
      var isArray = control.defaultValue.isArray;
      // TODO: have different sort of param and GUI element for arrays
      (exposeControl or: hideMidiControls.not) and: isArray.not and: isQ.not
    } .collect({ |control|
      var spec;
      // look for spec in metadata, otherwise use name.asSpec
      if (synthDesc.notNil and: { (spec = synthDesc.metadata.tryPerform(\at, \specs).tryPerform(\at, control.name)).notNil }) {
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

  *prMakeParamsDefName { |defName, hideMidiControls = true|
    var synthDesc = SynthDescLib.global[defName];
    var params = this.prMakeParams(synthDesc.controls, hideMidiControls, synthDesc);
    ^params;
  }

  *polySynth { |name, defName, args, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, top = 0, left = 0, width = 1, midiChannel, srcID|
    ^ESThing(name,
      playFunc: { |thing|
        thing[\synths] = ();
      },
      noteOnFunc: { |thing, num, vel| // note: vel is mapped 0-1
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
        var freq = thing.midicpsFunc.(num);
        var amp = thing.velampFunc.(vel);
        thing[\synths][num].free;
        thing[\synths][num] = Synth(thing.defName, [out: thing.outbus, in: thing.inbus, doneAction: 2, freq: freq, amp: amp, bend: thing[\bend]] ++ thing.args ++ defaults, thing.group);
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
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args,
      top: top,
      left: left,
      width: width,
      midiChannel: midiChannel,
      srcID: srcID
    )
  }

  *monoSynth { |name, defName, args, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, top = 0, left = 0, width = 1, midiChannel, srcID|
    ^ESThing(name,
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
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
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args,
      top: top,
      left: left,
      width: width,
      midiChannel: midiChannel,
      srcID: srcID
    )
  }

  *droneSynth { |name, defName, args, params, inChannels = 2, outChannels = 2, midicpsFunc, velampFunc, top = 0, left = 0, width = 1, midiChannel, srcID|
    ^ESThing(name,
      initFunc: { |thing|
        thing[\noteStack] = [];
      },
      playFunc: { |thing|
        var defaults = thing.params.collect({ |param| [param.name, param.val] }).flat;
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
      midicpsFunc: midicpsFunc,
      velampFunc: velampFunc,
      defName: defName,
      args: args,
      top: top,
      left: left,
      width: width,
      midiChannel: midiChannel,
      srcID: srcID
    )
  }
}




+ESThingSpace {
  makeWindow { |winBounds|
    var bounds = winBounds ?? { Rect(0, 80, 650, 800) };
    var winWidth = bounds.width;
    var left = 20;
    var inlets = [];
    var outlets = [];
    var knobPoints = [];
    var adc = inChannels.collect { |i| i * 50 + 25 };
    var dac = outChannels.collect { |i| i * 50 + 25 };

    var w = Window("Space", bounds).background_(Color.gray(0.95)).front;

    // for knobs later
    var redPatches = patches.select { |patch| patch.to.index.isKindOf(Symbol) };
    var redParams = redPatches.collect { |patch| patch.toThing.(patch.to.index) };

    var patchView = UserView(w, w.bounds.copy.origin_(0@0)).resize_(5).drawFunc_({ |v|
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
      var top = thing.top + 20 + (30 * thing.index);
      var width = 90 * thing.width;
      var height = thing.params.size / thing.width * 75 + 40;
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
      if (thing.name.notNil) {
        StaticText(view, Rect(5, 3, width, 20)).string_(thing.name).font_(Font.sansSerif(14, true)).stringColor_(Color.hsv(thing.hue, 1, 0.5));
      };
      thing.params.do { |param, i|
        var point = (left + 72 + (90 * (i % thing.width)))@(75 * (i / thing.width).floor + 40 + top);
        var knobBounds = Rect(5 + (90 * (i % thing.width)), 75 * (i / thing.width).floor + 30, 80, 70);
        var knob = EZKnob(view, knobBounds, param.name, param.spec, { |knob| thing.set(param.name, knob.value) }, param.val, labelWidth: 100, labelHeight: 15).setColors(stringColor: Color.hsv(thing.hue, 1, 0.35), knobColors: [Color.hsv(thing.hue, 0.4, 1), Color.hsv(thing.hue, 1, 0.675), Color.gray(0.5, 0.1), Color.black]);
        var dependantFunc = { |param, val|
          defer { knob.value = val };
        };
        // they won't be red anymore, unless modulated by an input
        var redIndex = redParams.indexOf(param);
        var patchKnob;
        if (redIndex.notNil) {
          var patch = redPatches[redIndex];
          var spec = \amp.asSpec;
          var hue = patch.fromThing.hue ?? 0;
          var dependantFunc = { |patch, what, val|
            defer {
              patchKnob.value = spec.unmap(val);
              patchView.refresh;
              knob.setColors(background: Color.hsv(hue, 0.5, 1, patch.amp.curvelin(0, 10, 0.02, 0.4, 4)));
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
        param.addDependant(dependantFunc);
        knob.onClose = { param.removeDependant(dependantFunc) };
        newKnobPoints[param.name] = point;
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
      left = left + (90 * thing.width) + if (thing.left.isNegative) { 30 } { 15 };
    };

    ^w;
  }
}