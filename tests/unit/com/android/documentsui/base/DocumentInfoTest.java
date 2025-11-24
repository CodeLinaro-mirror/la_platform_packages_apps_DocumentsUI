/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.documentsui.base;

import static android.provider.DocumentsContract.Document.MIME_TYPE_DIR;

import static androidx.core.util.Preconditions.checkArgument;

import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_AVAILABLE_LOCALLY;
import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_DOWNLOAD_ERROR;
import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_DOWNLOAD_PROGRESS;
import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_LOCAL_CHANGES;
import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_UPLOAD_ERROR;
import static com.android.documentsui.base.DocumentInfo.SYNC_STATE_FLAG_UPLOAD_PROGRESS;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.kotlin.VerificationKt.never;
import static org.mockito.kotlin.VerificationKt.verify;

import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.platform.test.annotations.DisableFlags;
import android.platform.test.annotations.EnableFlags;
import android.provider.DocumentsContract;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SmallTest;
import androidx.test.rule.provider.ProviderTestRule;

import com.android.documentsui.InspectorProvider;
import com.android.documentsui.archives.ArchivesProvider;
import com.android.documentsui.flags.Flags;
import com.android.documentsui.rules.OverrideFlagsRule;
import com.android.documentsui.testing.TestProvidersAccess;
import com.android.documentsui.util.VersionUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class DocumentInfoTest {

    @Rule public final OverrideFlagsRule mOverrideFlagsRule = new OverrideFlagsRule();
    @Rule
    public ProviderTestRule mProviderTestRule = new ProviderTestRule.Builder(
            InspectorProvider.class, InspectorProvider.AUTHORITY).build();

    private static final DocumentInfo TEST_DOC
            = createDocInfo("authority.a", "doc.1", "text/plain");
    private static final String FOLDER_NAME = "Top";
    private static final String FILE_NAME = InspectorProvider.OPEN_IN_PROVIDER_TEST;

    private ContentResolver mResolver;


    @Before
    public void setUp() throws Exception {
        mResolver = mProviderTestRule.getResolver();
    }

    private static DocumentInfo createDocInfo(String authority, String docId, String mimeType) {
        DocumentInfo doc = new DocumentInfo();
        doc.userId = UserId.DEFAULT_USER;
        doc.authority = authority;
        doc.documentId = docId;
        doc.mimeType = mimeType;
        doc.deriveFields();
        return doc;
    }

    @Test
    public void testEquals() throws Exception {
        assertEquals(TEST_DOC, TEST_DOC);
        assertEquals(TEST_DOC, createDocInfo("authority.a", "doc.1", "text/plain"));
    }

    @Test
    public void testEquals_HandlesNulls() throws Exception {
        assertFalse(TEST_DOC.equals(null));
    }

    @Test
    public void testEquals_HandlesNullFields() throws Exception {
        assertFalse(TEST_DOC.equals(new DocumentInfo()));
        assertFalse(new DocumentInfo().equals(TEST_DOC));
    }

    @Test
    public void testNotEquals_differentUser() throws Exception {
        DocumentInfo documentInfo1 = createDocInfo("authority.a", "doc.1", "text/plain");
        DocumentInfo documentInfo2 = createDocInfo("authority.a", "doc.1", "text/plain");
        documentInfo1.userId = UserId.of(documentInfo2.userId.getIdentifier() + 1);
        assertFalse(documentInfo1.equals(documentInfo2));
    }

    @Test
    public void testNotEquals_differentAuthority() throws Exception {
        assertFalse(TEST_DOC.equals(createDocInfo("authority.b", "doc.1", "text/plain")));
    }

    @Test
    public void testNotEquals_differentDocId() throws Exception {
        assertFalse(TEST_DOC.equals(createDocInfo("authority.a", "doc.2", "text/plain")));
    }

    @Test
    public void testNotEquals_differentMimetype() throws Exception {
        assertFalse(TEST_DOC.equals(createDocInfo("authority.a", "doc.1", "image/png")));
    }

    @Test
    public void testFolderMimeTypeFromUri() throws Exception {
        final Uri validUri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, FOLDER_NAME);

        final Set<String> mimeTypes = new HashSet<>();
        DocumentInfo.addMimeTypes(mResolver, validUri, mimeTypes);

        assertThat(mimeTypes.size()).isEqualTo(1);

        assertThat(mimeTypes.contains(MIME_TYPE_DIR)).isTrue();
    }

    @Test
    public void testFileMimeTypeFromUri() throws Exception {
        final Uri validUri = DocumentsContract.buildDocumentUri(
                InspectorProvider.AUTHORITY, FILE_NAME);

        final Set<String> mimeTypes = new HashSet<>();
        DocumentInfo.addMimeTypes(mResolver, validUri, mimeTypes);

        assertThat(mimeTypes.size()).isEqualTo(1);

        assertThat(mimeTypes.contains("text/plain")).isTrue();
    }

    @Test
    public void testGetTreeDocumentUri_currentUser() {
        checkArgument(UserId.CURRENT_USER.equals(TEST_DOC.userId));

        assertThat(TEST_DOC.getTreeDocumentUri())
                .isEqualTo(DocumentsContract.buildTreeDocumentUri(TEST_DOC.authority,
                        TEST_DOC.documentId));
    }

    @Test
    public void testGetTreeDocumentUri_otherUser_shouldHaveDifferentUri() {
        if (VersionUtils.isAtLeastR()) {
            final DocumentInfo doc = createDocInfo("authority.a", "doc.1", "text/plain");
            final DocumentInfo otherUserDoc = createDocInfo("authority.a", "doc.1", "text/plain");
            otherUserDoc.userId = TestProvidersAccess.OtherUser.USER_ID;

            // Make sure they do not return the same tree uri
            assertThat(otherUserDoc.getTreeDocumentUri()).isNotEqualTo(doc.getTreeDocumentUri());
        }
    }

    @Test
    public void testGetTreeDocumentUri_otherUser_sameHostAndPath() {
        if (VersionUtils.isAtLeastR()) {
            final DocumentInfo doc = createDocInfo("authority.a", "doc.1", "text/plain");
            final DocumentInfo otherUserDoc = createDocInfo("authority.a", "doc.1", "text/plain");
            otherUserDoc.userId = TestProvidersAccess.OtherUser.USER_ID;

            // They should have same host(authority without user info) and path
            assertThat(otherUserDoc.getTreeDocumentUri().getHost())
                    .isEqualTo(doc.getTreeDocumentUri().getHost());
            assertThat(otherUserDoc.getTreeDocumentUri().getPath())
                    .isEqualTo(doc.getTreeDocumentUri().getPath());
        }
    }

    @Test
    public void testGetTreeDocumentUri_otherUser_userInfo() {
        if (VersionUtils.isAtLeastR()) {
            final DocumentInfo doc = createDocInfo("authority.a", "doc.1", "text/plain");
            final DocumentInfo otherUserDoc = createDocInfo("authority.a", "doc.1", "text/plain");
            otherUserDoc.userId = TestProvidersAccess.OtherUser.USER_ID;

            // Different user info between doc and otherUserDoc
            assertThat(otherUserDoc.getTreeDocumentUri().getUserInfo())
                    .isNotEqualTo(doc.getTreeDocumentUri().getUserInfo());

            // Same user info within otherUserDoc
            assertThat(otherUserDoc.getTreeDocumentUri().getUserInfo())
                    .isEqualTo(otherUserDoc.getDocumentUri().getUserInfo());
        }
    }

    @Test
    public void testTextFile() throws Exception {
        final DocumentInfo doc = createDocInfo("authority.a", "doc.1", "text/plain");
        assertThat(doc.isArchive()).isFalse();
        assertThat(doc.isContainer()).isFalse();
        assertThat(doc.isDirectory()).isFalse();
        assertThat(doc.isInArchive()).isFalse();
    }

    @Test
    public void testDirectory() throws Exception {
        final DocumentInfo doc = createDocInfo("authority.a", "doc.1", MIME_TYPE_DIR);
        assertThat(doc.isArchive()).isFalse();
        assertThat(doc.isContainer()).isTrue();
        assertThat(doc.isDirectory()).isTrue();
        assertThat(doc.isInArchive()).isFalse();
    }

    @Test
    public void testArchive() throws Exception {
        final DocumentInfo doc = createDocInfo("authority.a", "doc.1", "application/zip");
        assertThat(doc.isArchive()).isTrue();
        assertThat(doc.isContainer()).isTrue();
        assertThat(doc.isDirectory()).isFalse();
        assertThat(doc.isInArchive()).isFalse();
    }

    @Test
    public void testTextFileInArchive() throws Exception {
        final DocumentInfo doc = createDocInfo(ArchivesProvider.AUTHORITY, "doc.1", "text/plain");
        assertThat(doc.isArchive()).isFalse();
        assertThat(doc.isContainer()).isFalse();
        assertThat(doc.isDirectory()).isFalse();
        assertThat(doc.isInArchive()).isTrue();
    }

    @Test
    public void testDirectoryInArchive() throws Exception {
        final DocumentInfo doc = createDocInfo(ArchivesProvider.AUTHORITY, "doc.1", MIME_TYPE_DIR);
        assertThat(doc.isArchive()).isFalse();
        assertThat(doc.isContainer()).isTrue();
        assertThat(doc.isDirectory()).isTrue();
        assertThat(doc.isInArchive()).isTrue();
    }

    @Test
    public void testArchiveInArchive() throws Exception {
        final DocumentInfo doc = createDocInfo(ArchivesProvider.AUTHORITY, "doc.1",
                "application/zip");
        assertThat(doc.isArchive()).isTrue();
        assertThat(doc.isContainer()).isFalse();
        assertThat(doc.isDirectory()).isFalse();
        assertThat(doc.isInArchive()).isTrue();
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testUpdateFromCursor_syncStateFlags_flagEnabled() {
        Cursor cursor = mock(Cursor.class);
        int index = 1;
        int value = 2;

        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.getInt(index)).thenReturn(value);
        DocumentInfo info = new DocumentInfo();
        info.updateFromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertEquals(info.syncStateFlags.intValue(), value);
    }

    @Test
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testUpdateFromCursor_syncStateFlags_flagDisabled() {
        Cursor cursor = mock(Cursor.class);
        verify(cursor, never()).getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS);
    }

    @Test
    public void testWriteRead_syncStateFlags_null() throws IOException {
        // Write info to output.
        DocumentInfo info = new DocumentInfo();
        info.displayName = "file";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        info.write(new DataOutputStream(out));

        // Read input to info2.
        DocumentInfo info2 = new DocumentInfo();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        info2.read(input);

        assert (info.equals(info2));
    }

    @Test
    public void testWriteRead_syncStateFlags_nonNull() throws IOException {
        // Write info to output.
        DocumentInfo info = new DocumentInfo();
        info.displayName = "file";
        info.syncStateFlags = 1;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        info.write(new DataOutputStream(out));

        // Read input to info2.
        DocumentInfo info2 = new DocumentInfo();
        DataInputStream input = new DataInputStream(new ByteArrayInputStream(out.toByteArray()));
        info2.read(input);

        assert (info.equals(info2));
    }

    @Test
    public void testGetCursorInt() {
        Cursor cursor = mock(Cursor.class);
        String columnName = "column";
        int index = 0;
        int value = 5;
        when(cursor.getInt(index)).thenReturn(value);

        // When cursor is null, the default value should be returned.
        assertThat(DocumentInfo.getCursorInt(null, columnName)).isEqualTo(0);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                null, columnName, /* returnIfMissingOrNull= */ -10))
                .isEqualTo(-10);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                null, columnName, /* returnIfMissingOrNull= */ null))
                .isEqualTo(null);

        // When the column has no index (-1), the default value should be returned.
        when(cursor.getColumnIndex(columnName)).thenReturn(-1);
        assertThat(DocumentInfo.getCursorInt(cursor, columnName)).isEqualTo(0);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                cursor, columnName, /* returnIfMissingOrNull= */ -10))
                .isEqualTo(-10);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                null, columnName, /* returnIfMissingOrNull= */ null))
                .isEqualTo(null);

        // When the column's value is null, the default value should be returned.
        when(cursor.getColumnIndex(columnName)).thenReturn(index);
        when(cursor.isNull(index)).thenReturn(true);
        assertThat(DocumentInfo.getCursorInt(cursor, columnName)).isEqualTo(0);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                cursor, columnName, /* returnIfMissingOrNull= */ -10))
                .isEqualTo(-10);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                cursor, columnName, /* returnIfMissingOrNull= */ null))
                .isEqualTo(null);

        // When the column has a non-null value, the column's value should be returned.
        when(cursor.getColumnIndex(columnName)).thenReturn(index);
        when(cursor.isNull(index)).thenReturn(false);
        assertThat(DocumentInfo.getCursorInt(cursor, columnName)).isEqualTo(value);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                cursor, columnName, /* returnIfMissingOrNull= */ -10))
                .isEqualTo(value);
        assertThat(
                        DocumentInfo.getCursorInteger(
                                cursor, columnName, /* returnIfMissingOrNull= */ null))
                .isEqualTo(value);
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHasSyncState() {
        // No sync state when the column doesn't exist.
        Cursor cursor = mock(Cursor.class);
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(-1);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasSyncState());

        // No sync state when the column value is null.
        int index = 1;
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.isNull(index)).thenReturn(true);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasSyncState());

        // Sync state when the column value is set.
        when(cursor.isNull(index)).thenReturn(false);
        when(cursor.getInt(index)).thenReturn(2);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasSyncState());
    }

    @Test
    @DisableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHasSyncState_featureDisabled() {
        Cursor cursor = mock(Cursor.class);
        int index = 1;

        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.getInt(index)).thenReturn(2);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasSyncState());
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHasSyncInProgress() {
        // No sync in progress when column value doesn't contain the right flags.
        Cursor cursor = mock(Cursor.class);
        int index = 1;
        int value = 0;
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.getInt(index)).thenReturn(value);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasSyncInProgress());

        // Sync in progress when column value includes SYNC_STATE_FLAG_UPLOAD_PROGRESS.
        value = SYNC_STATE_FLAG_UPLOAD_PROGRESS;
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasSyncInProgress());

        // Sync in progress when column value includes SYNC_STATE_FLAG_DOWNLOAD_PROGRESS.
        value = SYNC_STATE_FLAG_DOWNLOAD_PROGRESS | SYNC_STATE_FLAG_LOCAL_CHANGES;
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasSyncInProgress());
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHasSyncError() {
        // No sync error when column value doesn't contain the right flags.
        Cursor cursor = mock(Cursor.class);
        int index = 1;
        int value = 0;
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.getInt(index)).thenReturn(value);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasSyncError());

        // Sync error when column value includes SYNC_STATE_FLAG_UPLOAD_ERROR.
        value = SYNC_STATE_FLAG_UPLOAD_ERROR;
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasSyncError());

        // Sync error when column value includes SYNC_STATE_FLAG_DOWNLOAD_ERROR.
        value = SYNC_STATE_FLAG_DOWNLOAD_ERROR | SYNC_STATE_FLAG_AVAILABLE_LOCALLY;
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasSyncError());
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testHasLocalChanges() {
        // No local changes when column value is null.
        Cursor cursor = mock(Cursor.class);
        int index = 1;
        int value = 0;
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS)).thenReturn(index);
        when(cursor.isNull(index)).thenReturn(true);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasLocalChanges());

        // No local changes when column value doesn't contain the right flag.
        when(cursor.isNull(index)).thenReturn(false);
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.hasLocalChanges());

        // Local changes when column value includes SYNC_STATE_FLAG_LOCAL_CHANGES.
        value = SYNC_STATE_FLAG_LOCAL_CHANGES | SYNC_STATE_FLAG_AVAILABLE_LOCALLY;
        when(cursor.getInt(index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.hasLocalChanges());
    }

    @Test
    @EnableFlags(Flags.FLAG_CLOUD_FEATURES)
    public void testIsContentAvailableLocally() {
        // Available locally when column value is null.
        Cursor cursor = mock(Cursor.class);
        int sync_index = 1;
        int value = 0;
        when(cursor.getColumnIndex(DocumentInfo.COLUMN_CONTENT_SYNC_STATE_FLAGS))
                .thenReturn(sync_index);
        when(cursor.isNull(sync_index)).thenReturn(true);
        DocumentInfo info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.isContentAvailableLocally());

        // Not available locally when column value doesn't contain the right flag.
        when(cursor.isNull(sync_index)).thenReturn(false);
        when(cursor.getInt(sync_index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertFalse(info.isContentAvailableLocally());

        // Available locally when document is virtual.
        int flag_index = 1;
        when(cursor.getColumnIndex(DocumentsContract.Document.COLUMN_FLAGS)).thenReturn(flag_index);
        when(cursor.getInt(flag_index))
                .thenReturn(DocumentsContract.Document.FLAG_VIRTUAL_DOCUMENT);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.isContentAvailableLocally());

        // Available locally when column value includes SYNC_STATE_FLAG_AVAILABLE_LOCALLY.
        value = SYNC_STATE_FLAG_AVAILABLE_LOCALLY | SYNC_STATE_FLAG_LOCAL_CHANGES;
        when(cursor.getInt(flag_index)).thenReturn(0);
        when(cursor.getInt(sync_index)).thenReturn(value);
        info = DocumentInfo.fromCursor(cursor, UserId.DEFAULT_USER, "authority.a");
        assertTrue(info.isContentAvailableLocally());
    }
}
