package org.gradle.samples.tasks

import groovy.io.FileType
import groovy.transform.CompileStatic
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.compress.archivers.ArchiveEntry
import org.apache.commons.compress.archivers.ArchiveInputStream
import org.apache.commons.compress.archivers.ArchiveStreamFactory
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.compressors.CompressorInputStream
import org.apache.commons.compress.compressors.CompressorStreamFactory
import org.apache.commons.compress.utils.IOUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime
import java.nio.file.attribute.PosixFilePermission
import java.security.MessageDigest;

@CompileStatic
class ExtractToolChainTask extends DefaultTask {
    @InputFiles
    @SkipWhenEmpty
    final RegularFileProperty toolChainArchive = newInputFile()

    final DirectoryProperty toolChainLocation = newOutputDirectory()

    @OutputFile
    protected File getDoneFile() {
        new File(toolChainLocation.get().asFile.absolutePath + ".done")
    }

    @TaskAction
    private void extract() {
        if (!isExtractUpToDate()) {
            doneFile.delete()
            FileUtils.deleteDirectory(toolChainLocation.get().asFile)

            InputStream fileStream = new BufferedInputStream(Files.newInputStream(toolChainArchive.get().asFile.toPath()))
            ArchiveInputStream archiveStream = createArchiveStream(new BufferedInputStream(createDecompressStream(fileStream)))

            File outputDir = toolChainLocation.asFile.get()
            try {
                for (ArchiveEntry entry = archiveStream.nextEntry; entry != null; entry = archiveStream.nextEntry) {
                    String entryPath = entry.getName().split("[\\/]").drop(1).join("/")
                    final File file = new File(outputDir, entryPath)
                    if (entry.isDirectory()) {
                        continue
                    }

                    file.getParentFile().mkdirs()
                    if (entry instanceof TarArchiveEntry && entry.isSymbolicLink()) {
                        Files.createSymbolicLink(file.toPath(), Paths.get(entry.getLinkName()))
                    } else if (entry instanceof TarArchiveEntry && !entry.getLinkName().isEmpty()) {
                        OutputStream outStream = file.newOutputStream()
                        String sourcePath = entry.getLinkName().split("[\\/]").drop(1).join("/")
                        File sourceFile = new File(outputDir, sourcePath)
                        InputStream inStream = sourceFile.newInputStream()
                        IOUtils.copy(inStream, outStream)
                        IOUtils.closeQuietly(inStream)
                        IOUtils.closeQuietly(outStream)

                        Files.setLastModifiedTime(file.toPath(), FileTime.from(entry.getLastModifiedDate().toInstant()))

                        if (entry instanceof TarArchiveEntry) {
                            file.setExecutable((entry.getMode() & toModeBit(PosixFilePermission.OWNER_EXECUTE)) > 0, true)
                        }
                    } else {
                        OutputStream outStream = file.newOutputStream()
                        IOUtils.copy(archiveStream, outStream)
                        IOUtils.closeQuietly(outStream)

                        Files.setLastModifiedTime(file.toPath(), FileTime.from(entry.getLastModifiedDate().toInstant()))

                        if (entry instanceof TarArchiveEntry) {
                            file.setExecutable((entry.getMode() & toModeBit(PosixFilePermission.OWNER_EXECUTE)) > 0, true)
                        }
                    }
                }
            } catch (EOFException ex) {
                // Ignore EOF exception
            } finally {
                IOUtils.closeQuietly(archiveStream)
            }

            doneFile.text = md5Hash(toolChainLocationFile)
        }
    }

    private static CompressorInputStream createDecompressStream(InputStream inputStream) {
        return new CompressorStreamFactory().createCompressorInputStream(inputStream)
    }

    private static ArchiveInputStream createArchiveStream(InputStream inputStream) {
        return new ArchiveStreamFactory().createArchiveInputStream(inputStream)
    }

    private static int toModeBit(PosixFilePermission permission) {
        return 1 << permission.ordinal()
    }

    private boolean isExtractUpToDate() {
        return toolChainLocationFile.exists() && doneFile.exists() && doneFile.text == md5Hash(toolChainLocationFile)
    }

    private File getToolChainLocationFile() {
        return toolChainLocation.get().asFile
    }

    private static String md5Hash(File dirToHash) {
        MessageDigest digest = DigestUtils.getDigest("MD5")
        dirToHash.eachFileRecurse(FileType.FILES) { File fileToHash ->
            digest.update(fileToHash.bytes)
        }
        return new String(digest.digest())
    }
}
