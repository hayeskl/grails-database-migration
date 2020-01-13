package org.grails.plugins.databasemigration.liquibase

import liquibase.configuration.GlobalConfiguration
import liquibase.configuration.LiquibaseConfiguration
import liquibase.resource.ClassLoaderResourceAccessor

import java.util.jar.JarEntry
import java.util.jar.JarFile

class GrailsClassLoaderResourceAccessor extends ClassLoaderResourceAccessor {
    GrailsClassLoaderResourceAccessor() {
        super()
    }

    GrailsClassLoaderResourceAccessor(ClassLoader classLoader) {
        super(classLoader)
    }


    @Override
    Set<String> list(String relativeTo, String path, boolean includeFiles, boolean includeDirectories, boolean recursive) throws IOException {
        path = convertToPath(relativeTo, path)

        Enumeration<URL> fileUrls = toClassLoader().getResources(path)

        Set<String> returnSet = new HashSet<String>()

        if (!fileUrls.hasMoreElements() && (path.startsWith("jar:") || path.startsWith("file:") || path.startsWith("wsjar:file:") || path.startsWith("zip:"))) {
            fileUrls = new Vector<URL>(Collections.singletonList(new URL(path))).elements()
        }

        while (fileUrls.hasMoreElements()) {
            URL fileUrl = fileUrls.nextElement()

            if (fileUrl.toExternalForm().startsWith("jar:file:")
                    || fileUrl.toExternalForm().startsWith("wsjar:file:")
                    || fileUrl.toExternalForm().startsWith("zip:")) {

                String[] zipAndFile = fileUrl.getFile().split("!")
                String zipFilePath = zipAndFile[0]
                if (zipFilePath.matches("file:/[A-Za-z]:/.*")) {
                    zipFilePath = zipFilePath.replaceFirst("file:/", "")
                } else {
                    zipFilePath = zipFilePath.replaceFirst("file:", "")
                }
                zipFilePath = URLDecoder.decode(zipFilePath, LiquibaseConfiguration.getInstance().getConfiguration(GlobalConfiguration.class).getOutputEncoding())

                // In some cases, there might be two ! in the path, such as when using spring-boot with mult-module projects
                // In that case the file path will be something like "jar:file:foo/bar/project.war!/WEB-INF/classes!/dbmigrations/"
                String extendedPath = null
                if (zipAndFile.length == 3) {
                    extendedPath = zipAndFile[1]
                    if (extendedPath.startsWith("/")) {
                        extendedPath = extendedPath.substring(1)
                    }
                    if (!extendedPath.endsWith("/")) {
                        extendedPath = extendedPath + "/"
                    }
                }

                if (path.startsWith("classpath:")) {
                    path = path.replaceFirst("classpath:", "")
                }
                if (path.startsWith("classpath*:")) {
                    path = path.replaceFirst("classpath\\*:", "")
                }

                // TODO:When we update to Java 7+, we can can create a FileSystem from the JAR (zip)
                // file, and then use NIO's directory walking and filtering mechanisms to search through it.
                //
                // As of 2016-02-03, Liquibase is Java 6+ (1.6)

                // java.util.JarFile has a slightly nicer interface than ZipInputStream here and
                // it works for zip files as well as JAR files
                JarFile zipfile = new JarFile(zipFilePath, false)

                try {
                    Enumeration<JarEntry> entries = zipfile.entries()
                    while (entries.hasMoreElements()) {
                        JarEntry entry = entries.nextElement()

                        // If we had an extended path attribute (from spring-boot most likely)
                        // then remove it for the match checking.
                        String entryName = entry.getName()
                        if (extendedPath != null) {
                            entryName = entryName.replaceAll(extendedPath, "")
                        }

                        if (entryName.startsWith(path)) {
                            if (!recursive) {
                                String pathAsDir = path.endsWith("/") ? path : path + "/"
                                if (!entryName.startsWith(pathAsDir) || entryName.substring(pathAsDir.length()).contains("/")) {
                                    continue
                                }
                            }

                            if (entry.isDirectory() && includeDirectories) {
                                returnSet.add(entryName)
                            } else if (includeFiles) {
                                returnSet.add(entryName)
                            }
                        }
                    }
                } finally {
                    zipfile.close()
                }
            } else {
                try {
                    File file = new File(fileUrl.toURI())
                    if (file.exists()) {
                        getContents(file, recursive, includeFiles, includeDirectories, path, returnSet)
                    }
                } catch (URISyntaxException ignored) {
                    //not a local file
                } catch (IllegalArgumentException ignored) {
                    //not a local file
                }
            }

            Enumeration<URL> resources = toClassLoader().getResources(path)

            while (resources.hasMoreElements()) {
                String url = resources.nextElement().toExternalForm()
                url = url.replaceFirst("^\\Q" + path + "\\E", "")
                returnSet.add(url)
            }
        }

        if (returnSet.size() == 0) {
            return null
        }
        return returnSet
    }

}
