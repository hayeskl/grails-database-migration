package org.grails.plugins.databasemigration.liquibase

import liquibase.configuration.GlobalConfiguration
import liquibase.configuration.LiquibaseConfiguration
import liquibase.exception.UnexpectedLiquibaseException
import liquibase.resource.ClassLoaderResourceAccessor
import liquibase.util.SpringBootFatJar
import liquibase.util.StringUtils
import liquibase.util.file.FilenameUtils

import java.util.jar.JarEntry
import java.util.jar.JarFile
import java.util.jar.JarInputStream

class GrailsClassLoaderResourceAccessor extends ClassLoaderResourceAccessor {
    GrailsClassLoaderResourceAccessor() {
        super()
    }

    GrailsClassLoaderResourceAccessor(ClassLoader classLoader) {
        super(classLoader)
    }

    @Override
    protected String convertToPath(String relativeTo, String path) {
        if (StringUtils.trimToNull(relativeTo) == null) {
            return path;
        }
        URL baseUrl = toClassLoader().getResource(relativeTo);
        if (baseUrl == null) {
            throw new UnexpectedLiquibaseException("Cannot find base path '"+relativeTo+"'");
        }
        String base;
        if (baseUrl.toExternalForm().startsWith("file:")) {
            File baseFile = new File(baseUrl.getPath());
            if (!baseFile.exists()) {
                throw new UnexpectedLiquibaseException("Base file '" + baseFile.getAbsolutePath() + "' does not exist");
            }
            if (baseFile.isFile()) {
                baseFile = baseFile.getParentFile();
            }
            base = baseFile.toURI().getPath();
        } else if (baseUrl.toExternalForm().startsWith("jar:file:")) {
            return FilenameUtils.concat(FilenameUtils.getFullPath(baseUrl.toExternalForm()), path);
        } else {
            base = relativeTo;
        }
        String separator = "";
        if (!base.endsWith("/") && !path.startsWith("/")) {
            separator = "/";
        }
        if (base.endsWith("/") && path.startsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return convertToPath(base + separator + path);
    }

    @Override
    Set<String> list(String relativeTo, String path, boolean includeFiles, boolean includeDirectories, boolean recursive) throws IOException {
        return super.list(null, convertToPath(relativeTo, path), includeFiles, includeDirectories, recursive)
    }
}
