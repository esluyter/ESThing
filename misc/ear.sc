

// ew, add metadata to control spec?
ESControlSpec : ControlSpec {
  var <>meta;
  storeArgs { ^[minval, maxval, warp.asSpecifier, step, default, units, grid, meta] }
  *newFrom { arg similar;
		^this.new(similar.minval, similar.maxval, similar.warp.asSpecifier,
			similar.step, similar.default, similar.units, nil).meta_(similar.tryPerform(\meta))
	}
}

+ Symbol {
  ear { |val, spec, meta, lag|
    spec = spec ? this;
    // double asSpec necessary because \blah.asSpec -> nil
    spec = ESControlSpec.newFrom(spec.asSpec.asSpec);
    if (meta.notNil) {
      spec.meta = meta;
    };
    ^this.ar(val, lag, spec: spec);
  }
  ekr { |val, spec, meta, lag|
    spec = spec ? this;
    spec = ESControlSpec.newFrom(spec.asSpec.asSpec);
    if (meta.notNil) {
      spec.meta = meta;
    };
    ^this.kr(val, lag, spec: spec);
  }
}

