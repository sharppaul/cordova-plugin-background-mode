/*
  Copyright 2013-2017 appPlant GmbH

  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
*/

#import "APPMethodMagic.h"
#import "APPBackgroundMode.h"
#import <Cordova/CDVAvailability.h>

@implementation APPBackgroundMode

#pragma mark -
#pragma mark Constants

NSString* const kAPPBackgroundJsNamespace = @"cordova.plugins.backgroundMode";
NSString* const kAPPBackgroundEventActivate = @"activate";
NSString* const kAPPBackgroundEventDeactivate = @"deactivate";


#pragma mark -
#pragma mark Life Cycle

/**
 * Called by runtime once the Class has been loaded.
 * Exchange method implementations to hook into their execution.
 */
+ (void) load
{
    [self swizzleWKWebViewEngine];
}

/**
 * Initialize the plugin.
 */
- (void) pluginInitialize
{
    enabled = NO;
    [self configureAudioPlayer];
    [self configureAudioSession];
    [self observeLifeCycle];
}

/**
 * Register the listener for pause and resume events.
 */
- (void) observeLifeCycle
{
    NSNotificationCenter* listener = [NSNotificationCenter
                                      defaultCenter];

        [listener addObserver:self
                     selector:@selector(keepAwake)
                         name:UIApplicationDidEnterBackgroundNotification
                       object:nil];

        [listener addObserver:self
                     selector:@selector(stopKeepingAwake)
                         name:UIApplicationWillEnterForegroundNotification
                       object:nil];

        [listener addObserver:self
                     selector:@selector(handleAudioSessionInterruption:)
                         name:AVAudioSessionInterruptionNotification
                       object:nil];
}

#pragma mark -
#pragma mark Interface

/**
 * Enable the mode to stay awake
 * when switching to background for the next time.
 */
- (void) enable:(CDVInvokedUrlCommand*)command
{
    if (enabled)
        return;

    enabled = YES;
    [self execCallback:command];
}

/**
 * Disable the background mode
 * and stop being active in background.
 */
- (void) disable:(CDVInvokedUrlCommand*)command
{
    if (!enabled)
        return;

    enabled = NO;
    [self stopKeepingAwake];
    [self execCallback:command];
}

#pragma mark -
#pragma mark Core

/**
 * Keep the app awake.
 */
- (void) keepAwake
{
    if (!enabled)
        return;

    [audioPlayer play];
    [self fireEvent:kAPPBackgroundEventActivate];
}

/**
 * Let the app going to sleep.
 */
- (void) stopKeepingAwake
{
    if (TARGET_IPHONE_SIMULATOR) {
        NSLog(@"BackgroundMode: On simulator apps never pause in background!");
    }

    if (audioPlayer.isPlaying) {
        [self fireEvent:kAPPBackgroundEventDeactivate];
    }

    [audioPlayer pause];
}

/**
 * Configure the audio player.
 */
- (void) configureAudioPlayer
{
    NSString* path = [[NSBundle mainBundle]
                      pathForResource:@"appbeep" ofType:@"wav"];

    NSURL* url = [NSURL fileURLWithPath:path];


    audioPlayer = [[AVAudioPlayer alloc]
                   initWithContentsOfURL:url error:NULL];

    audioPlayer.volume        = 0;
    audioPlayer.numberOfLoops = -1;
};

/**
 * Configure the audio session.
 */
- (void) configureAudioSession
{
    AVAudioSession* session = [AVAudioSession
                               sharedInstance];

    // Don't activate the audio session yet
    [session setActive:NO error:NULL];

    // Play music even in background and dont stop playing music
    // even another app starts playing sound
    [session setCategory:AVAudioSessionCategoryPlayback
                   error:NULL];

    // Active the audio session
    [session setActive:YES error:NULL];
};

#pragma mark -
#pragma mark Helper

/**
 * Simply invokes the callback without any parameter.
 */
- (void) execCallback:(CDVInvokedUrlCommand*)command
{
    CDVPluginResult *result = [CDVPluginResult
                               resultWithStatus:CDVCommandStatus_OK];

    [self.commandDelegate sendPluginResult:result
                                callbackId:command.callbackId];
}

/**
 * Restart playing sound when interrupted by phone calls.
 */
- (void) handleAudioSessionInterruption:(NSNotification*)notification
{
    [self fireEvent:kAPPBackgroundEventDeactivate];
    [self keepAwake];
}

