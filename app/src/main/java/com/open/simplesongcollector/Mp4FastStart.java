package com.open.simplesongcollector;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

/**
 * Relocates the MOOV atom to the beginning of an MP4/M4A file.
 * This is equivalent to ffmpeg's -movflags faststart and is required
 * for jaudiotagger to parse the file for ID3 tagging.
 */
public class Mp4FastStart {

    public static void process(File file) throws IOException {
        File tempFile = new File(file.getParent(), file.getName() + ".tmp");

        try (FileInputStream fis = new FileInputStream(file);
             FileChannel inChannel = fis.getChannel()) {

            long fileSize = inChannel.size();
            long pos = 0;

            // Parse top-level atoms to find ftyp, mdat, moov positions and sizes
            long ftypPos = -1, ftypSize = 0;
            long mdatPos = -1, mdatSize = 0;
            long moovPos = -1, moovSize = 0;

            while (pos < fileSize) {
                ByteBuffer header = ByteBuffer.allocate(8);
                inChannel.position(pos);
                if (inChannel.read(header) < 8) break;
                header.flip();

                long atomSize = header.getInt() & 0xFFFFFFFFL;
                byte[] typeBytes = new byte[4];
                header.get(typeBytes);
                String atomType = new String(typeBytes);

                if (atomSize == 1) {
                    ByteBuffer extSize = ByteBuffer.allocate(8);
                    inChannel.read(extSize);
                    extSize.flip();
                    atomSize = extSize.getLong();
                }

                if (atomSize == 0) {
                    atomSize = fileSize - pos;
                }

                System.out.println("Mp4FastStart: atom '" + atomType + "' at " + pos + " size " + atomSize);

                switch (atomType) {
                    case "ftyp": ftypPos = pos; ftypSize = atomSize; break;
                    case "mdat": mdatPos = pos; mdatSize = atomSize; break;
                    case "moov": moovPos = pos; moovSize = atomSize; break;
                }

                pos += atomSize;
            }

            if (moovPos < 0 || mdatPos < 0) {
                throw new IOException("Could not find moov or mdat atoms");
            }

            if (moovPos < mdatPos) {
                System.out.println("Mp4FastStart: moov already before mdat, skipping");
                return;
            }

            // Calculate how much mdat shifts in the new layout
            // New layout: ftyp + moov + mdat
            // Old mdat position: mdatPos
            // New mdat position: ftypSize + moovSize
            long newMdatPos = (ftypPos >= 0 ? ftypSize : 0) + moovSize;
            long offsetShift = newMdatPos - mdatPos;
            System.out.println("Mp4FastStart: mdatPos=" + mdatPos + " newMdatPos=" + newMdatPos + " offsetShift=" + offsetShift);

            // Read the moov atom and strip non-essential atoms (udta, meta)
            // that MediaMuxer adds with device info. jaudiotagger can't parse
            // the meta full-box format and will create its own udta/meta/ilst for tags.
            byte[] moovBytes = new byte[(int) moovSize];
            inChannel.position(moovPos);
            ByteBuffer moovBuf = ByteBuffer.wrap(moovBytes);
            inChannel.read(moovBuf);

            byte[] cleanMoov = stripAtoms(moovBytes, (int) moovSize, "udta", "meta");
            long cleanMoovSize = cleanMoov.length;

            // Recalculate offset shift with cleaned moov size + free padding
            long newFtypSize = 32; // we write a fixed M4A ftyp
            int freePadding = 32768; // padding for jaudiotagger to write metadata in-place (needs room for artwork)
            long actualNewMdatPos = newFtypSize + cleanMoovSize + freePadding;
            long actualOffsetShift = actualNewMdatPos - mdatPos;
            System.out.println("Mp4FastStart: cleanMoovSize=" + cleanMoovSize + " actualNewMdatPos=" + actualNewMdatPos + " actualOffsetShift=" + actualOffsetShift);

            // Update chunk offsets in cleaned moov
            updateChunkOffsets(cleanMoov, cleanMoov.length, actualOffsetShift);

            // Write: ftyp + moov + mdat
            try (FileOutputStream fos = new FileOutputStream(tempFile);
                 FileChannel outChannel = fos.getChannel()) {

                // Write M4A-compatible ftyp matching the format jaudiotagger expects
                byte[] ftyp = new byte[] {
                    0x00, 0x00, 0x00, 0x20,                         // size = 32
                    'f', 't', 'y', 'p',                             // type
                    'M', '4', 'A', ' ',                              // major brand
                    0x00, 0x00, 0x00, 0x00,                          // minor version
                    'm', 'p', '4', '2', 'm', 'p', '4', '1',        // compatible brands
                    'i', 's', 'o', 'm', 'i', 's', 'o', '2'
                };
                outChannel.write(ByteBuffer.wrap(ftyp));

                // Write cleaned moov (with updated offsets)
                outChannel.write(ByteBuffer.wrap(cleanMoov));

                // Write free padding atom for metadata space
                byte[] freeAtom = new byte[freePadding];
                writeInt(freeAtom, 0, freePadding);
                freeAtom[4] = 'f'; freeAtom[5] = 'r'; freeAtom[6] = 'e'; freeAtom[7] = 'e';
                outChannel.write(ByteBuffer.wrap(freeAtom));

                // Write mdat
                inChannel.position(mdatPos);
                long remaining = mdatSize;
                while (remaining > 0) {
                    long toTransfer = Math.min(remaining, 8 * 1024 * 1024);
                    long transferred = inChannel.transferTo(inChannel.position(), toTransfer, outChannel);
                    if (transferred == 0) break;
                    remaining -= transferred;
                    inChannel.position(inChannel.position() + transferred);
                }
            }
        }

        if (!file.delete() || !tempFile.renameTo(file)) {
            throw new IOException("Failed to replace original file with faststart version");
        }

        System.out.println("Mp4FastStart: moov atom moved to front successfully");
    }

