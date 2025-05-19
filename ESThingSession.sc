ESThingSession {
  var <>tps;

  *new { |tps = #[]|
    ^super.newCopyArgs(tps);
  }

  // is this good?
  doesNotUnderstand { |selector ...args|
    ^tps.perform(selector, *args);
  }

  // sugar: put a thing space directly
  put { |index, ts|
    if (tps.size <= index) {
      tps = tps.extend(index + 1);
    };
    if (tps[index].isNil) {
      // make a new player with default winBounds by index
      tps[index] = ESThingPlayer().play.winBounds_(Rect(500 * index, 80, 650, 800));
      tps[index].presets.makeWindow(Rect(500 * index, 910, 650, 330));
    };
    // sugar: convert array to ESThingSpace
    if (ts.isArray) {
      var argNames = ['things', 'patches', 'initFunc', 'playFunc', 'stopFunc', 'freeFunc', 'inChannels', 'outChannels', 'useADC', 'useDAC', 'target', 'oldSpace'];
      var args = [[], [], nil, nil, nil, nil, 2, 2, true, true, nil, tps[index].ts];
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
    if (ts.isNil) {
      tps[index].stop;
      tps[index].free;
      tps[index].presets.w.close;
      tps[index] = nil;
    } {
      // after all this, if ts not nil put it where it goes
      tps[index].ts = ts;
    };
  }
}


ESThingClient {
  var <session, <>tpIndex;
  var <presetIndices, <fades, <lastPresets;

  *new { |session, tpIndex = 0, presetIndices, fades, lastPresets|
    presetIndices = presetIndices ?? { session.tps.size.collect { 0 } };
    fades = fades ?? { session.tps.size.collect{ true } };
    lastPresets = lastPresets ?? { session.tps.size.collect { nil } };
    ^super.newCopyArgs(session, tpIndex, presetIndices, fades, lastPresets);
  }

  presetIndex { ^presetIndices[tpIndex] }
  presetIndex_ { |val| presetIndices[tpIndex] = val }
  fade { ^fades[tpIndex] }
  fade_ { |val| fades[tpIndex] = val }
  lastPreset { ^lastPresets[tpIndex] }
  lastPreset_ { |val| lastPresets[tpIndex] = val }

  tp { ^session[tpIndex] }
  ts { ^this.tp.ts }

  map { |string, width = 5, height = 5, n = 64|
    // x => knob
    // . => break
    // + => add space
    // - => skip knob
    // A => skip and store in "a"
    // a => use stored knob from earlier
    var verticalMap = string ?? { $x.dup(n).join };
    var map = nil.dup(n);
    {
      var x = 0, y = 0, i = 0;
      var symbols = ();
      var insertKnob = { |i|
        var bank;
        x = x + ((y / height).floor);
        y = y % height;
        bank = (x / height).floor;
        map[(bank * height * width) + (y*height) + (x%height)] = i;
        y = y + 1;
      };
      verticalMap.do { |c|
        switch(c)
        {$x} {
          insertKnob.(i);
          i = i + 1;
        }
        {$-} {
          i = i + 1;
        }
        {$+} {
          y = y + 1;
        }
        {$.} {
          x = x + 1;
          y = 0;
        }
        {
          if (c.isUpper) {
            symbols[c] = i;
            i = i + 1;
          };
          if (c.isLower) {
            insertKnob.(symbols[c.toUpper]);
          };
        };
      };
    }.value;
    ^map;
  }
}


ESThingOSCClient {
  var <osc, <session, <client, <id;
  var <oscfuncs, <dependants;
  var <presetFunc, <>currentPreset = 0;
  var <presetOscFuncs;

  *new { |osc, session, client, id|
    client = client ?? { ESThingClient(session) };
    ^super.newCopyArgs(osc, session, client, id);
  }

  init {
    var n = osc.netAddr;
    var map;

    map = client.map(osc.maps[client.tpIndex]);

    oscfuncs = [];
    dependants = [];
    50.do { |i|
      n.sendMsg("/client", id, "/knobmod_" ++ (i + 1) ++ "/show", false);
      n.sendMsg("/client", id, "/knob_" ++ (i + 1) ++ "/show", false);
      n.sendMsg("/client", id, "/text_" ++ (i + 1), "");
    };
    session.tps.size.do { |i|
      var func = {
        n.sendMsg("/client", id, "/session_fader_" ++ (i + 1), \amp.asSpec.unmap(session.tps[i].amp));
      };
      func.();
      dependants = dependants.add([session.tps[i], func]);
      session.tps[i].addDependant(func);
      oscfuncs = oscfuncs.add(OSCFunc({ |msg|
        if (msg.last == id) {
          session[i].amp = \amp.asSpec.map(msg[1]);
        };
      }, "/session_fader_" ++ (i + 1)));
    };
    n.sendMsg("/client", id, "/session_switch", client.tpIndex);
    oscfuncs = oscfuncs.add(OSCFunc({ |msg|
      if (msg.last == id) {
        if (msg[1] < session.tps.size) {
          if (client.tpIndex != msg[1]) {
            client.tpIndex = msg[1];
            this.free;
            this.init;
          };
        };
      };
    }, "/session_switch"));
    client.ts.params.do { |param, i|
      i = map.indexOf(i);
      if (i.notNil) {
        var func;
        var addr = "/knob_" ++ (i + 1);
        n.sendMsg("/client", id, addr ++ "/color", Color.hsv(param.hue ? param.parentThing.hue, 1, 1).hexString);
        n.sendMsg("/client", id, addr ++ "/show", true);
        n.sendMsg("/client", id, "/text_" ++ (i + 1), param.name);
        n.sendMsg("/client", id, addr, param.valNorm);
        oscfuncs = oscfuncs.add(OSCFunc({ |msg|
          if (msg.last == id) {
            param.valNorm = msg[1]
          };
        }, addr));
        func = { n.sendMsg("/client", id, addr, param.valNorm) };
        dependants = dependants.add([param, func]);
        param.addDependant(func);
      };
    };
    client.ts.modPatches.do { |patch|
      var fromThing = patch.fromThing;
      var toThing = patch.toThing;
      var toParam = toThing.(patch.to.index);
      var i = map.indexOf(client.ts.params.indexOf(toParam));
      if (i.notNil) {
        var func;
        var spec = \amp.asSpec;
        var addr = "/knobmod_" ++ (i + 1);
        n.sendMsg("/client", id, addr ++ "/color", Color.hsv(fromThing.hue, 1, 1).hexString);
        n.sendMsg("/client", id, addr ++ "/show", 1);
        n.sendMsg("/client", id, addr, spec.unmap(patch.amp));
        oscfuncs = oscfuncs.add(OSCFunc({ |msg|
          if (msg.last == id) {
            patch.amp = spec.map(msg[1]);
          };
        }, addr));
        func = { n.sendMsg("/client", id, addr, spec.unmap(patch.amp)) };
        dependants = dependants.add([patch, func]);
        patch.addDependant(func);
      };
    };



    // presets
    presetFunc = {
      n.sendMsg("/client", id, "/switch_1/values", *client.tp.presets.displayNames);
      n.sendMsg("/client", id, "/switch_1", client.tp.presets.displayNames[currentPreset]);
      n.sendMsg("/client", id, "/fader_fade", ControlSpec(0, 120, 6, 0, 1, "sec").unmap(client.tp.presets.defaultTime));
      n.sendMsg("/client", id, "/button_fade", client.fade);
      n.sendMsg("/client", id, "/button_mod", client.tp.presets.affectModAmps);
    };
    presetFunc.();
    client.tp.presets.addDependant(presetFunc);
    presetOscFuncs = [
      OSCFunc({ |msg|
        if (msg.last == id) {
          // change preset
          var i = client.tp.presets.displayNames.collect(_.asSymbol).indexOf(msg[1]);
          if (client.fade.asBoolean.not) {
            client.tp.presets.goNow(i);
          } {
            client.tp.presets.go(i);
          };
          currentPreset = i;
        };
      }, "/switch_1"),
      OSCFunc({ |msg|
        if (msg.last == id) {
          client.tp.presets.affectModAmps = msg[1].asBoolean;
        };
      }, "/button_mod"),
      OSCFunc({ |msg|
        if (msg.last == id) {
          client.fade = msg[1].asBoolean;
        };
      }, "/button_fade"),
      OSCFunc({ |msg|
        if (msg.last == id) {
          client.tp.presets.defaultTime = ControlSpec(0, 120, 6, 0, 1, "sec").map(msg[1]);
        };
      }, "/fader_fade"),
      OSCFunc({ |msg|
        if (msg.last == id) {
          client.tp.presets.capture;
        };
      }, "/button_capture"),
    ];
    /*


    ;
    OSCdef(\undo, { |msg|
      ~undoPreset !? { defer { ~restorePreset.(~undoPreset, if (~fade == 0) { 0 } { ~slider.value }); } };
    }, "/button_undo_preset");
    */

    // randomize xy pad
    {
      var vals, randVals, modAmps, randModAmps, mulFuncs, distY, distX;
      oscfuncs = oscfuncs.add(OSCFunc({ |msg|
        if (msg.last == id) {
          var x, y;
          #x, y = msg[1].asString.interpret;
          distY = (y - 0.5);
          distX = (x - 0.5);
          if ((x == 0.5) and: (y == 0.5)) {
            vals = nil;
          } {
            if (vals == nil) {
              vals = client.tp.includedParams.collect(_.valNorm);
              modAmps = client.tp.includedModPatches.collect(_.ampNorm);
              randVals = client.tp.includedParams.size.collect { 1.0.rand2 };
              randModAmps = client.tp.includedModPatches.size.collect { 1.0.rand2 };
              mulFuncs = max(client.tp.includedParams.size, client.tp.includedModPatches.size).collect { [{ distX }, { distY }].choose };
            };
            client.tp.includedParams.do { |param, i|
              var dist = mulFuncs[i].();
              param.valNorm = blend(vals[i], vals[i] + (randVals[i] * dist.sign), dist.abs * 2);
            };
            defer {
              if (client.tp.presets.affectModAmps) {
                client.tp.includedModPatches.do { |patch, i|
                  var dist = mulFuncs[i].();
                  patch.ampNorm = blend(modAmps[i], modAmps[i] + (randModAmps[i] * dist.sign), dist.abs * 2);
                };
              };
            };
          };
        };
      }, "/xy_2"));
    }.value;
  }

  free {
    oscfuncs.do(_.free);
    oscfuncs = [];
    dependants.do { |arr| arr[0].removeDependant(arr[1]) };
    dependants = [];
    client.tp.presets.removeDependant(presetFunc);
    presetOscFuncs.do(_.free);
  }

  doesNotUnderstand { |selector ...args|
    ^client.perform(selector, *args);
  }
}


ESThingOSC {
  var <session, <>netAddr, <>maps;
  var <clients, <clientIds;
  var <oscFuncs;

  *new { |session, maps = ([]), netAddr|
    netAddr = netAddr ?? { NetAddr("localhost", 8080) };
    ^super.newCopyArgs(session, netAddr, maps, [], []).init;
  }

  init {
    oscFuncs = ();
    oscFuncs.clientIdFunc = OSCFunc({ |msg|
      var id = msg[1].debug("client open");
      if (clientIds.indexOf(id).isNil) {
        var client = ESThingOSCClient(this, session, id: id);
        clientIds = clientIds.add(id);
        clients = clients.add(client);
        fork {
          // make sure interface has loaded
          1.wait;
          client.init;
        };
      };
    }, "/clientId");
    oscFuncs.clientIdCloseFunc = OSCFunc({ |msg|
      var id = msg[1].debug("client close");
      var index = clientIds.indexOf(id);
      if (index.notNil) {
        clientIds.removeAt(index);
        clients.removeAt(index).free;
      }
    }, "/clientIdClose");
    oscFuncs.clientFunc = OSCFunc({ |msg|
      msg.postcs;
    }, "/client");
  }

  free {
    oscFuncs.do(_.free);
    oscFuncs = ();
    clients.do(_.free);
    clients = [];
  }
}