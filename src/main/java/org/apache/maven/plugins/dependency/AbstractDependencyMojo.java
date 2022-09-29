package org.apache.maven.plugins.dependency;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.DependencySilentLog;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.archiver.manager.NoSuchArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.components.io.fileselectors.IncludeExcludeFileSelector;
import org.codehaus.plexus.util.ReflectionUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public abstract class AbstractDependencyMojo
        implements Mojo
{
    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter( defaultValue = "${reactorProjects}", readonly = true )
    protected List<Project> reactorProjects;
    /**
     * The Maven session
     */
    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    protected Session session;
    /**
     * Output absolute filename for resolved artifacts
     *
     * @since 2.0
     */
    @Parameter( property = "outputAbsoluteArtifactFilename", defaultValue = "false" )
    protected boolean outputAbsoluteArtifactFilename;
    /**
     * To look up Archiver/UnArchiver implementations
     */
    @Component
    private ArchiverManager archiverManager;
    /**
     * <p>
     * will use the jvm chmod, this is available for user and all level group level will be ignored
     * </p>
     * <b>since 2.6 is on by default</b>
     *
     * @since 2.5.1
     */
    @Parameter( property = "dependency.useJvmChmod", defaultValue = "true" )
    private boolean useJvmChmod = true;
    /**
     * ignore to set file permissions when unpacking a dependency
     *
     * @since 2.7
     */
    @Parameter( property = "dependency.ignorePermissions", defaultValue = "false" )
    private boolean ignorePermissions;
    /**
     * POM
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;
    /**
     * Remote repositories which will be searched for artifacts.
     */
    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> remoteRepositories;
    /**
     * Remote repositories which will be searched for plugins.
     */
    @Parameter( defaultValue = "${project.remotePluginRepositories}", readonly = true, required = true )
    private List<RemoteRepository> remotePluginRepositories;
    /**
     * If the plugin should be silent.
     *
     * @since 2.0
     */
    @Parameter( property = "silent", defaultValue = "false" )
    private boolean silent;
    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter( property = "mdep.skip", defaultValue = "false" )
    private boolean skip;

    @Component
    private Log log;

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public final void execute()
            throws MojoException
    {
        if ( skip )
        {
            getLog().info( "Skipping plugin execution" );
            return;
        }

        doExecute();
    }

    /**
     * @throws MojoException {@link MojoException}
     */
    protected abstract void doExecute()
            throws MojoException;

    /**
     * @return Returns the archiverManager.
     */
    public ArchiverManager getArchiverManager()
    {
        return this.archiverManager;
    }

    /**
     * @param archiverManager The archiverManager to set.
     */
    public void setArchiverManager( ArchiverManager archiverManager )
    {
        this.archiverManager = archiverManager;
    }

    public Log getLog()
    {
        return log;
    }

    /**
     * Does the actual copy of the file and logging.
     *
     * @param artifact represents the file to copy.
     * @param destFile file name of destination file.
     * @throws MojoException with a message if an error occurs.
     */
    protected void copyFile( Path artifact, Path destFile )
            throws MojoException
    {
        try
        {
            getLog().info( "Copying "
                    + ( this.outputAbsoluteArtifactFilename ? artifact.toAbsolutePath() : artifact.getFileName() )
                    + " to "
                    + destFile );

            if ( Files.isDirectory( artifact ) )
            {
                // usual case is a future jar packaging, but there are special cases: classifier and other packaging
                throw new MojoException( "Artifact has not been packaged yet. When used on reactor artifact, "
                        + "copy should be executed after packaging: see MDEP-187." );
            }

            if ( !Files.exists( artifact ) )
            {
                throw new IOException( "File " + artifact + " does not exist" );
            }
            else
            {
                Files.createDirectories( destFile.getParent() );
                Files.copy( artifact, destFile );
                if ( Files.size( artifact ) != Files.size( destFile ) )
                {
                    throw new IOException(  "Failed to copy full contents from " + artifact + " to " + destFile );
                }
            }
        }
        catch ( IOException e )
        {
            throw new MojoException( "Error copying artifact from " + artifact + " to " + destFile, e );
        }
    }

    /**
     * @param artifact    {@link Artifact}
     * @param location    The location.
     * @param encoding    The encoding.
     * @param fileMappers {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting
     *                    shall happen.
     * @throws MojoException in case of an error.
     */
    protected void unpack( Artifact artifact, Path location, String encoding, FileMapper[] fileMappers )
            throws MojoException
    {
        unpack( artifact, location, null, null, encoding, fileMappers );
    }

    /**
     * Unpacks the archive file.
     *
     * @param artifact    File to be unpacked.
     * @param location    Location where to put the unpacked files.
     * @param includes    Comma separated list of file patterns to include i.e. <code>**&#47;.xml,
     *                    **&#47;*.properties</code>
     * @param excludes    Comma separated list of file patterns to exclude i.e. <code>**&#47;*.xml,
     *                    **&#47;*.properties</code>
     * @param encoding    Encoding of artifact. Set {@code null} for default encoding.
     * @param fileMappers {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting
     *                    shall happen.
     * @throws MojoException In case of errors.
     */
    protected void unpack( Artifact artifact, Path location, String includes, String excludes, String encoding,
                           FileMapper[] fileMappers ) throws MojoException
    {
        unpack( artifact, artifact.getType().getName(), location, includes, excludes, encoding, fileMappers );
    }

    /**
     * @param artifact    {@link Artifact}
     * @param type        The type.
     * @param location    The location.
     * @param includes    includes list.
     * @param excludes    excludes list.
     * @param encoding    the encoding.
     * @param fileMappers {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting
     *                    shall happen.
     * @throws MojoException in case of an error.
     */
    protected void unpack( Artifact artifact, String type, Path location, String includes, String excludes,
                           String encoding, FileMapper[] fileMappers )
            throws MojoException
    {
        Path file = artifact.getPath().get();
        try
        {
            logUnpack( file, location, includes, excludes );

            try
            {
                Files.createDirectories( location );
            }
            catch ( IOException e )
            {
                // ignore
            }
            if ( !Files.isDirectory( location ) )
            {
                throw new MojoException( "Location to write unpacked files to could not be created: "
                        + location );
            }

            if ( Files.isDirectory( file ) )
            {
                // usual case is a future jar packaging, but there are special cases: classifier and other packaging
                throw new MojoException( "Artifact has not been packaged yet. When used on reactor artifact, "
                        + "unpack should be executed after packaging: see MDEP-98." );
            }

            UnArchiver unArchiver;

            try
            {
                unArchiver = archiverManager.getUnArchiver( type );
                getLog().debug( "Found unArchiver by type: " + unArchiver );
            }
            catch ( NoSuchArchiverException e )
            {
                unArchiver = archiverManager.getUnArchiver( file.toFile() );
                getLog().debug( "Found unArchiver by extension: " + unArchiver );
            }

            if ( encoding != null && unArchiver instanceof ZipUnArchiver )
            {
                ( (ZipUnArchiver) unArchiver ).setEncoding( encoding );
                getLog().info( "Unpacks '" + type + "' with encoding '" + encoding + "'." );
            }

            unArchiver.setIgnorePermissions( ignorePermissions );

            unArchiver.setSourceFile( file.toFile() );

            unArchiver.setDestDirectory( location.toFile() );

            if ( StringUtils.isNotEmpty( excludes ) || StringUtils.isNotEmpty( includes ) )
            {
                // Create the selectors that will filter
                // based on include/exclude parameters
                // MDEP-47
                IncludeExcludeFileSelector[] selectors =
                        new IncludeExcludeFileSelector[] {new IncludeExcludeFileSelector()};

                if ( StringUtils.isNotEmpty( excludes ) )
                {
                    selectors[0].setExcludes( excludes.split( "," ) );
                }

                if ( StringUtils.isNotEmpty( includes ) )
                {
                    selectors[0].setIncludes( includes.split( "," ) );
                }

                unArchiver.setFileSelectors( selectors );
            }
            if ( this.silent )
            {
                silenceUnarchiver( unArchiver );
            }

            unArchiver.setFileMappers( fileMappers );

            unArchiver.extract();
        }
        catch ( NoSuchArchiverException e )
        {
            throw new MojoException( "Unknown archiver type", e );
        }
        catch ( ArchiverException e )
        {
            throw new MojoException( "Error unpacking file: " + file + " to: " + location, e );
        }
    }

    private void silenceUnarchiver( UnArchiver unArchiver )
    {
        // dangerous but handle any errors. It's the only way to silence the unArchiver.
        try
        {
            Field field = ReflectionUtils.getFieldByNameIncludingSuperclasses( "logger", unArchiver.getClass() );

            field.setAccessible( true );

            field.set( unArchiver, this.getLog() );
        }
        catch ( Exception e )
        {
            // was a nice try. Don't bother logging because the log is silent.
        }
    }

    /**
     * @return Returns a new ProjectBuildingRequest populated from the current session and the current project remote
     * repositories, used to resolve artifacts.
     */
    public Session newResolveArtifactProjectBuildingRequest()
    {
        return newProjectBuildingRequest( remoteRepositories );
    }

    /**
     * @return Returns a new ProjectBuildingRequest populated from the current session and the current project remote
     * repositories, used to resolve plugins.
     */
    protected Session newResolvePluginProjectBuildingRequest()
    {
        return newProjectBuildingRequest( remotePluginRepositories );
    }

    private Session newProjectBuildingRequest( List<RemoteRepository> repositories )
    {
        return session.withRemoteRepositories( repositories );
    }

    /**
     * @return Returns the project.
     */
    public Project getProject()
    {
        return this.project;
    }

    /**
     * @return {@link #useJvmChmod}
     */
    public boolean isUseJvmChmod()
    {
        return useJvmChmod;
    }

    /**
     * @param useJvmChmod {@link #useJvmChmod}
     */
    public void setUseJvmChmod( boolean useJvmChmod )
    {
        this.useJvmChmod = useJvmChmod;
    }

    /**
     * @return {@link #silent}
     */
    protected final boolean isSilent()
    {
        return silent;
    }

    /**
     * @param silent {@link #silent}
     */
    public void setSilent( boolean silent )
    {
        this.silent = silent;
        if ( silent )
        {
            log = new DependencySilentLog();
        }
    }

    private void logUnpack( Path file, Path location, String includes, String excludes )
    {
        if ( !getLog().isInfoEnabled() )
        {
            return;
        }

        StringBuilder msg = new StringBuilder();
        msg.append( "Unpacking " );
        msg.append( file );
        msg.append( " to " );
        msg.append( location );

        if ( includes != null && excludes != null )
        {
            msg.append( " with includes \"" );
            msg.append( includes );
            msg.append( "\" and excludes \"" );
            msg.append( excludes );
            msg.append( "\"" );
        }
        else if ( includes != null )
        {
            msg.append( " with includes \"" );
            msg.append( includes );
            msg.append( "\"" );
        }
        else if ( excludes != null )
        {
            msg.append( " with excludes \"" );
            msg.append( excludes );
            msg.append( "\"" );
        }

        getLog().info( msg.toString() );
    }
}
