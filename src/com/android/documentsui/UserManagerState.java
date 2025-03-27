/*
 * Copyright (C) 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.documentsui;

import static androidx.core.util.Preconditions.checkNotNull;

import static com.android.documentsui.DevicePolicyResources.Drawables.Style.SOLID_COLORED;
import static com.android.documentsui.DevicePolicyResources.Drawables.WORK_PROFILE_ICON;
import static com.android.documentsui.DevicePolicyResources.Strings.PERSONAL_TAB;
import static com.android.documentsui.DevicePolicyResources.Strings.WORK_TAB;
import static com.android.documentsui.base.SharedMinimal.DEBUG;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserProperties;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;

import androidx.annotation.GuardedBy;
import androidx.annotation.RequiresApi;
import androidx.annotation.RequiresPermission;
import androidx.annotation.VisibleForTesting;

import com.android.documentsui.base.Features;
import com.android.documentsui.base.UserId;
import com.android.documentsui.util.VersionUtils;
import com.android.modules.utils.build.SdkLevel;

import com.google.common.base.Objects;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresApi(Build.VERSION_CODES.S)
public interface UserManagerState {

    /**
     * Returns the {@link UserId} of each profile which should be queried for documents. This will
     * always include {@link UserId#CURRENT_USER}.
     */
    List<UserId> getUserIds();

    /** Returns mapping between the {@link UserId} and the label for the profile */
    Map<UserId, String> getUserIdToLabelMap();

    /**
     * Returns mapping between the {@link UserId} and the drawable badge for the profile
     *
     * <p>returns {@code null} for non-profile userId
     */
    Map<UserId, Drawable> getUserIdToBadgeMap();

    /**
     * Returns a map of {@link UserId} to boolean value indicating whether the {@link
     * UserId}.CURRENT_USER can forward {@link Intent} to that {@link UserId}
     */
    Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent);

    /**
     * Updates the state of the list of userIds and all the associated maps according the intent
     * received in broadcast
     *
     * @param userId {@link UserId} for the profile for which the availability status changed
     * @param action {@link Intent}.ACTION_PROFILE_UNAVAILABLE and {@link
     *     Intent}.ACTION_PROFILE_AVAILABLE, {@link Intent}.ACTION_PROFILE_ADDED} and {@link
     *     Intent}.ACTION_PROFILE_REMOVED}
     */
    void onProfileActionStatusChange(String action, UserId userId);

    /** Sets the intent that triggered the launch of the DocsUI */
    void setCurrentStateIntent(Intent intent);

    /** Returns true if there are hidden profiles */
    boolean areHiddenInQuietModeProfilesPresent();

    /** Creates an implementation of {@link UserManagerState}. */
    // TODO: b/314746383 Make this class a singleton
    static UserManagerState create(Context context) {
        return new RuntimeUserManagerState(context);
    }

    /** Implementation of {@link UserManagerState} */
    final class RuntimeUserManagerState implements UserManagerState {

        private static final String TAG = "UserManagerState";
        private final Context mContext;
        private final UserId mCurrentUser;
        private final boolean mIsDeviceSupported;
        private final UserManager mUserManager;
        private final ConfigStore mConfigStore;

        /**
         * List of all the {@link UserId} that have the {@link UserProperties.ShowInSharingSurfaces}
         * set as `SHOW_IN_SHARING_SURFACES_SEPARATE` OR it is a system/personal user
         */
        @GuardedBy("mUserIds")
        private final List<UserId> mUserIds = new ArrayList<>();

        /** Mapping between the {@link UserId} to the corresponding profile label */
        @GuardedBy("mUserIdToLabelMap")
        private final Map<UserId, String> mUserIdToLabelMap = new HashMap<>();

        /** Mapping between the {@link UserId} to the corresponding profile badge */
        @GuardedBy("mUserIdToBadgeMap")
        private final Map<UserId, Drawable> mUserIdToBadgeMap = new HashMap<>();

        /**
         * Map containing {@link UserId}, other than that of the current user, as key and boolean
         * denoting whether it is accessible by the current user or not as value
         */
        @GuardedBy("mCanForwardToProfileIdMap")
        private final Map<UserId, Boolean> mCanForwardToProfileIdMap = new HashMap<>();

        private Intent mCurrentStateIntent;

        private final BroadcastReceiver mIntentReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        synchronized (mUserIds) {
                            mUserIds.clear();
                        }
                        synchronized (mUserIdToLabelMap) {
                            mUserIdToLabelMap.clear();
                        }
                        synchronized (mUserIdToBadgeMap) {
                            mUserIdToBadgeMap.clear();
                        }
                        synchronized (mCanForwardToProfileIdMap) {
                            mCanForwardToProfileIdMap.clear();
                        }
                    }
                };

        private RuntimeUserManagerState(Context context) {
            this(
                    context,
                    UserId.CURRENT_USER,
                    Features.CROSS_PROFILE_TABS && isDeviceSupported(context),
                    DocumentsApplication.getConfigStore());
        }

        @VisibleForTesting
        RuntimeUserManagerState(
                Context context,
                UserId currentUser,
                boolean isDeviceSupported,
                ConfigStore configStore) {
            mContext = context.getApplicationContext();
            mCurrentUser = checkNotNull(currentUser);
            mIsDeviceSupported = isDeviceSupported;
            mUserManager = mContext.getSystemService(UserManager.class);
            mConfigStore = configStore;

            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
            filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
            if (SdkLevel.isAtLeastV() && mConfigStore.isPrivateSpaceInDocsUIEnabled()) {
                filter.addAction(Intent.ACTION_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_PROFILE_REMOVED);
            }
            mContext.registerReceiver(mIntentReceiver, filter);
        }

        @Override
        public List<UserId> getUserIds() {
            synchronized (mUserIds) {
                if (mUserIds.isEmpty()) {
                    mUserIds.addAll(getUserIdsInternal());
                }
                return mUserIds;
            }
        }

        @Override
        public Map<UserId, String> getUserIdToLabelMap() {
            synchronized (mUserIdToLabelMap) {
                if (mUserIdToLabelMap.isEmpty()) {
                    getUserIdToLabelMapInternal();
                }
                return mUserIdToLabelMap;
            }
        }

        @Override
        public Map<UserId, Drawable> getUserIdToBadgeMap() {
            synchronized (mUserIdToBadgeMap) {
                if (mUserIdToBadgeMap.isEmpty()) {
                    getUserIdToBadgeMapInternal();
                }
                return mUserIdToBadgeMap;
            }
        }

        @Override
        public Map<UserId, Boolean> getCanForwardToProfileIdMap(Intent intent) {
            synchronized (mCanForwardToProfileIdMap) {
                if (mCanForwardToProfileIdMap.isEmpty()) {
                    getCanForwardToProfileIdMapInternal(intent);
                }
                return mCanForwardToProfileIdMap;
            }
        }

        @Override
        @SuppressLint("NewApi")
        public void onProfileActionStatusChange(String action, UserId userId) {
            if (!SdkLevel.isAtLeastV()) return;
            UserProperties userProperties =
                    mUserManager.getUserProperties(UserHandle.of(userId.getIdentifier()));
            if (userProperties.getShowInQuietMode() != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                return;
            }
            if (Intent.ACTION_PROFILE_UNAVAILABLE.equals(action)
                    || Intent.ACTION_PROFILE_REMOVED.equals(action)) {
                synchronized (mUserIds) {
                    mUserIds.remove(userId);
                }
            } else if (Intent.ACTION_PROFILE_AVAILABLE.equals(action)
                    || Intent.ACTION_PROFILE_ADDED.equals(action)) {
                synchronized (mUserIds) {
                    if (!mUserIds.contains(userId)) {
                        mUserIds.add(userId);
                    }
                }
                synchronized (mUserIdToLabelMap) {
                    if (!mUserIdToLabelMap.containsKey(userId)) {
                        mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                    }
                }
                synchronized (mUserIdToBadgeMap) {
                    if (!mUserIdToBadgeMap.containsKey(userId)) {
                        mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                    }
                }
                synchronized (mCanForwardToProfileIdMap) {
                    if (!mCanForwardToProfileIdMap.containsKey(userId)) {

                        UserHandle handle = UserHandle.of(userId.getIdentifier());

                        // Decide if to use the parent's access, or this handle's access.
                        if (isCrossProfileContentSharingStrategyDelegatedFromParent(handle)) {
                            UserHandle parentHandle = mUserManager.getProfileParent(handle);
                            // Couldn't resolve parent to check access, so fail closed.
                            if (parentHandle == null) {
                                mCanForwardToProfileIdMap.put(userId, false);
                            } else if (mCurrentUser.getIdentifier()
                                    == parentHandle.getIdentifier()) {
                                // Check if the parent is the current user, if so this profile
                                // is also accessible.
                                mCanForwardToProfileIdMap.put(userId, true);

                            } else {
                                UserId parent = UserId.of(parentHandle);
                                mCanForwardToProfileIdMap.put(
                                        userId,
                                        doesCrossProfileForwardingActivityExistForUser(
                                                mCurrentStateIntent, parent));
                            }
                        } else {
                            // Update the profile map for this profile.
                            mCanForwardToProfileIdMap.put(
                                    userId,
                                    doesCrossProfileForwardingActivityExistForUser(
                                            mCurrentStateIntent, userId));
                        }
                    }
                }
            } else {
                Log.e(TAG, "Unexpected action received: " + action);
            }
        }

        @Override
        public void setCurrentStateIntent(Intent intent) {
            mCurrentStateIntent = intent;
        }

        @Override
        public boolean areHiddenInQuietModeProfilesPresent() {
            if (!SdkLevel.isAtLeastV()) {
                return false;
            }

            for (UserId userId : getUserIds()) {
                if (mUserManager
                                .getUserProperties(UserHandle.of(userId.getIdentifier()))
                                .getShowInQuietMode()
                        == UserProperties.SHOW_IN_QUIET_MODE_HIDDEN) {
                    return true;
                }
            }
            return false;
        }

        private List<UserId> getUserIdsInternal() {
            final List<UserId> result = new ArrayList<>();

            if (!mIsDeviceSupported) {
                result.add(mCurrentUser);
                return result;
            }

            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                result.add(mCurrentUser);
                return result;
            }

            final List<UserHandle> userProfiles = mUserManager.getUserProfiles();
            if (userProfiles.size() < 2) {
                result.add(mCurrentUser);
                return result;
            }

            if (SdkLevel.isAtLeastV()) {
                getUserIdsInternalPostV(userProfiles, result);
            } else {
                getUserIdsInternalPreV(userProfiles, result);
            }
            return result;
        }

        @SuppressLint("NewApi")
        private void getUserIdsInternalPostV(List<UserHandle> userProfiles, List<UserId> result) {
            for (UserHandle userHandle : userProfiles) {
                if (userHandle.getIdentifier() == ActivityManager.getCurrentUser()) {
                    result.add(UserId.of(userHandle));
                } else {
                    // Out of all the profiles returned by user manager the profiles that are
                    // returned should satisfy both the following conditions:
                    // 1. It has user property SHOW_IN_SHARING_SURFACES_SEPARATE
                    // 2. Quite mode is not enabled, if it is enabled then the profile's user
                    // property is not SHOW_IN_QUIET_MODE_HIDDEN
                    if (isProfileAllowed(userHandle)) {
                        result.add(UserId.of(userHandle));
                    }
                }
            }
            if (result.isEmpty()) {
                result.add(mCurrentUser);
            }
        }

        /**
         * Checks if a package is installed for a given user.
         *
         * @param userHandle The ID of the user.
         * @return {@code true} if the package is installed for the user, {@code false} otherwise.
         */
        @RequiresPermission(
                anyOf = {
                    "android.permission.MANAGE_USERS",
                    "android.permission.INTERACT_ACROSS_USERS"
                })
        private boolean isPackageInstalledForUser(UserHandle userHandle) {
            String packageName = mContext.getPackageName();
            try {
                Context userPackageContext =
                        mContext.createPackageContextAsUser(
                                mContext.getPackageName(), 0 /* flags */, userHandle);
                return userPackageContext != null;
            } catch (PackageManager.NameNotFoundException e) {
                Log.w(TAG, "Package " + packageName + " not found for user " + userHandle);
                return false;
            }
        }

        /**
         * Checks if quiet mode is enabled for a given user.
         *
         * @param userHandle The UserHandle of the profile to check.
         * @return {@code true} if quiet mode is enabled, {@code false} otherwise.
         */
        private boolean isQuietModeEnabledForUser(UserHandle userHandle) {
            return UserId.of(userHandle.getIdentifier()).isQuietModeEnabled(mContext);
        }

        /**
         * Checks if a profile should be allowed, taking into account quiet mode and package
         * installation.
         *
         * @param userHandle The UserHandle of the profile to check.
         * @return {@code true} if the profile should be allowed, {@code false} otherwise.
         */
        @SuppressLint("NewApi")
        @RequiresPermission(
                anyOf = {
                    "android.permission.MANAGE_USERS",
                    "android.permission.INTERACT_ACROSS_USERS"
                })
        private boolean isProfileAllowed(UserHandle userHandle) {
            final UserProperties userProperties = mUserManager.getUserProperties(userHandle);

            // 1. Check if the package is installed for the user
            if (!isPackageInstalledForUser(userHandle)) {
                Log.w(
                        TAG,
                        "Package "
                                + mContext.getPackageName()
                                + " is not installed for user "
                                + userHandle);
                return false;
            }

            // 2. Check user properties and quiet mode
            if (userProperties.getShowInSharingSurfaces()
                    == UserProperties.SHOW_IN_SHARING_SURFACES_SEPARATE) {
                // Return true if profile is not in quiet mode or if it is in quiet mode
                // then its user properties do not require it to be hidden
                return !isQuietModeEnabledForUser(userHandle)
                        || userProperties.getShowInQuietMode()
                                != UserProperties.SHOW_IN_QUIET_MODE_HIDDEN;
            }

            return false;
        }

        private void getUserIdsInternalPreV(List<UserHandle> userProfiles, List<UserId> result) {
            result.add(mCurrentUser);
            UserId systemUser = null;
            UserId managedUser = null;
            for (UserHandle userHandle : userProfiles) {
                if (userHandle.isSystem()) {
                    systemUser = UserId.of(userHandle);
                } else if (mUserManager.isManagedProfile(userHandle.getIdentifier())) {
                    managedUser = UserId.of(userHandle);
                }
            }
            if (mCurrentUser.isSystem() && managedUser != null) {
                result.add(managedUser);
            } else if (mCurrentUser.isManagedProfile(mUserManager) && systemUser != null) {
                result.add(0, systemUser);
            } else {
                if (DEBUG) {
                    Log.w(
                            TAG,
                            "The current user "
                                    + UserId.CURRENT_USER
                                    + " is neither system nor managed user. has system user: "
                                    + (systemUser != null));
                }
            }
        }

        private void getUserIdToLabelMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToLabelMapInternalPostV();
            } else {
                getUserIdToLabelMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToLabelMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToLabelMap) {
                    mUserIdToLabelMap.put(userId, getProfileLabel(userId));
                }
            }
        }

        private void getUserIdToLabelMapInternalPreV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(
                                userId, getEnterpriseString(WORK_TAB, R.string.work_tab));
                    }
                } else {
                    synchronized (mUserIdToLabelMap) {
                        mUserIdToLabelMap.put(
                                userId, getEnterpriseString(PERSONAL_TAB, R.string.personal_tab));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private String getProfileLabel(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return getEnterpriseString(PERSONAL_TAB, R.string.personal_tab);
            }
            try {
                Context userContext =
                        mContext.createContextAsUser(
                                UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getProfileLabel();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile label:\n" + e);
                return null;
            }
        }

        private String getEnterpriseString(String updatableStringId, int defaultStringId) {
            if (SdkLevel.isAtLeastT()) {
                return getUpdatableEnterpriseString(updatableStringId, defaultStringId);
            } else {
                return mContext.getString(defaultStringId);
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private String getUpdatableEnterpriseString(String updatableStringId, int defaultStringId) {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            if (Objects.equal(dpm, null)) {
                Log.e(TAG, "can not get device policy manager");
                return mContext.getString(defaultStringId);
            }
            return dpm.getResources()
                    .getString(updatableStringId, () -> mContext.getString(defaultStringId));
        }

        private void getUserIdToBadgeMapInternal() {
            if (SdkLevel.isAtLeastV()) {
                getUserIdToBadgeMapInternalPostV();
            } else {
                getUserIdToBadgeMapInternalPreV();
            }
        }

        @SuppressLint("NewApi")
        private void getUserIdToBadgeMapInternalPostV() {
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                synchronized (mUserIdToBadgeMap) {
                    mUserIdToBadgeMap.put(userId, getProfileBadge(userId));
                }
            }
        }

        private void getUserIdToBadgeMapInternalPreV() {
            if (!SdkLevel.isAtLeastR()) return;
            if (mUserManager == null) {
                Log.e(TAG, "cannot obtain user manager");
                return;
            }
            List<UserId> userIds = getUserIds();
            for (UserId userId : userIds) {
                if (mUserManager.isManagedProfile(userId.getIdentifier())) {
                    synchronized (mUserIdToBadgeMap) {
                        mUserIdToBadgeMap.put(
                                userId,
                                SdkLevel.isAtLeastT()
                                        ? getWorkProfileBadge()
                                        : mContext.getDrawable(R.drawable.ic_briefcase));
                    }
                }
            }
        }

        @SuppressLint("NewApi")
        private Drawable getProfileBadge(UserId userId) {
            if (userId.getIdentifier() == ActivityManager.getCurrentUser()) {
                return null;
            }
            try {
                Context userContext =
                        mContext.createContextAsUser(
                                UserHandle.of(userId.getIdentifier()), 0 /* flags */);
                UserManager userManagerAsUser = userContext.getSystemService(UserManager.class);
                if (userManagerAsUser == null) {
                    Log.e(TAG, "cannot obtain user manager");
                    return null;
                }
                return userManagerAsUser.getUserBadge();
            } catch (Exception e) {
                Log.e(TAG, "Exception occurred while trying to get profile badge:\n" + e);
                return null;
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private Drawable getWorkProfileBadge() {
            DevicePolicyManager dpm = mContext.getSystemService(DevicePolicyManager.class);
            Drawable drawable =
                    dpm.getResources()
                            .getDrawable(
                                    WORK_PROFILE_ICON,
                                    SOLID_COLORED,
                                    () -> mContext.getDrawable(R.drawable.ic_briefcase));
            return drawable;
        }

        /**
         * Updates Cross Profile access for all UserProfiles in {@code getUserIds()}
         *
         * <p>This method looks at a variety of situations for each Profile and decides if the
         * profile's content is accessible by the current process owner user id.
         *
         * <ol>
         *   <li>UserProperties attributes for CrossProfileDelegation are checked first. When the
         *       profile delegates to the parent profile, the parent's access is used.
         *   <li>{@link CrossProfileIntentForwardingActivity}s are resolved via the process owner's
         *       PackageManager, and are considered when evaluating cross profile to the target
         *       profile.
         * </ol>
         *
         * <p>In the event none of the above checks succeeds, the profile is considered to be
         * inaccessible to the current process user.
         *
         * @param intent The intent Photopicker is currently running under, for
         *     CrossProfileForwardActivity checking.
         */
        private void getCanForwardToProfileIdMapInternal(Intent intent) {

            Map<UserId, Boolean> profileIsAccessibleToProcessOwner = new HashMap<>();

            List<UserId> delegatedFromParent = new ArrayList<>();

            for (UserId userId : getUserIds()) {

                // Early exit, self is always accessible.
                if (userId.getIdentifier() == mCurrentUser.getIdentifier()) {
                    profileIsAccessibleToProcessOwner.put(userId, true);
                    continue;
                }

                // CrossProfileContentSharingStrategyDelegatedFromParent is only V+ sdks.
                if (SdkLevel.isAtLeastV()
                        && isCrossProfileContentSharingStrategyDelegatedFromParent(
                                UserHandle.of(userId.getIdentifier()))) {
                    delegatedFromParent.add(userId);
                    continue;
                }

                // Check for cross profile & add to the map.
                profileIsAccessibleToProcessOwner.put(
                        userId, doesCrossProfileForwardingActivityExistForUser(intent, userId));
            }

            // For profiles that delegate their access to the parent, set the access for
            // those profiles
            // equal to the same as their parent.
            for (UserId userId : delegatedFromParent) {
                UserHandle parent =
                        mUserManager.getProfileParent(UserHandle.of(userId.getIdentifier()));
                profileIsAccessibleToProcessOwner.put(
                        userId,
                        profileIsAccessibleToProcessOwner.getOrDefault(
                                UserId.of(parent), /* default= */ false));
            }

            synchronized (mCanForwardToProfileIdMap) {
                mCanForwardToProfileIdMap.clear();
                for (Map.Entry<UserId, Boolean> entry :
                        profileIsAccessibleToProcessOwner.entrySet()) {
                    mCanForwardToProfileIdMap.put(entry.getKey(), entry.getValue());
                }
            }
        }

        /**
         * Looks for a matching CrossProfileIntentForwardingActivity in the targetUserId for the
         * given intent.
         *
         * @param intent The intent the forwarding activity needs to match.
         * @param targetUserId The target user to check for.
         * @return whether a CrossProfileIntentForwardingActivity could be found for the given
         *     intent, and user.
         */
        private boolean doesCrossProfileForwardingActivityExistForUser(
                Intent intent, UserId targetUserId) {

            final PackageManager pm = mContext.getPackageManager();
            final Intent intentToCheck = (Intent) intent.clone();
            intentToCheck.setComponent(null);
            intentToCheck.setPackage(null);

            for (ResolveInfo resolveInfo :
                    pm.queryIntentActivities(intentToCheck, PackageManager.MATCH_DEFAULT_ONLY)) {

                if (resolveInfo.isCrossProfileIntentForwarderActivity()) {
                    /*
                     * IMPORTANT: This is a reflection based hack to ensure the profile is
                     * actually the installer of the CrossProfileIntentForwardingActivity.
                     *
                     * ResolveInfo.targetUserId exists, but is a hidden API not available to
                     * mainline modules, and no such API exists, so it is accessed via
                     * reflection below. All exceptions are caught to protect against
                     * reflection related issues such as:
                     * NoSuchFieldException / IllegalAccessException / SecurityException.
                     *
                     * In the event of an exception, the code fails "closed" for the current
                     * profile to avoid showing content that should not be visible.
                     */
                    try {
                        Field targetUserIdField =
                                resolveInfo.getClass().getDeclaredField("targetUserId");
                        targetUserIdField.setAccessible(true);
                        int activityTargetUserId = (int) targetUserIdField.get(resolveInfo);

                        if (activityTargetUserId == targetUserId.getIdentifier()) {

                            // Found a match for this profile
                            return true;
                        }

                    } catch (NoSuchFieldException | IllegalAccessException | SecurityException ex) {
                        // Couldn't check the targetUserId via reflection, so fail without
                        // further iterations.
                        Log.e(TAG, "Could not access targetUserId via reflection.", ex);
                        return false;
                    } catch (Exception ex) {
                        Log.e(TAG, "Exception occurred during cross profile checks", ex);
                    }
                }
            }

            // No match found, so return false.
            return false;
        }

        @SuppressLint("NewApi")
        private boolean isCrossProfileContentSharingStrategyDelegatedFromParent(
                UserHandle userHandle) {
            if (mUserManager == null) {
                Log.e(TAG, "can not obtain user manager");
                return false;
            }
            UserProperties userProperties = mUserManager.getUserProperties(userHandle);
            if (java.util.Objects.equals(userProperties, null)) {
                Log.e(TAG, "can not obtain user properties");
                return false;
            }

            return userProperties.getCrossProfileContentSharingStrategy()
                    == UserProperties.CROSS_PROFILE_CONTENT_SHARING_DELEGATE_FROM_PARENT;
        }

        private static boolean isDeviceSupported(Context context) {
            // The feature requires Android R DocumentsContract APIs and
            // INTERACT_ACROSS_USERS_FULL permission.
            return VersionUtils.isAtLeastR()
                    && context.checkSelfPermission(Manifest.permission.INTERACT_ACROSS_USERS)
                            == PackageManager.PERMISSION_GRANTED;
        }
    }
}
