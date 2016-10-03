package com.cloudlabhk.CiMarking;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.*;


public class TarUtils {
    //http://www.programcreek.com/java-api-examples/index.php?source_dir=rce-master/main/de.rcenvironment.core.datamanagement/src/main/java/de/rcenvironment/core/datamanagement/internal/DataManagementServiceImpl.java
    private static final int BUFFER = 1024;

    public static void createDirectoryFromTarGz(InputStream archive, File targetDir) throws IOException {

        targetDir.mkdirs();

        //FileInputStream fileInStream = new FileInputStream(archive);
        BufferedInputStream bufferedInStream = new BufferedInputStream(archive);
        GzipCompressorInputStream gzipInStream;
        try {
            gzipInStream = new GzipCompressorInputStream(bufferedInStream);
        } catch (IOException e) {
            bufferedInStream.close();
            archive.close();
            throw e;
        }
        TarArchiveInputStream tarInStream = new TarArchiveInputStream(gzipInStream);
        try {
            createFileOrDirForTarEntry(tarInStream, targetDir);
        } finally {
            tarInStream.close();
            gzipInStream.close();
            bufferedInStream.close();
            archive.close();
        }
    }

    private static void createFileOrDirForTarEntry(TarArchiveInputStream tarInStream, File targetDir) throws IOException {

        TarArchiveEntry tarEntry = tarInStream.getNextTarEntry();
        if (tarEntry == null) {
            return;
        }
        File destPath = new File(targetDir, tarEntry.getName());
        if (tarEntry.isDirectory()) {
            destPath.mkdirs();
        } else {
            destPath.createNewFile();
            byte[] btoRead = new byte[BUFFER];
            BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(destPath));
            int len = 0;
            final int minusOne = -1;
            while ((len = tarInStream.read(btoRead)) != minusOne) {
                bout.write(btoRead, 0, len);
            }
            bout.close();
        }
        createFileOrDirForTarEntry(tarInStream, targetDir);
    }

}