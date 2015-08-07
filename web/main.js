(function() {
    "use strict";

    var availableChannels = [];
    var ws;
    var $channels;
    var $logs;

    if(document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else if(["interactive", "complete"].indexOf(document.readyState) !== -1) {
        init();
    }

    function init() {
        ws = new WebSocket("wss://" + document.location.hostname + ":8043");
        $channels = document.getElementById("channels");
        $logs = document.getElementById("logs");

        document.addEventListener('beforeunload', function() {
            ws.close(1001);
        });

        $channels.addEventListener('change', function(e) {
            ws.send(JSON.stringify({
                _type: "listen_request",
                channels: getSelected()
            }));
        });

        ws.onmessage = function(e) {
            var data = JSON.parse(e.data);
            console.log(data);

            if(data._status !== "ok") {
                // Notification!
                console.error(data);
                return;
            }

            switch(data._type.toLowerCase()){
                case "channel_list":
                    availableChannels = data.channels.sort();
                    updateChannels();
                    break;
                case "event":
                    insertLine(data);
                    break;
                case "unknown":
                default:
                    console.warning("unhandled message _type", data._type);
                    break;
            }
        }
    }

    function insertLine(data) {
        var $line = document.createElement('div');

        for(var i in data) {
            if(i[0] === "_") {
                continue;
            }

            $line.appendChild(createElem("span", {
                textContent: data[i],
                class: i,
            }));
        }

        $logs.appendChild($line);
    }

    function createElem(tag, options) {
        var $tag = document.createElement(tag);
        for(var i in options) {
            $tag[i] = options[i];
        }
        return $tag;
    }

    function updateChannels() {
        var selected = getSelected();

        $channels.innerHTML = "";

        $channels.appendChild(createElem("option", {
            textContent: "Choose channels",
            disabled: true,
        }));

        for(var i in availableChannels) {
            $channels.appendChild(createElem("option", {
                value: availableChannels[i],
                textContent: availableChannels[i],
                selected: selected.indexOf(availableChannels[i]) !== -1,
            }));
        }
    }

    function getSelected() {
        return [].map.call($channels.selectedOptions, function(opt) {
            return opt.value;
        });
    }
})();
