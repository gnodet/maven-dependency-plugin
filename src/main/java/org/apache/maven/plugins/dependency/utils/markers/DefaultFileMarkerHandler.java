package org.apache.maven.plugins.dependency.utils.markers;

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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactUtils;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class DefaultFileMarkerHandler
        implements MarkerHandler
{
    /**
     * The artifact.
     */
    protected Artifact artifact;

    /**
     * The marker directory.
     */
    protected Path markerFilesDirectory;

    /**
     * @param theMarkerFilesDirectory The marker directory.
     */
    public DefaultFileMarkerHandler( Path theMarkerFilesDirectory )
    {
        this.markerFilesDirectory = theMarkerFilesDirectory;
    }

    /**
     * @param theArtifact             {@link Artifact}
     * @param theMarkerFilesDirectory The marker directory.
     */
    public DefaultFileMarkerHandler( Artifact theArtifact, Path theMarkerFilesDirectory )
    {
        this.artifact = theArtifact;
        this.markerFilesDirectory = theMarkerFilesDirectory;
    }

    /**
     * Returns properly formatted File
     *
     * @return File object for marker. The file is not guaranteed to exist.
     */
    protected Path getMarkerFile()
    {
        return this.markerFilesDirectory.resolve( ArtifactUtils.getIdWithDashes( this.artifact ) + ".marker" );
    }

    /**
     * Tests whether the file or directory denoted by this abstract pathname exists.
     *
     * @return <code>true</code> if and only if the file or directory denoted by this abstract pathname exists;
     * <code>false</code> otherwise
     * @throws SecurityException If a security manager exists and its <code>{@link
     *                           java.lang.SecurityManager#checkRead(java.lang.String)}</code> method denies read access
     *                           to the file or
     *                           directory
     */
    @Override
    public boolean isMarkerSet()
            throws MojoException
    {
        Path marker = getMarkerFile();
        return Files.exists( marker );
    }

    @Override
    public boolean isMarkerOlder( Artifact artifact1 )
            throws MojoException
    {
        Path marker = getMarkerFile();
        if ( Files.exists( marker ) )
        {
            return isNewer( artifact1.getPath().get(), marker );
        }
        else
        {
            // if the marker doesn't exist, we want to copy so assume it is
            // infinitely older
            return true;
        }
    }

    @Override
    public void setMarker()
            throws MojoException
    {
        Path marker = getMarkerFile();
        // create marker file
        try
        {
            Files.createDirectories( marker.getParent() );
        }
        catch ( NullPointerException e )
        {
            // parent is null, ignore it.
        }
        catch ( IOException e )
        {
            throw new MojoException( "Unable to create directory: " + marker, e );
        }
        try
        {
            Files.createFile( marker );
        }
        catch ( IOException e )
        {
            throw new MojoException( "Unable to create Marker: " + marker, e );
        }

        // update marker file timestamp
        try
        {
            FileTime ts;
            if ( this.artifact != null && this.artifact.getPath().isPresent() )
            {
                ts = Files.getLastModifiedTime( this.artifact.getPath().get() );
            }
            else
            {
                ts = FileTime.from( Instant.now() );
            }
            try
            {
                Files.setLastModifiedTime( marker, ts );
            }
            catch ( IOException e )
            {
                throw new MojoException( "Unable to update last modified timestamp on marker file "
                        + marker.toAbsolutePath(), e );

            }
        }
        catch ( MojoException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new MojoException( "Unable to update Marker timestamp: " + marker.toAbsolutePath(), e );
        }
    }

    /**
     * Deletes the file or directory denoted by this abstract pathname. If this pathname denotes a directory, then the
     * directory must be empty in order to be deleted.
     *
     * @return <code>true</code> if and only if the file or directory is successfully deleted; <code>false</code>
     * otherwise
     * @throws SecurityException If a security manager exists and its <code>{@link
     *                           java.lang.SecurityManager#checkDelete}</code> method denies delete access to the file
     */
    @Override
    public boolean clearMarker()
            throws MojoException
    {
        Path marker = getMarkerFile();
        try
        {
            return Files.deleteIfExists( marker );
        }
        catch ( IOException e )
        {
            throw new MojoException( e );
        }
    }

    /**
     * @return Returns the artifact.
     */
    public Artifact getArtifact()
    {
        return this.artifact;
    }

    /**
     * @param artifact The artifact to set.
     */
    @Override
    public void setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
    }

    /**
     * @return Returns the markerFilesDirectory.
     */
    public Path getMarkerFilesDirectory()
    {
        return this.markerFilesDirectory;
    }

    /**
     * @param markerFilesDirectory The markerFilesDirectory to set.
     */
    public void setMarkerFilesDirectory( Path markerFilesDirectory )
    {
        this.markerFilesDirectory = markerFilesDirectory;
    }

    static boolean isNewer( Path p1, Path p2 )
    {
        try
        {
            return Files.getLastModifiedTime( p1 ).compareTo( Files.getLastModifiedTime( p2 ) ) > 0;
        }
        catch ( IOException e )
        {
            throw new MojoException( e );
        }
    }
}