/**
 * Find out if the app runs inside the webkit powered webview.
 */
+ (BOOL) isRunningWebKit
{
    return IsAtLeastiOSVersion(@"8.0") && NSClassFromString(@"CDVWKWebViewEngine");
}

/**
 * Method to fire an event with some parameters in the browser.
 */
- (void) fireEvent:(NSString*)event
{
    NSString* active =
    [event isEqualToString:kAPPBackgroundEventActivate] ? @"true" : @"false";

    NSString* flag = [NSString stringWithFormat:@"%@._isActive=%@;",
                      kAPPBackgroundJsNamespace, active];

    NSString* depFn = [NSString stringWithFormat:@"%@.on%@();",
                       kAPPBackgroundJsNamespace, event];

    NSString* fn = [NSString stringWithFormat:@"%@.fireEvent('%@');",
                    kAPPBackgroundJsNamespace, event];

    NSString* js = [NSString stringWithFormat:@"%@%@%@", flag, depFn, fn];

    [self.commandDelegate evalJs:js];
}

#pragma mark -
#pragma mark Swizzling

/**
 * Method to swizzle.
 */
+ (NSString*) wkProperty
{
    NSString* str = @"X2Fsd2F5c1J1bnNBdEZvcmVncm91bmRQcmlvcml0eQ==";
    NSData* data  = [[NSData alloc] initWithBase64EncodedString:str options:0];

    return [[NSString alloc] initWithData:data encoding:NSUTF8StringEncoding];
}

/**
 * Swizzle some implementations of CDVWKWebViewEngine.
 */
+ (void) swizzleWKWebViewEngine
{
    if (![self isRunningWebKit])
        return;

    Class wkWebViewEngineCls = NSClassFromString(@"CDVWKWebViewEngine");
    SEL selector = NSSelectorFromString(@"createConfigurationFromSettings:");

    SwizzleSelectorWithBlock_Begin(wkWebViewEngineCls, selector)
    ^(CDVPlugin *self, NSDictionary *settings) {
        id obj = ((id (*)(id, SEL, NSDictionary*))_imp)(self, _cmd, settings);

        [obj setValue:[NSNumber numberWithBool:YES]
               forKey:[APPBackgroundMode wkProperty]];

        [obj setValue:[NSNumber numberWithBool:NO]
               forKey:@"requiresUserActionForMediaPlayback"];

        return obj;
    }
    SwizzleSelectorWithBlock_End;
}


- (BOOL)do_open:(NSString *)pref {
	if ([[UIApplication sharedApplication] openURL:[NSURL URLWithString:pref]]) {
		return YES;
	} else {
		return NO;
	}
}

