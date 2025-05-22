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