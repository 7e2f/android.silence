/*
 * Copyright (C) 2017 Anton Kaliturin <kaliturin@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.ch.silence.utils;

import android.app.Activity;
import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.ch.silence.R;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Settings {
    public static final String ENABLE_WHITELIST = "ENABLE_WHITELIST";

    private static final String TRUE = "TRUE";
    private static final String FALSE = "FALSE";

    private static Map<String, String> settingsMap = new ConcurrentHashMap<>();

    public static boolean setStringValue(Context context, @NonNull String name, @NonNull String value) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
        if (db != null && db.setSettingsValue(name, value)) {
            settingsMap.put(name, value);
            return true;
        }
        return false;
    }

    @Nullable
    public static String getStringValue(Context context, @NonNull String name) {
        String value = settingsMap.get(name);
        if (value == null) {
            DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(context);
            if (db != null) {
                value = db.getSettingsValue(name);
                if (value != null) {
                    settingsMap.put(name, value);
                }
            }
        }
        return value;
    }

    public static boolean setBooleanValue(Context context, @NonNull String name, boolean value) {
        String v = (value ? TRUE : FALSE);
        return setStringValue(context, name, v);
    }

    public static boolean getBooleanValue(Context context, @NonNull String name) {
        String value = getStringValue(context, name);
        return (value != null && value.equals(TRUE));
    }

}