- (void)open:(CDVInvokedUrlCommand*)command
{
	CDVPluginResult* pluginResult = nil;
	NSString* key = [command.arguments objectAtIndex:0];
	NSString* prefix = @"App-Prefs:";
	BOOL result = NO;

	/*if(SYSTEM_VERSION_LESS_THAN(@"11.3")){
        prefix = @"app-settings:";
    }
  */
	
    if ([key isEqualToString:@"application_details"]) {
        result = [self do_open:UIApplicationOpenSettingsURLString];
    }
	else if ([key isEqualToString:@"settings"]) {
		result = [self do_open:prefix];
	}
	else if ([key isEqualToString:@"about"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=About"]];
	}
	else if ([key isEqualToString:@"accessibility"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=ACCESSIBILITY"]];
	}
	else if ([key isEqualToString:@"account"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=ACCOUNT_SETTINGS"]];
	}
	else if ([key isEqualToString:@"airplane_mode"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=AIRPLANE_MODE"]];
	}
	else if ([key isEqualToString:@"autolock"]) {
		if (SYSTEM_VERSION_LESS_THAN(@"10.0")) {
			result = [self do_open:[prefix stringByAppendingString:@"root=General&path=AUTOLOCK"]];
		}
		else {
			result = [self do_open:[prefix stringByAppendingString:@"root=DISPLAY&path=AUTOLOCK"]];
		}
	}
	else if ([key isEqualToString:@"display"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Brightness"]];
	}
	else if ([key isEqualToString:@"bluetooth"]) {
		if (SYSTEM_VERSION_LESS_THAN(@"9.0")) {
			result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Bluetooth"]];
		}
		else {
			result = [self do_open:[prefix stringByAppendingString:@"root=Bluetooth"]];
		}
	}
	else if ([key isEqualToString:@"castle"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=CASTLE"]];
	}
	else if ([key isEqualToString:@"cellular_usage"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=USAGE/CELLULAR_USAGE"]];
	}
	else if ([key isEqualToString:@"configuration_list"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=ManagedConfigurationList"]];
	}
	else if ([key isEqualToString:@"date"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=DATE_AND_TIME"]];
	}
	else if ([key isEqualToString:@"facetime"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=FACETIME"]];
	}
	else if ([key isEqualToString:@"settings"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General"]];
	}
	else if ([key isEqualToString:@"tethering"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=INTERNET_TETHERING"]];
	}
	else if ([key isEqualToString:@"music"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=MUSIC"]];
	}
	else if ([key isEqualToString:@"music_equalizer"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=MUSIC&path=EQ"]];
	}
	else if ([key isEqualToString:@"music_volume"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=MUSIC&path=VolumeLimit"]];
	}
	else if ([key isEqualToString:@"keyboard"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Keyboard"]];
	}
	else if ([key isEqualToString:@"locale"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=INTERNATIONAL"]];
	}
	else if ([key isEqualToString:@"location"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=LOCATION_SERVICES"]];
	}
	else if ([key isEqualToString:@"locations"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Privacy&path=LOCATION"]];
	}
	else if ([key isEqualToString:@"network"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Network"]];
	}
	else if ([key isEqualToString:@"nike_ipod"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=NIKE_PLUS_IPOD"]];
	}
	else if ([key isEqualToString:@"notes"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=NOTES"]];
	}
	else if ([key isEqualToString:@"notification_id"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=NOTIFICATIONS_ID"]];
	}
	else if ([key isEqualToString:@"passbook"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=PASSBOOK"]];
	}
	else if ([key isEqualToString:@"phone"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Phone"]];
	}
	else if ([key isEqualToString:@"photos"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Photos"]];
	}
	else if ([key isEqualToString:@"reset"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Reset"]];
	}
	else if ([key isEqualToString:@"ringtone"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Sounds&path=Ringtone"]];
	}
	else if ([key isEqualToString:@"browser"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Safari"]];
	}
	else if ([key isEqualToString:@"search"]) {
		if (SYSTEM_VERSION_LESS_THAN(@"10.0")) {
			result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Assistant"]];
		}
		else {
			result = [self do_open:[prefix stringByAppendingString:@"root=SIRI"]];
		}
	}
	else if ([key isEqualToString:@"sound"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Sounds"]];
	}
	else if ([key isEqualToString:@"software_update"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=SOFTWARE_UPDATE_LINK"]];
	}
	else if ([key isEqualToString:@"storage"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=CASTLE&path=STORAGE_AND_BACKUP"]];
	}
	else if ([key isEqualToString:@"store"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=STORE"]];
	}
	else if ([key isEqualToString:@"usage"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=USAGE"]];
	}
	else if ([key isEqualToString:@"video"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=VIDEO"]];
	}
	else if ([key isEqualToString:@"vpn"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Network/VPN"]];
	}
	else if ([key isEqualToString:@"wallpaper"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Wallpaper"]];
	} 
	else if ([key isEqualToString:@"wifi"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=WIFI"]];
	} 
	else if ([key isEqualToString:@"touch"]) {
	    result = [self do_open:[prefix stringByAppendingString:@"root=TOUCHID_PASSCODE"]];
	}	
	else if ([key isEqualToString:@"battery"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=BATTERY_USAGE"]];
	}
	else if ([key isEqualToString:@"privacy"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=Privacy"]];
	}
	else if ([key isEqualToString:@"do_not_disturb"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=DO_NOT_DISTURB"]];
	}
	else if ([key isEqualToString:@"keyboards"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=General&path=Keyboard/KEYBOARDS"]];
	}
	else if ([key isEqualToString:@"mobile_data"]) {
		result = [self do_open:[prefix stringByAppendingString:@"root=MOBILE_DATA_SETTINGS_ID"]];
	}
	else {
		pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Invalid Action"];
	}
		
	if (result) {
		pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:@"Opened"];
	} else {
		pluginResult = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Cannot open"];
	}
	
	[self.commandDelegate sendPluginResult:pluginResult callbackId:command.callbackId];
}

@end
