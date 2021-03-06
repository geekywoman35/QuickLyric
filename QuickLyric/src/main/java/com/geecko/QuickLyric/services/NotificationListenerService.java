/*
 * *
 *  * This file is part of QuickLyric
 *  * Created by geecko
 *  *
 *  * QuickLyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * QuickLyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  * GNU General Public License for more details.
 *  * You should have received a copy of the GNU General Public License
 *  * along with QuickLyric.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.geecko.QuickLyric.services;


import android.annotation.TargetApi;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.InflateException;
import android.view.LayoutInflater;
import android.view.ViewGroup;
import android.widget.RemoteViews;
import android.widget.TextView;

import com.geecko.QuickLyric.App;
import com.geecko.QuickLyric.MainActivity;
import com.geecko.QuickLyric.broadcastReceiver.MusicBroadcastReceiver;
import com.geecko.QuickLyric.fragment.LyricsViewFragment;

@TargetApi(Build.VERSION_CODES.KITKAT)
public class NotificationListenerService extends android.service.notification.NotificationListenerService
        implements RemoteController.OnClientUpdateListener {

    private RemoteController mRemoteController;
    private boolean isRemoteControllerPlaying;
    private boolean mHasBug = true;

    @Override
    public void onCreate() {
        super.onCreate();
        mRemoteController = new RemoteController(this, this);
        if (!((AudioManager) getSystemService(Context.AUDIO_SERVICE)).registerRemoteController(mRemoteController)) {
            throw new RuntimeException("Error while registering RemoteController!");
        } else {
            mRemoteController.setArtworkConfiguration(1024, 1024);
            // setSynchronizationMode(mRemoteController, RemoteController.POSITION_SYNCHRONIZATION_CHECK);
        }
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        super.onNotificationPosted(sbn);
        String artist = null;
        String track = null;
        boolean isPlaying = false;
        int duration = -1;
        int position = -1;
        ViewGroup viewGroup = getNotificationLayout(sbn);
        if (viewGroup == null)
            return;
        String packageName = sbn.getPackageName();
        try {
            switch (packageName) {
                case "com.apple.android.music":
                    track = ((TextView) ((ViewGroup) viewGroup.getChildAt(1))
                            .getChildAt(0)).getText().toString();
                    artist = ((TextView) ((ViewGroup) viewGroup.getChildAt(1))
                            .getChildAt(2)).getText().toString();
                    isPlaying = ((ViewGroup) viewGroup.getChildAt(3))
                            .getChildAt(1).getContentDescription().equals("Pause");
                    break;
                case "com.saavn.android":
                    track = ((TextView) ((ViewGroup) viewGroup.getChildAt(4))
                            .getChildAt(0)).getText().toString();
                    artist = ((TextView) ((ViewGroup) viewGroup.getChildAt(4))
                            .getChildAt(1)).getText().toString();
                    break;
                case "com.pandora.android":
                    track = ((TextView) ((ViewGroup) ((ViewGroup) viewGroup.getChildAt(2))
                            .getChildAt(0)).getChildAt(0)).getText().toString();
                    artist = ((TextView) ((ViewGroup) viewGroup.getChildAt(2))
                            .getChildAt(1)).getText().toString();
                    break;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        if ((artist != null && !artist.trim().isEmpty()) && (track != null && !track.trim().isEmpty()))
            broadcast(artist, track, isPlaying, duration, position);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }

    private void broadcast(String artist, String track, boolean playing, int duration, long position) {
        Intent localIntent = new Intent("com.android.music.metachanged");
        localIntent.putExtra("artist", artist);
        localIntent.putExtra("track", track);
        localIntent.putExtra("playing", playing);
        localIntent.putExtra("duration", duration);
        if (position != -1)
            localIntent.putExtra("position", position);
        new MusicBroadcastReceiver().onReceive(this, localIntent);
    }

    private void broadcast(String artist, String track, boolean playing, double duration, long position) {
        Intent localIntent = new Intent("com.android.music.metachanged");
        localIntent.putExtra("artist", artist);
        localIntent.putExtra("track", track);
        localIntent.putExtra("playing", playing);
        localIntent.putExtra("duration", duration);
        if (position != -1)
            localIntent.putExtra("position", position);
        new MusicBroadcastReceiver().onReceive(this, localIntent);
    }

    private ViewGroup getNotificationLayout(StatusBarNotification sbn) {
        RemoteViews rv;
        if (sbn.getNotification().bigContentView != null)
            rv = sbn.getNotification().bigContentView;
        else
            rv = sbn.getNotification().contentView;
        Context context = null;
        try {
            context = createPackageContext(sbn.getPackageName(), Context.CONTEXT_RESTRICTED);
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        ViewGroup viewGroup = null;
        try {
            viewGroup = (ViewGroup) ((LayoutInflater) context
                    .getSystemService(LAYOUT_INFLATER_SERVICE))
                    .inflate(rv.getLayoutId(), null);
            rv.reapply(context, viewGroup);
        } catch (InflateException ignore) {
        }
        return viewGroup;
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        if (MainActivity.waitingForListener) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }
    }

    public static boolean isListeningAuthorized(Context context) {
        ContentResolver contentResolver = context.getContentResolver();
        String enabledNotificationListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners");
        String packageName = context.getPackageName();

        return !(enabledNotificationListeners == null || !enabledNotificationListeners.contains(packageName));

    }

    @Override
    public void onClientChange(boolean clearing) {
        isListeningAuthorized(this);
    }

    @Override
    public void onClientPlaybackStateUpdate(int state) {
        this.isRemoteControllerPlaying = state == RemoteControlClient.PLAYSTATE_PLAYING;
    }

    @Override
    public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs, long currentPosMs, float speed) {
        this.isRemoteControllerPlaying = state == RemoteControlClient.PLAYSTATE_PLAYING;
        mHasBug = false;
        SharedPreferences current = getSharedPreferences("current_music", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = current.edit();
        editor.putLong("position", currentPosMs);
        if (isRemoteControllerPlaying) {
            long currentTime = System.currentTimeMillis();
            editor.putLong("startTime", currentTime);
        }
        editor.putBoolean("playing", isRemoteControllerPlaying).apply();

        if (App.isActivityVisible() && isRemoteControllerPlaying) {
            Intent internalIntent = new Intent("Broadcast");
            internalIntent
                    .putExtra("artist", current.getString("artist", ""))
                    .putExtra("track", current.getString("track", ""));
            LyricsViewFragment.sendIntent(this, internalIntent);
        }
        Log.d("geecko", "PlaybackStateUpdate - position stored: " + currentPosMs);
    }

    @Override
    public void onClientTransportControlUpdate(int transportControlFlags) {
        if (mHasBug) {
            long position = mRemoteController.getEstimatedMediaPosition() + 3000;
            SharedPreferences current = getSharedPreferences("current_music", Context.MODE_PRIVATE);
            current.edit().putLong("position", position).apply();
            if (isRemoteControllerPlaying) {
                long currentTime = System.currentTimeMillis();
                current.edit().putLong("startTime", currentTime).apply();
            }
            Log.d("geecko", "TransportControlUpdate - position stored: " + position);
        }
    }

    @Override
    public void onClientMetadataUpdate(RemoteController.MetadataEditor metadataEditor) {
        // isRemoteControllerPlaying = true;
        long position = mRemoteController.getEstimatedMediaPosition();
        Object durationObject = metadataEditor.getObject(MediaMetadataRetriever.METADATA_KEY_DURATION, 60000);
        String artist = metadataEditor.getString(MediaMetadataRetriever.METADATA_KEY_ARTIST,
                metadataEditor.getString(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST, ""));
        String track = metadataEditor.getString(MediaMetadataRetriever.METADATA_KEY_TITLE, "");
        // ToDo handle BitMap?
        if (durationObject instanceof Double)
            broadcast(artist, track, isRemoteControllerPlaying, (Double) durationObject, position);
        else if (durationObject instanceof Integer)
            broadcast(artist, track, isRemoteControllerPlaying, (Integer) durationObject, position);
        else if (durationObject instanceof Long)
            broadcast(artist, track, isRemoteControllerPlaying, (Long) durationObject, position);
        Log.d("geecko", "MetadataUpdate - position stored: " + position);
    }

    public void onClientSessionEvent(String packageName, Bundle bundle) {
    }
}
