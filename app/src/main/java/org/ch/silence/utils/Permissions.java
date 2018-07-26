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
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;

import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Permissions check and request helper
 */
public class Permissions {
//    private static final String TAG = Permissions.class.getName();
    private static final int REQUEST_CODE = (Permissions.class.hashCode() & 0xffff);
    private static final Map<String, Boolean> permissionsResults = new ConcurrentHashMap<>();

    // Permissions names
    public static final String WRITE_EXTERNAL_STORAGE = "android.permission.WRITE_EXTERNAL_STORAGE";
    public static final String CALL_PHONE = "android.permission.CALL_PHONE";
    public static final String READ_PHONE_STATE = "android.permission.READ_PHONE_STATE";
    public static final String READ_CONTACTS = "android.permission.READ_CONTACTS";


    /**
     * Checks for permission
     **/
    public static boolean isGranted(@NonNull Context context, @NonNull String permission) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return true;
        }
        Boolean result = permissionsResults.get(permission);
        if (result == null) {
            int permissionCheck = ContextCompat.checkSelfPermission(context, permission);
            result = (permissionCheck == PackageManager.PERMISSION_GRANTED);
            permissionsResults.put(permission, result);
        }
        return result;
    }


}
