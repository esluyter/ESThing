ESBufList {
  var <>name, <>bufs;
  var <w, <topView, <bufViews;

  storeArgs {
    ^[name, bufs.collect { |bufwrap| (name: bufwrap.name, buf: bufwrap.buf.path) }]
  }
  *new { |name = \bufs, list = nil|
    ^super.newCopyArgs(name, []).prInit(list);
  }
  prInit { |list|
    if (list.notNil) {
      this.add(list);
    };
  }

  makeWindow { |winBounds|
    var bounds = winBounds ?? { Rect(650, 80, 400, 800) };
    w !? { w.close };
    w = Window(name, bounds).front;

    topView = View(w, w.bounds.copy.origin_(0@0)).receiveDragHandler_({ |v, x, y|
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
      var sf = SoundFile.new;
      var view = View(topView, Rect(0, i * 85 + 5, topView.bounds.width, 80));
      var nameField;
      sf.openRead(buf.path);
      SoundFileView(view, view.bounds.copy.origin_(0@0)).soundfile_(sf).read(0, sf.numFrames).background_(Color.gray(0.1)).gridColor_(Color.gray(0.16)).peakColor_(Color.gray(0.45)).rmsColor_(Color.gray(0.5));
      sf.close;
      UserView(view, view.bounds.copy.origin_(0@0))
      .drawFunc_{
        var subwidth = topView.bounds.width - 20;
        var seconds = buf.duration;
        var minutes = (seconds / 60).floor.asInteger;
        seconds = seconds - (minutes * 60);
        Pen.stringAtPoint("% ░ %".format(bufwrap[\name] ?? { i.asString }, PathName(buf.path).fileName), 10@2, Font.monospace(bold: true), Color.white);
        Pen.stringAtPoint("%:%    %ch    %Hz     #%".format(minutes, seconds.round(0.1), buf.numChannels, buf.sampleRate.asInteger, buf.bufnum), 10@63, color: Color.gray(0.9));
      }
      .canReceiveDragHandler_(true)
      .receiveDragHandler_{
        var drag = View.currentDrag;
        if (drag.class == String) {
          fork {
            buf.allocRead(drag);
            Server.default.sync;
            buf.updateInfo({defer { this.refresh }});
          };
        };
        if (drag.class == Integer) {
          var item = bufs.removeAt(drag);
          bufs = bufs.insert(i, item);
          defer { this.refresh };
        };
      }
      .beginDragAction_{
        i;
      }
      .mouseDownAction_{ |v, x, y, mods, butt, clicks|
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
  }

  at { |index|
    var res;
    if (index.isInteger) { res = bufs[index] };
    if (index.isKindOf(Symbol)) { res = bufs.select { |buf| buf.name == index } [0] };
    if (res.isNil) {
      ^nil;
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
      bufs = bufs ++ items.collect { |item|
        if (item.class == String) {
          (
            buf: Buffer.read(Server.default, item)
          )
        } {
          item[\buf] = Buffer.read(Server.default, item[\buf])
        }
      };
      Server.default.sync;
      defer { this.refresh };
    };
  }
}