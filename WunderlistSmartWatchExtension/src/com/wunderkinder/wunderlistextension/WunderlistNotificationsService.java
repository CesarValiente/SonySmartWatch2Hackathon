/*
Copyright (c) 2011, Sony Ericsson Mobile Communications AB
Copyright (c) 2011-2013, Sony Mobile Communications AB

 All rights reserved.

 Redistribution and use in source and binary forms, with or without
 modification, are permitted provided that the following conditions are met:

 * Redistributions of source code must retain the above copyright notice, this
 list of conditions and the following disclaimer.

 * Redistributions in binary form must reproduce the above copyright notice,
 this list of conditions and the following disclaimer in the documentation
 and/or other materials provided with the distribution.

 * Neither the name of the Sony Ericsson Mobile Communications AB / Sony Mobile
 Communications AB nor the names of its contributors may be used to endorse or promote
 products derived from this software without specific prior written permission.

 THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.wunderkinder.wunderlistextension;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;

import com.sonyericsson.extras.liveware.aef.notification.Notification;
import com.sonyericsson.extras.liveware.aef.registration.Registration;
import com.sonyericsson.extras.liveware.extension.util.ExtensionService;
import com.sonyericsson.extras.liveware.extension.util.notification.NotificationUtil;
import com.sonyericsson.extras.liveware.extension.util.registration.DeviceInfoHelper;
import com.sonyericsson.extras.liveware.extension.util.registration.RegistrationInformation;

/**
 * The sample extension service handles extension registration and inserts data
 * into the notification database.
 */
public class WunderlistNotificationsService extends ExtensionService {

    /**
     * Extensions specific id for the source
     */
    public static final String EXTENSION_SPECIFIC_ID = "EXTENSION_SPECIFIC_ID_SAMPLE_NOTIFICATION";

    /**
     * Extension key
     */
    public static final String EXTENSION_KEY = "com.sonymobile.smartconnect.extension.notificationsample.key";

    /**
     * Log tag
     */
    public static final String LOG_TAG = "SampleNotificationExtension";

    /**
     * Time between new data insertion
     */
    private static final long INTERVAL = 10 * 1000;

    /**
     * Starts periodic insert of data handled in onStartCommand()
     */
    public static final String INTENT_ACTION_START = "com.wunderkinder.wunderlistextension.action.start";

    /**
     * Stop periodic insert of data, handled in onStartCommand()
     */
    public static final String INTENT_ACTION_STOP = "com.wunderkinder.wunderlistextension.action.stop";

    /**
     * Add data, handled in onStartCommand()
     */
    private static final String INTENT_ACTION_ADD = "com.wunderkinder.wunderlistextension.action.add";

    public WunderlistNotificationsService() {
        super(EXTENSION_KEY);
    }

