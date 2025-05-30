/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.archives;

import android.text.TextUtils;

import androidx.annotation.NonNull;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.ArchiveInputStream;
import org.apache.commons.compress.archivers.sevenz.SevenZFile;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;

/**
 * To simulate the input stream by using ZipFile, SevenZFile, or ArchiveInputStream.
 */
abstract class ArchiveEntryInputStream extends InputStream {
    private final long mSize;
    private final ReadSource mReadSource;

    private ArchiveEntryInputStream(ReadSource readSource, @NonNull ArchiveEntry archiveEntry) {
        mReadSource = readSource;
        mSize = archiveEntry.getSize();
    }

    @Override
    public int read() throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (mReadSource != null) {
            return mReadSource.read(b, off, len);
        }

        return -1; /* end of input stream */
    }

    @Override
    public int available() throws IOException {
        return mReadSource == null ? 0 : StrictMath.toIntExact(mSize);
    }

    /**
     * The interface describe how to make the ArchiveHandle to next entry.
     */
    private interface NextEntryIterator {
        ArchiveEntry getNextEntry() throws IOException;
    }

    /**
     * The interface provide where to read the data.
     */
    private interface ReadSource {
        int read(byte[] b, int off, int len) throws IOException;
    }

    private static boolean moveToArchiveEntry(
            NextEntryIterator nextEntryIterator, ArchiveEntry archiveEntry) throws IOException {
        ArchiveEntry entry;
        while ((entry = nextEntryIterator.getNextEntry()) != null) {
            if (TextUtils.equals(entry.getName(), archiveEntry.getName())) {
                return true;
            }
        }
        return false;
    }

    private static class WrapArchiveInputStream extends ArchiveEntryInputStream {
        private WrapArchiveInputStream(ReadSource readSource,
                ArchiveEntry archiveEntry, NextEntryIterator iterator) throws IOException {
            super(readSource, archiveEntry);

            moveToArchiveEntry(iterator, archiveEntry);
        }
    }

    private static class WrapZipFileInputStream extends ArchiveEntryInputStream {
        private final Closeable mCloseable;

        private WrapZipFileInputStream(
                ReadSource readSource, @NonNull ArchiveEntry archiveEntry, Closeable closeable)
                throws IOException {
            super(readSource, archiveEntry);
            mCloseable = closeable;
        }

        @Override
        public void close() throws IOException {
            super.close();
            if (mCloseable != null) {
                mCloseable.close();
            }
        }
    }

    static @NonNull InputStream create(@NonNull ArchiveHandle handle, @NonNull ArchiveEntry entry)
            throws IOException {
        if (handle == null) {
            throw new IllegalArgumentException("handle is null");
        }

        if (entry == null) {
            throw new IllegalArgumentException("entry is null");
        }

        if (entry.isDirectory() || entry.getSize() < 0 || TextUtils.isEmpty(entry.getName())) {
            throw new IllegalArgumentException("ArchiveEntry is an invalid file entry");
        }

        final Object archive = handle.getCommonArchive();

        if (archive instanceof SevenZFile) {
            final SevenZFile file = (SevenZFile) archive;
            return new WrapArchiveInputStream(file::read, entry, file::getNextEntry);
        }

        if (archive instanceof ZipFile) {
            final ZipFile file = (ZipFile) archive;
            InputStream stream = file.getInputStream((ZipArchiveEntry) entry);
            return new WrapZipFileInputStream(stream::read, entry, stream);
        }

        if (archive instanceof ArchiveInputStream) {
            final ArchiveInputStream stream = (ArchiveInputStream) archive;
            return new WrapArchiveInputStream(stream::read, entry, stream::getNextEntry);
        }

        throw new IllegalArgumentException("Unexpected archive type " + archive);
    }
}