    private static byte[] stripAtoms(byte[] moov, int moovSize, String... atomNames) {
        // Remove specified atoms from moov's direct children
        java.util.Set<String> toStrip = new java.util.HashSet<>(java.util.Arrays.asList(atomNames));
        byte[] result = moov;
        int currentSize = moovSize;

        boolean found = true;
        while (found) {
            found = false;
            int pos = 8; // skip moov header
            while (pos < currentSize - 8) {
                int atomSize = readInt(result, pos);
                String atomType = new String(result, pos + 4, 4);
                if (atomSize < 8 || pos + atomSize > currentSize) break;

                if (toStrip.contains(atomType)) {
                    System.out.println("Mp4FastStart: stripping " + atomType + " atom at " + pos + " size " + atomSize);
                    byte[] newResult = new byte[currentSize - atomSize];
                    System.arraycopy(result, 0, newResult, 0, pos);
                    System.arraycopy(result, pos + atomSize, newResult, pos, currentSize - pos - atomSize);
                    currentSize -= atomSize;
                    writeInt(newResult, 0, currentSize);
                    result = newResult;
                    found = true;
                    break; // restart scan since positions shifted
                }
                pos += atomSize;
            }
        }
        return result;
    }

    private static void updateChunkOffsets(byte[] moov, int moovLen, long shift) {
        // Recursively scan for stco and co64 atoms within the moov data
        int moovSize = readInt(moov, 0); // use the size from the moov header
        scanAndUpdate(moov, 8, moovSize, shift);
    }

    private static void scanAndUpdate(byte[] data, int start, int end, long shift) {
        int pos = start;
        while (pos < end - 8) {
            int atomSize = readInt(data, pos);
            String atomType = new String(data, pos + 4, 4);

            if (atomSize < 8 || pos + atomSize > end) {
                System.out.println("Mp4FastStart: bad atom size " + atomSize + " at " + pos + ", stopping scan");
                break;
            }

            if ("stco".equals(atomType)) {
                int entryCount = readInt(data, pos + 12);
                System.out.println("Mp4FastStart: found stco with " + entryCount + " entries at " + pos);
                for (int i = 0; i < entryCount; i++) {
                    int entryPos = pos + 16 + (i * 4);
                    long oldOffset = readInt(data, entryPos) & 0xFFFFFFFFL;
                    long newOffset = oldOffset + shift;
                    writeInt(data, entryPos, (int) newOffset);
                }
            } else if ("co64".equals(atomType)) {
                int entryCount = readInt(data, pos + 12);
                System.out.println("Mp4FastStart: found co64 with " + entryCount + " entries at " + pos);
                for (int i = 0; i < entryCount; i++) {
                    int entryPos = pos + 16 + (i * 8);
                    long oldOffset = readLong(data, entryPos);
                    writeLong(data, entryPos, oldOffset + shift);
                }
            }

            if (isContainerAtom(atomType)) {
                // Recurse into container, children start after the 8-byte header
                scanAndUpdate(data, pos + 8, pos + atomSize, shift);
            }

            pos += atomSize;
        }
    }

    private static boolean isContainerAtom(String type) {
        return "moov".equals(type) || "trak".equals(type) || "mdia".equals(type)
                || "minf".equals(type) || "stbl".equals(type) || "udta".equals(type)
                || "edts".equals(type) || "dinf".equals(type);
    }

    private static int readInt(byte[] data, int pos) {
        return ((data[pos] & 0xFF) << 24) | ((data[pos + 1] & 0xFF) << 16)
                | ((data[pos + 2] & 0xFF) << 8) | (data[pos + 3] & 0xFF);
    }

    private static void writeInt(byte[] data, int pos, int value) {
        data[pos] = (byte) (value >> 24);
        data[pos + 1] = (byte) (value >> 16);
        data[pos + 2] = (byte) (value >> 8);
        data[pos + 3] = (byte) value;
    }

    private static long readLong(byte[] data, int pos) {
        return ((long) readInt(data, pos) << 32) | (readInt(data, pos + 4) & 0xFFFFFFFFL);
    }

    private static void writeLong(byte[] data, int pos, long value) {
        writeInt(data, pos, (int) (value >> 32));
        writeInt(data, pos + 4, (int) value);
    }
}
