

//            ESThingPresets
//         saves snapshots of a thing space
//           editable via GUI


ESThingPresets {
  // an ESThingPlayer
  var <tp,
  // array of presets as events
  <presetArr,
  // if no time is specified in the preset, how long to fade
  <defaultTime,
  // whether to affect modulation amounts or just param values
  <affectModAmps,
  // callback for when preset is restored
  <>restoreCallback,
  // default save path is the now executing dir
  <>defaultPath;

  defaultTime_ { |val|
    defaultTime = val;
    this.changed(\defaultTime, val);
  }

  affectModAmps_ { |val|
    affectModAmps = val;
    this.changed(\affectModAmps, val);
  }

  storeArgs { ^[presetArr, defaultTime, affectModAmps] }
  *new { |tp, presetArr = ([]), defaultTime = 1, affectModAmps = true, restoreCallback|
    var defaultPath;
    if (thisProcess.nowExecutingPath.notNil) {
      defaultPath = PathName(thisProcess.nowExecutingPath).pathOnly;
      if (File.exists(defaultPath +/+ "presets")) {
        defaultPath = defaultPath +/+ "presets";
      };
    };
    ^super.newCopyArgs(tp, presetArr, defaultTime, affectModAmps, restoreCallback, defaultPath);
  }

  presetArr_ { |val|
    presetArr = val;
    this.changed(\presets);
  }
  at { |index|
    ^presetArr.at(index);
  }
  put { |index, presetEvent|
    if (index >= presetArr.size) {
      presetArr = presetArr.add(presetEvent);
    } {
      presetArr[index] = presetEvent;
    };
    this.changed(\presets);
  }
  insert { |index, presetEvent|
    if (index >= presetArr.size) {
      presetArr = presetArr.add(presetEvent);
    } {
      presetArr.insert(index, presetEvent);
    };
    this.changed(\presets);
  }
  removeAt { |index|
    presetArr.removeAt(index);
    this.changed(\presets);
  }
  move { |fromIndex, toIndex|
    toIndex = min(max(toIndex, 0), presetArr.size - 1);
    presetArr.move(fromIndex, toIndex);
    this.changed(\presets);
  }
  size { |index|
    ^presetArr.size;
  }

  // return the current situation in the form of a preset
  currentState {
    ^(params: tp.ts.params.collect { |param|
      [param.parentThing.name, param.name, param.val]
    }, modAmps: tp.ts.modPatches.collect { |patch|
      [patch.to.thingIndex, patch.to.index, patch.amp]
    }, date: Date.localtime);
  }

  // insert current situation as preset
  capture { |index = inf|
    var preset = this.currentState;
    this.insert(index, preset);
  }

  // preset arg can be either event or index #
  restorePreset { |preset, dur = 0|
    if (preset.isInteger) { preset = presetArr[preset] };
    restoreCallback.();
    preset[\params].do { |arr|
      try {
        tp.ts[arr[0]][arr[1]].fadeTo(arr[2], dur);
      };
    };
    if (affectModAmps) {
      preset[\modAmps].do { |arr|
        tp.ts.modPatches.do { |patch|
          if ((patch.to.thingIndex == arr[0]) and: patch.to.index == arr[1]) {
            patch.fadeTo(arr[2], dur);
          };
        };
      };
    };
  }

  // shortcuts to go at a particular index
  go { |index = 0|
    var preset = presetArr[index];
    var dur = preset[\time] ?? { defaultTime };
    this.restorePreset(preset, dur);
  }
  goNow { |index = 0|
    this.restorePreset(presetArr[index], 0);
  }

  // list of display names for GUI
  displayNames {
    ^presetArr.collect { |preset, i|
      var name = preset[\name] ?? { preset[\date] !? { preset[\date].format(/*%d/%m/%y %I:%M:%S*/ "%m/%d %I:%M %p") } } ? "";
      "%: %".format(i, name);
    }
  }


  // gross GUI code
  makeWindow { |bounds|
    var view;
    var list, populateList, textView, setTextViewString;
    var slider, goButt, goNowButt, modBox, saveEditButt, captureButt, deleteButt, moveUpButt, moveDownButt, writeButt, readButt;
    var dependantFunc;
    var w;

    bounds = bounds ?? Rect(0, 910, 650, 330);

    w = Window("Space Presets", bounds).front;
    view = View(w, Rect(0, 30, 650, 300)).resize_(5);

    list = ListView(w, Rect(0, 0, 200, w.bounds.height)).resize_(4).action_({
      if (list.items.size > 0) {
        setTextViewString.(tp.presets[list.value]);
      } {
        textView.string_("");
      };
    });
    populateList = {
      var value = list.value ?? 0;
      list.items_(tp.presets.displayNames);
      list.valueAction = min(value, list.items.size - 1);
    };
    writeButt = Button(w, Rect(210, 5, 130, 20)).string_("Save to file").action_{
      FileDialog({ |path|
        var file = File.open(path[0], "w");
        file.write(this.storeArgs.asCompileString);
        file.close;
      }, fileMode: 0, acceptMode: 1, path: defaultPath);
    };
    readButt = Button(w, Rect(350, 5, 130, 20)).string_("Open file").action_{
      FileDialog({ |path|
        var arr = File.readAllString(path[0]).interpret;
        if (arr.isArray) {
          this.presetArr = arr[0];
          this.defaultTime = arr[1];
          this.affectModAmps = arr[2];
        };
      }, path: defaultPath)
    };

    slider = EZSlider(view, Rect(200, 0, 400, 20), "default time", ControlSpec(0, 120, 6, 0, 1, "sec"), labelWidth: 100, unitWidth: 25).value_(tp.presets.defaultTime).action_{
      tp.presets.defaultTime = slider.value
    };
    goButt = Button(view, Rect(210, 25, 100, 25)).string_("Fade preset").action_{
      tp.presets.go(list.value);
      list.valueAction_(list.value + 1 % list.items.size);
    };
    goNowButt = Button(view, Rect(320, 25, 130, 25)).string_("Load immediately").action_{
      tp.presets.goNow(list.value);
      list.valueAction_(list.value + 1 % list.items.size);
    };
    modBox = CheckBox(view, Rect(470, 25, 20, 20)).value_(tp.presets.affectModAmps).action_{
      tp.presets.affectModAmps = modBox.value;
    };
    StaticText(view, Rect(490, 25, 200, 20)).string_("Affect modulation amps");
    saveEditButt = Button(view, Rect(560, view.bounds.height - 30, 80, 25)).resize_(7).string_("Save edit").action_{
      tp.presets[list.value] = textView.string.interpret;
    };
    captureButt = Button(view, Rect(210, view.bounds.height - 30, 120, 25)).resize_(7).string_("Capture preset").action_{
      tp.presets.capture;
    };
    deleteButt = Button(view, Rect(340, view.bounds.height - 30, 120, 25)).resize_(7).string_("Delete preset").action_{
      tp.presets.removeAt(list.value);
    };
    moveUpButt = Button(view, Rect(480, view.bounds.height - 30, 35, 25)).resize_(7).string_("⇑").action_{
      tp.presets.move(list.value, list.value - 1);
      list.valueAction = list.value - 1;
    };
    moveDownButt = Button(view, Rect(515, view.bounds.height - 30, 35, 25)).resize_(7).string_("⇓").action_{
      tp.presets.move(list.value, list.value + 1);
      list.valueAction = list.value + 1;
    };
    textView = CodeView(view, Rect(210, 55, view.bounds.width - 220, view.bounds.height - 90)).resize_(5).background_(Color.clear);
    setTextViewString = { |preset|
      textView.string_("(\n  params: [\n%\n  ],\n\n  modAmps: [\n%\n  ],\n\n  time: %,\n  name: %,\n\n  date: %\n)".format(preset[\params].collect { |paramArr|
        "    [%, %, %]".format(*paramArr.collect(_.asCompileString))
      }.join(",\n"), preset[\modAmps].collect { |paramArr|
        "    [%, %, %]".format(*paramArr.collect(_.asCompileString))
      }.join(",\n"), preset[\time].asCompileString, preset[\name].asCompileString, preset[\date].asCompileString));
    };

    populateList.();

    dependantFunc = { |obj, what, val|
      if (what == \presets) {
        defer { populateList.() };
      };
      if (what == \defaultTime) {
        defer { slider.value = val };
      };
      if (what == \affectModAmps) {
        defer { modBox.value = val };
      };
    };
    tp.presets.addDependant(dependantFunc);
    w.onClose_{ tp.presets.removeDependant(dependantFunc); };

    ^w;
  }
}