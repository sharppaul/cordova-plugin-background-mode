var exec    = require('cordova/exec'),
    channel = require('cordova/channel');


/*************
 * INTERFACE *
 *************/

/**
 * Starts the background service for listening to bluetooth device.
 *
 * @return [ Void ]
 */
exports.start = function() {
    if (!this._isAndroid) return;
    cordova.exec(null, null, 'AutoStart', 'start', []);
};

/**
 * @private
 *
 * Initialize the plugin.
 *
 * Method should be called after the 'deviceready' event
 * but before the event listeners will be called.
 *
 * @return [ Void ]
 */
exports._pluginInitialize = function() {
    this._isAndroid = device.platform.match(/^android|amazon/i) !== null;
    console.log('AutoStart.js initialized.');
};

// Called before 'deviceready' listener will be called
channel.onCordovaReady.subscribe(function() {
    channel.onCordovaInfoReady.subscribe(function() {
        exports._pluginInitialize();
    });
});