    /**
     * {@inheritDoc}
     *
     * @see android.app.Service#onCreate()
     */
    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(LOG_TAG, "onCreate");
    }

    /**
     * {@inheritDoc}
     *
     * @see android.app.Service#onStartCommand()
     */
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int retVal = super.onStartCommand(intent, flags, startId);
        if (intent != null) {
            if (INTENT_ACTION_START.equals(intent.getAction())) {
                Log.d(LOG_TAG, "onStart action: INTENT_ACTION_START");
                startAddData();
                stopSelfCheck();
            } else if (INTENT_ACTION_STOP.equals(intent.getAction())) {
                Log.d(LOG_TAG, "onStart action: INTENT_ACTION_STOP");
                stopAddData();
                stopSelfCheck();
            } else if (INTENT_ACTION_ADD.equals(intent.getAction())) {
                Log.d(LOG_TAG, "onStart action: INTENT_ACTION_ADD");
                String taskTitle = intent.getStringExtra("taskTitle");
                String taskId = intent.getStringExtra("taskId");
                int taskDbId = intent.getIntExtra("taskDbObjectId", 0);
                String geoData = intent.getStringExtra("taskGeoData");
                String uri = intent.getStringExtra("taskGeoUri");
                addData(intent.getStringExtra("action"), taskTitle, taskId, taskDbId, geoData, uri);


                stopSelfCheck();
            }
        }

        return retVal;
    }

    /**
     * {@inheritDoc}
     *
     * @see android.app.Service#onDestroy()
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(LOG_TAG, "onDestroy");
    }

    /**
     * Start periodic data insertion into event table
     */
    private void startAddData() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, WunderlistNotificationsService.class);
        i.setAction(INTENT_ACTION_ADD);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        am.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(),
                INTERVAL, pi);
    }

    /**
     * Cancel scheduled data insertion
     */
    private void stopAddData() {
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        Intent i = new Intent(this, WunderlistNotificationsService.class);
        i.setAction(INTENT_ACTION_ADD);
        PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
        am.cancel(pi);
    }


    private void addData(String _action, String _name, String _id, int _dbId, String geoData, String uri) {
        String name = _action;
        String message = _name;
        long time = System.currentTimeMillis();
        long sourceId = NotificationUtil
                .getSourceId(this, EXTENSION_SPECIFIC_ID);
        if (sourceId == NotificationUtil.INVALID_ID) {
            Log.e(LOG_TAG, "Failed to insert data");
            return;
        }

        String extraInfo = _dbId + "," + _id;

        ContentValues eventValues = new ContentValues();
        eventValues.put(Notification.EventColumns.EVENT_READ_STATUS, false);
        eventValues.put(Notification.EventColumns.DISPLAY_NAME, name);
        eventValues.put(Notification.EventColumns.MESSAGE, message);
        eventValues.put(Notification.EventColumns.PERSONAL, 1);
        eventValues.put(Notification.EventColumns.PUBLISHED_TIME, time);
        eventValues.put(Notification.EventColumns.SOURCE_ID, sourceId);
        eventValues.put(Notification.EventColumns.FRIEND_KEY, extraInfo);

        if (geoData != null) {
            eventValues.put(Notification.EventColumns.GEO_DATA, geoData);
        }

        if (uri != null) {
            eventValues.put(Notification.EventColumns.IMAGE_URI, uri);
        }

        try {
            getContentResolver().insert(Notification.Event.URI, eventValues);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Failed to insert event", e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Failed to insert event, is Live Ware Manager installed?", e);
        } catch (SQLException e) {
            Log.e(LOG_TAG, "Failed to insert event", e);
        }
    }

    @Override
    protected void onViewEvent(Intent intent) {
        String action = intent.getStringExtra(Notification.Intents.EXTRA_ACTION);
        String hostAppPackageName = intent
                .getStringExtra(Registration.Intents.EXTRA_AHA_PACKAGE_NAME);
        boolean advancedFeaturesSupported = DeviceInfoHelper.isSmartWatch2ApiAndScreenDetected(
                this, hostAppPackageName);

        int eventId = intent.getIntExtra(Notification.Intents.EXTRA_EVENT_ID, -1);
        if (Notification.SourceColumns.ACTION_1.equals(action)) {
            doAction1(eventId);
        } else if (Notification.SourceColumns.ACTION_2.equals(action)) {
            doAction2(eventId);
        } else if (Notification.SourceColumns.ACTION_3.equals(action)) {
            doAction3(eventId);
        }
    }

    @Override
    protected void onRefreshRequest() {
        // Do nothing here, only relevant for polling extensions, this
        // extension is always up to date
    }

    /**
     * Show toast with event information
     *
     * @param eventId The event id
     */
    public void doAction1(int eventId) {
        // Snooze
        Log.d(LOG_TAG, "Snooze " + eventId);

        Cursor cursor = null;
        try {
            String data = "";
            cursor = getContentResolver().query(Notification.Event.URI, null,
                    Notification.EventColumns._ID + " = " + eventId, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                data = cursor.getString(cursor.getColumnIndexOrThrow(Notification.EventColumns.FRIEND_KEY));
                if (data != null) {
                    String[] d = data.split(",");
                    Intent markDoneIntent = new Intent("com.wunderkinder.wunderlistandroid.action" +
                            ".snooze_action");
                    markDoneIntent.putExtra("extra_task_id", d[1]);
                    markDoneIntent.putExtra("extra_task_db_id", Integer.valueOf(d[0]));

                    getBaseContext().sendBroadcast(markDoneIntent);
                }
            }
        } catch (SQLException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Also, remove the reminder
        doAction3(eventId);
    }

    public void doAction2(int eventId) {
        // Mark task as done
        Log.d(LOG_TAG, "Mark as done " + eventId);

        Cursor cursor = null;
        try {
            String data = "";
            cursor = getContentResolver().query(Notification.Event.URI, null,
                    Notification.EventColumns._ID + " = " + eventId, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                data = cursor.getString(cursor.getColumnIndexOrThrow(Notification.EventColumns.FRIEND_KEY));
                if (data != null) {
                    String[] d = data.split(",");
                    Intent markDoneIntent = new Intent("com.wunderkinder.wunderlistandroid.action" +
                            ".mark_task_done_action");
                    markDoneIntent.putExtra("extra_task_id", d[1]);
                    markDoneIntent.putExtra("extra_task_db_id", Integer.valueOf(d[0]));

                    getBaseContext().sendBroadcast(markDoneIntent);
                }
            }
        } catch (SQLException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } catch (SecurityException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } catch (IllegalArgumentException e) {
            Log.e(LOG_TAG, "Failed to query event", e);
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Also, remove the reminder
        doAction3(eventId);
    }

    public void doAction3(int eventId) {
        // Remove reminder
        Log.d(LOG_TAG, "Remove event " + eventId);
        String[] arguments = { String.valueOf(eventId) };
        NotificationUtil.deleteEvents(getBaseContext(), Notification.EventColumns._ID + "=?", arguments);
    }

    /**
     * Called when extension and sources has been successfully registered.
     * Override this method to take action after a successful registration.
     */
    @Override
    public void onRegisterResult(boolean result) {
        super.onRegisterResult(result);
        Log.d(LOG_TAG, "onRegisterResult");

        // Start adding data if extension is active in preferences
        if (result) {
            SharedPreferences prefs = PreferenceManager
                    .getDefaultSharedPreferences(this);
            boolean isActive = prefs.getBoolean(
                    getString(R.string.preference_key_is_active), false);
            if (isActive) {
                startAddData();
            }
        }
    }

    @Override
    protected RegistrationInformation getRegistrationInformation() {
        return new WunderlistExtensionRegistrationInformation(this);
    }

    /*
     * (non-Javadoc)
     * @see com.sonyericsson.extras.liveware.aef.util.ExtensionService#
     * keepRunningWhenConnected()
     */
    @Override
    protected boolean keepRunningWhenConnected() {
        return false;
    }
}
