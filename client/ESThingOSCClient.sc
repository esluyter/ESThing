ESThingOSCClient {
  var <osc, <session, <clients, <clientIds, <id;
  var <oscfuncs, <dependants;
  var <>currentPreset = 0;

  *new { |osc, session, clients, clientIds, id|
    // two clients, one for each side of interface, called "left" and "right"
    clientIds = clientIds ?? { [\left, \right] };
    clients = clients ?? { { ESThingClient(session) }.dup(2) };
    ^super.newCopyArgs(osc, session, clients, clientIds, id);
  }

  init {
    var n = osc.netAddr;

    oscfuncs = [];
    dependants = [];

    clients.do { |client, i|
      var clientId = clientIds[i];
      var map = client.map(osc.maps[client.tpIndex]);
      var presetFunc;

      // hide all knobs and buttons
      50.do { |i|
        n.sendMsg("/client", id, "/%_knobmod_%/show".format(clientId, i + 1), false);
        n.sendMsg("/client", id, "/%_knob_%/show".format(clientId, i + 1), false);
        n.sendMsg("/client", id, "/%_button_%/show".format(clientId, i + 1), false);
        n.sendMsg("/client", id, "/%_multislider_%/show".format(clientId, i + 1), false);
        n.sendMsg("/client", id, "/%_text_%".format(clientId, i + 1), "");
      };

      // set the session fader levels and add handlers for changes
      session.tps.size.do { |i|
        var tp = session.tps[i];
        if (tp.notNil) {
          var func = {
            n.sendMsg("/client", id, "/%_session_fader_%".format(clientId, i + 1), \amp.asSpec.unmap(session.tps[i].amp));
          };
          func.();
          dependants = dependants.add([session.tps[i], func]);
          session.tps[i].addDependant(func);
          oscfuncs = oscfuncs.add(OSCFunc({ |msg|
            if (msg.last == id) {
              session[i].amp = \amp.asSpec.map(msg[1]);
            };
          }, "/%_session_fader_%".format(clientId, i + 1)));
        };
      };

      // set the session switch to the current TP and add handlers for changes
      n.sendMsg("/client", id, "/%_session_switch".format(clientId), client.tpIndex);
      oscfuncs = oscfuncs.add(OSCFunc({ |msg|
        if (msg.last == id) {
          if (client.tpIndex != msg[1]) {
            client.tpIndex = msg[1];
            this.free;
            this.init;
          };
        };
      }, "/%_session_switch".format(clientId)));

      // only do the rest of the init if there's a TP in the selected slot
      if (client.tp.notNil) {
        // set knob values, colors, and labels, and register handlers for changes
        client.ts.params.do { |param, i|
          i = map.indexOf(i);
          if (i.notNil) {
            var func;
            var widget = switch (param.spec.units)
            { \push } { \button }
            { \toggle } { \button }
            { if (param.val.size == 0) { \knob } { \multislider } };
            var addr = "/%_%_%".format(clientId, widget, i + 1);
            if (widget == \button) {
              n.sendMsg("/client", id, addr ++ "/mode", param.spec.units)
            };
            n.sendMsg("/client", id, addr ++ "/color", Color.hsv(param.hue ? param.parentThing.hue, 1, 1).hexString);
            n.sendMsg("/client", id, addr ++ "/show", true);
            n.sendMsg("/client", id, "/%_text_%".format(clientId, i + 1), param.name);
            if (widget == \multislider) {
              n.sendMsg("/client", id, addr ++ "/valueLength", param.val.size);
            };
            n.sendMsg("/client", id, addr, *param.valNorm);
            oscfuncs = oscfuncs.add(OSCFunc({ |msg|
              if (msg.last == id) {
                param.valNorm = msg[1].asString.interpret
              };
            }, addr));
            func = { n.sendMsg("/client", id, addr, *param.valNorm) };
            dependants = dependants.add([param, func]);
            param.addDependant(func);
          };
        };


        // set mod knob values and colors, and register handlers for changes
        client.ts.modPatches.do { |patch|
          var fromThing = patch.fromThing;
          var toThing = patch.toThing;
          var toParam = toThing.(patch.to.index);
          var i = map.indexOf(client.ts.params.indexOf(toParam));
          if (i.notNil) {
            var func;
            var spec = \amp.asSpec;
            var addr = "/%_knobmod_%".format(clientId, i + 1);
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
          n.sendMsg("/client", id, "/%_switch_presets/values".format(clientId), *client.tp.presets.displayNames);
          n.sendMsg("/client", id, "/%_switch_presets".format(clientId), client.tp.presets.displayNames[currentPreset]);
          n.sendMsg("/client", id, "/%_fader_time".format(clientId), ControlSpec(0, 120, 6, 0, 1, "sec").unmap(client.tp.presets.defaultTime));
          n.sendMsg("/client", id, "/%_button_fade".format(clientId), client.fade);
          n.sendMsg("/client", id, "/%_button_mod".format(clientId), client.tp.presets.affectModAmps);
        };
        presetFunc.();
        client.tp.presets.addDependant(presetFunc);
        dependants = dependants.add([client.tp.presets, presetFunc]);
        oscfuncs = oscfuncs ++ [
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
          }, "/%_switch_presets".format(clientId)),
          OSCFunc({ |msg|
            if (msg.last == id) {
              client.tp.presets.affectModAmps = msg[1].asBoolean;
            };
          }, "/%_button_mod".format(clientId)),
          OSCFunc({ |msg|
            if (msg.last == id) {
              client.fade = msg[1].asBoolean;
            };
          }, "/%_button_fade".format(clientId)),
          OSCFunc({ |msg|
            if (msg.last == id) {
              client.tp.presets.defaultTime = ControlSpec(0, 120, 6, 0, 1, "sec").map(msg[1]);
            };
          }, "/%_fader_time".format(clientId)),
          OSCFunc({ |msg|
            if (msg.last == id) {
              client.tp.presets.capture;
            };
          }, "/%_button_capture".format(clientId)),
        ];


        // randomize xy pad
        {
          var func = client.tp.makeXYFunc;
          oscfuncs = oscfuncs.add(OSCFunc({ |msg|
            msg.postln;
            if (msg.last == id) {
              var x, y;
              #x, y = msg[1].asString.interpret;
              func.(x, y);
            };
          }, "/%_xy_rand".format(clientId).postln));
        }.value;

        /*
        ;
        OSCdef(\undo, { |msg|
        ~undoPreset !? { defer { ~restorePreset.(~undoPreset, if (~fade == 0) { 0 } { ~slider.value }); } };
        }, "/button_undo_preset");
        */
      };
    };
  }

  free {
    oscfuncs.do(_.free);
    oscfuncs = [];
    dependants.do { |arr| arr[0].removeDependant(arr[1]) };
    dependants = [];
    //client.tp.presets.removeDependant(presetFunc);
  }

  doesNotUnderstand { |selector ...args|
    //^client.perform(selector, *args);
  }
}