ESThingSessionTabs {
  var <session, <>left;
  var <bigWin, <presetWin;
  var <bigWinBounds;
  var <buttons;
  var <activeButton = 0;
  var <w, <f;

  *new { |session, left = 0|
    ^super.newCopyArgs(session, left).init.makeWindow;
  }

  init {
    f = { |obj, what|
      if (what == \routing) {
        [bigWin, presetWin].do { |win|
          win !? { win.close };
        };
        this.makeWindow;
      };
    };
    session.addDependant(f);
  }

  free {
    session.removeDependant(f);
  }

  makeWindow {
    var width = 800;
    var thisTps = session.tps.select(_.notNil);
    var spaces = thisTps.collect(_.ts);
    var nTabs = spaces.size + 1;
    var tabWidth = 800 / nTabs;
    var closeBigWin = {
      bigWin !? {
        if (bigWin.isClosed.not) {
          bigWinBounds = bigWin.bounds;
          bigWin.close;
        };
        bigWin = nil;
      };
      presetWin !? {
        presetWin.close;
        presetWin = nil;
      };
    };
    w !? { w.onClose_(nil); w.close };
    w = Window("Tabs", Rect(left, 10, width, 50)).front;
    w.onClose = { [bigWin, presetWin].do { |win|
      win !? { win.close };
    } };
    bigWinBounds = bigWinBounds ?? Rect(left, 90, 800, 800);
    buttons = (nTabs - 1).collect { |i|
      Button(w, Rect(tabWidth * (i + 1), 0, tabWidth, 50)).string_(spaces[i].index).action_ {
        closeBigWin.();
        bigWin = spaces[i].makeWindow(bigWinBounds);
        presetWin = thisTps[i].presets.makeWindow(Rect(bigWin.bounds.left, 920, 800, 330));
        activeButton = i;
      }
    } ++ Button(w, Rect(0, 0, tabWidth, 50)).string_("Session routing").action_ {
      closeBigWin.();
      bigWin = ~session.makeWindow(bigWinBounds, dblClickAction: { |space|
        buttons[spaces.indexOf(space)].action.();
      });
      activeButton = nTabs - 1;
    };

    if (activeButton.notNil) {
      buttons[activeButton].action.();
    };
  }


}