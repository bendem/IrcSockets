(function() {
    "use strict";

    var availableChannels = [];
    var ws;
    var retries = 0;
    var $channels;
    var $logs;

    if(document.readyState === "loading") {
        document.addEventListener("DOMContentLoaded", init);
    } else {
        init();
    }

    function init() {
        initWebSocket();
        $channels = document.getElementById("channels");
        $logs = document.getElementById("logs");

        document.addEventListener("beforeunload", function() {
            ws.close(1001);
        });

        $channels.addEventListener("change", function() {
            sendChannels();
        });
    }

    function initWebSocket() {
        if(connecting) {
            return;
        }
        var connecting = true;
        ws = new WebSocket("wss://" + document.location.hostname + ":8043");

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
                    console.warn("unhandled message _type", data._type);
                    break;
            }
        };

        ws.onopen = function() {
            console.log("connected");
            connecting = false;
            retries = 0;

            var channels = getSelected();
            if(channels.length) {
                sendChannels();
            }
        };

        ws.onerror = function(e) {
            console.error("Connection error", e);
        };

        ws.onclose = function(e) {
            if(e.code === 1001 || retries >= 3) {
                return;
            }

            connecting = false;
            // Notification!
            var time = ++retries * 5000;
            console.warn("Connection closed, retrying in " + time + "ms", e.code);
            setTimeout(initWebSocket, time);
        };
    }

    function insertLine(data) {
        var moveToBottom = false;
        if($logs.offsetTop + $logs.offsetHeight < window.innerHeight + window.pageYOffset) {
            moveToBottom = true;
        }

        var $line = document.createElement("div");

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

        if(moveToBottom) {
            window.scrollBy(0, $line.offsetHeight);
        }
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

    function sendChannels() {
        ws.send(JSON.stringify({
            _type: "listen_request",
            channels: getSelected()
        }));
    }

    function getSelected() {
        return [].map.call($channels.selectedOptions, function(opt) {
            return opt.value;
        });
    }
})();
