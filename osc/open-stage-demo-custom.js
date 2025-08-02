// keep track of connected clients

var clients = []
console.log('custom start')

app.on('open', (data, client)=>{
    send('127.0.0.1', 57120, '/clientId', client.id)
    receive('/SESSION/OPEN', __dirname + '/open-stage-demo-tabs.json', {clientId: client.id})
})

app.on('close', (data, client)=>{
    send('127.0.0.1', 57120, '/clientIdClose', client.id)
})

module.exports = {

    oscInFilter: function(data) {

        var {address, args, host, port} = data

        if (address === '/client') {
            var clientId = args.shift().value
            args = args.map((x) => x.value)
            /*
            var widgetId = args[1].value
            var value = args[2].value

            console.log([clientId, widgetId, value])

            receive('/SET', widgetId, value, {clientId: clientId, sync: false})
            */
           console.log(args)
            receive(...args, {clientId: clientId, sync: false})
            return // bypass original message
        }

        return {address, args, host, port}

    },

    oscOutFilter: function(data) {

        var {address, args, host, port, clientId} = data

        if (address === '/clientRaw') {
            args[0] = '/' + args[0].value
            console.log(args)
            console.log([host, port])
            send(host, port, ...args, clientId)
            return
        }

        return {address, args, host, port, clientId}
    }
}
