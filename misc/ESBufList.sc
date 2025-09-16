ESBufList {
  var <>name, <>bufs, autoNormalize;
  var <w, <topView, <bufViews, <dropText;
  var <lastNormalizedTo = 0;

  storeArgs {
    ^[name, bufs.collect { |bufwrap| (name: bufwrap.name, buf: bufwrap.buf.path) }]
  }
  *new { |name = \bufs, list = nil, makeWindow = true, autoNormalize = true|
    ^super.newCopyArgs(name, [], autoNormalize).prInit(list, makeWindow);
  }
  prInit { |list, makeWindow|
    if (list.notNil) {
      this.add(list);
    };
    if (makeWindow) {
      this.makeWindow;
    };
  }

  makeWindow { |winBounds|
    var bounds = winBounds ?? { Rect(800, 20, 400, 800) };
    w !? { w.close };
    w = Window(name, bounds).front;

    topView = ScrollView(w, w.bounds.copy.origin_(0@0)).receiveDragHandler_({ |v, x, y|
      var drags;
      if (View.currentDrag.class == String) {
        drags = [View.currentDrag];
      };
      if (View.currentDrag.class == Array) {
        drags = View.currentDrag;
      };
      if (drags.notNil) {
        this.add(drags);
      };
      true;
    }).canReceiveDragHandler_(true);

    this.refresh;
  }

  refresh {
    if (w.isNil) { ^this };

    bufViews.do(_.close);
    bufViews = bufs.collect { |bufwrap, i|
      var buf = bufwrap[\buf];
      var path = "%%_%_BufListTmp.wav".format(PathName.tmp,Date.localtime.stamp,UniqueID.next);
      var view = View(topView, Rect(0, i * 85 + 5, topView.bounds.width, 80));
      var nameField;

      var sfv = SoundFileView(view, view.bounds.copy.origin_(0@0)).background_(Color.gray(0.1)).gridColor_(Color.gray(0.16)).peakColor_(Color.gray(0.45)).rmsColor_(Color.gray(0.5));
      forkIfNeeded({
        buf.write(path,"wav");
        buf.server.sync;
        sfv.readFile(SoundFile(path));
        File.delete(path)
      }, AppClock);

      UserView(view, view.bounds.copy.origin_(0@0))
      .drawFunc_{
        var subwidth = topView.bounds.width - 20;
        var seconds = buf.duration;
        var minutes = (seconds / 60).floor.asInteger;
        seconds = seconds - (minutes * 60);
        Pen.stringAtPoint("% ░ %".format(bufwrap[\name] ?? { i.asString }, PathName(buf.path ? "").fileName), 10@2, Font.monospace(bold: true), Color.white);
        Pen.stringAtPoint("%:%    %ch    %Hz     #%".format(minutes, seconds.round(0.1), buf.numChannels, buf.sampleRate.asInteger, buf.bufnum), 10@63, color: Color.gray(0.9));

        Pen.addRect(Rect(view.bounds.width - 150, 0, 150, view.bounds.height));
        Pen.color = Color.gray(0.5, 0.4);
        Pen.fill;
      }
      .canReceiveDragHandler_(true)
      .receiveDragHandler_{ |view, x, y|
        var drag = View.currentDrag;
        if (drag.class == String) {
          fork {
            buf.allocRead(drag);
            Server.default.sync;
            buf.updateInfo({defer { this.refresh }});
          };
        };
        if (drag.class == Integer) {
          if (x > (view.bounds.width - 150)) {
            var fromBuf = bufs[drag].buf;
            var toBuf = bufs[i].buf;
            toBuf.numFrames = fromBuf.numFrames;
            toBuf.numChannels = fromBuf.numChannels;
            toBuf.sampleRate = fromBuf.sampleRate;
            toBuf.path = fromBuf.path;
            toBuf.alloc;
            fromBuf.copyData(toBuf);
          } {
            var item = bufs.removeAt(drag);
            bufs = bufs.insert(i, item);
          };
          defer { this.refresh };
        };
      }
      .beginDragAction_{
        i;
      }
      .mouseMoveAction_{ |v|
        v.beginDrag(0, 0);
      }
      .mouseDownAction_{ |v, x, y, mods, butt, clicks|
        if (butt == 1) {
          Menu(
            MenuAction("Normalize", {
              bufs[i].buf.normalize;
              defer { this.refresh };
            }),
            MenuAction("Normalize To", {
              var width = 400, height = 50;
              var center = Window.availableBounds.extent / 2;
              NumberBox(nil, Rect(center.x - (width/2), center.y - (height/2), width, height)).font_(Font.monospace(20)).value_(lastNormalizedTo).front.keyDownAction_ { |view, char, mods, unicode, keycode, key|
                switch (key)
                { 16777220 } {
                  // enter
                  if (mods.isShift) {
                    lastNormalizedTo = view.value;
                    bufs[i].buf.normalize(view.value.dbamp);
                    defer { this.refresh };
                    view.close;
                  };
                }
                { 16777216 } {
                  // esc
                  view.close;
                };

              };
            }),
            /*
            MenuAction("+6db", {
              bufs[i].buf.loadToFloatArray(0, -1, { |arr|
                arr = arr * 2;
                bufs[i].buf.loadCollection(arr, 0, {
                  defer { this.refresh };
                });
              })
            })
            */
          ).front;
        };
        if (mods.isAlt) {
          bufs[i].buf.free; bufs.removeAt(i);
          defer { this.refresh };
        };
        if (clicks == 2) {
          nameField = TextField(view, Rect(5, 2, 100, 17)).string_(bufwrap[\name] ? "").font_(Font.monospace).focus.keyDownAction_{ |v, char, mods, uni, kc, key|
            if (key == 16777220) { // enter
              var str = nameField.string;
              if (str == "") { str = nil } { str = str.asSymbol };
              bufwrap[\name] = str;
              nameField.close;
              nameField = nil;
              defer { this.refresh };
            };
            if (key == 16777216) { // esc
              nameField.close;
              nameField = nil;
            };
          }
        };
      };
      view;
    };

    // bit of space at bottom for drag/drop
    dropText !? { dropText.close };
    dropText = StaticText(topView, Rect(0, bufViews.size * 85 + 5, topView.bounds.width, 80)).string_("+ drag files here to add to list\nor double click to make a new buffer").align_(\center).canReceiveDragHandler_(true).receiveDragHandler_(topView.receiveDragHandler).mouseDownAction_{ |v, x, y, mods, buttNum, clickCount|
      if (clickCount == 2) {
        Server.default.waitForBoot {
          this.addEmpty;
          defer { this.refresh };
        };
      };
    };
  }

  addEmpty { |duration = 10|
    bufs = bufs.add((buf: Buffer.alloc(Server.default, Server.default.sampleRate * duration)));
  }

  at { |index|
    var res;
    if (index.isInteger) { res = bufs[index] };
    if (index.isKindOf(Symbol)) { res = bufs.select { |buf| buf.name == index } [0] };
    if (res.isNil) {
      // default to 0
      // so can be used in play func synth
      ^0;
    } {
      ^res[\buf];
    };
  }

  free {
    bufs.do(_.buf.free); bufs = [];
    w !? w.close
  }

  add { |items|
    if (items.isKindOf(Array).not) { items = [items] };
    Server.default.waitForBoot {
      items = items.collect { |item|
        if (item.class == String) {
          (
            buf: Buffer.read(Server.default, item)
          )
        } {
          item[\buf] = Buffer.read(Server.default, item[\buf])
        }
      };
      Server.default.sync;
      if (autoNormalize) {
        items.do { |item| item[\buf].normalize(0.5) };
        Server.default.sync;
      };
      bufs = bufs ++ items;
      defer { this.refresh };
    };
  }
}