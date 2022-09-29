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

import org.apache.maven.api.Artifact;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactUtils;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public class SourcesFileMarkerHandler
        extends DefaultFileMarkerHandler
{

    boolean resolved;

    /**
     * @param markerFilesDirectory the marker files directory.
     */
    public SourcesFileMarkerHandler( Path markerFilesDirectory )
    {
        super( markerFilesDirectory );
    }

    /**
     * @param artifact             {@link Artifact}
     * @param markerFilesDirectory marker files directory.
     * @param isResolved           true/false.
     */
    public SourcesFileMarkerHandler( Artifact artifact, Path markerFilesDirectory, boolean isResolved )
    {
        super( artifact, markerFilesDirectory );
        this.resolved = isResolved;
    }

    private static boolean delete( Path marker )
    {
        boolean markResult;
        try
        {
            Files.delete( marker );
            markResult = true;
        }
        catch ( IOException e )
        {
            markResult = false;
        }
        return markResult;
    }

    /**
     * Returns properly formatted File
     *
     * @return File object for marker. The file is not guaranteed to exist.
     */
    @Override
    public Path getMarkerFile()
    {
        return getMarkerFile( this.resolved );
    }

    /**
     * Get MarkerFile, exposed for unit testing purposes
     *
     * @param res resolved or not.
     * @return marker file for this artifact.
     */
    protected Path getMarkerFile( boolean res )
    {
        String suffix;
        if ( res )
        {
            suffix = ".resolved";
        }
        else
        {
            suffix = ".unresolved";
        }

        return this.markerFilesDirectory.resolve( ArtifactUtils.getIdWithDashes( this.artifact ) + suffix );
    }

    /**
     * Tests whether the file or directory denoted by this abstract pathname exists.
     *
     * @return <code>true</code> if and only if the file or directory denoted by this abstract pathname exists;
     * <code>false</code> otherwise
     * @throws MojoException If a security manager exists and its <code>{@link
     *                       java.lang.SecurityManager#checkRead(java.lang.String)}</code> method denies read access to
     *                       the file or
     *                       directory
     */
    @Override
    public boolean isMarkerSet()
            throws MojoException
    {
        Path marker = getMarkerFile();

        Path marker2 = getMarkerFile( !this.resolved );

        return Files.exists( marker ) || Files.exists( marker2 );
    }

    @Override
    public boolean isMarkerOlder( Artifact theArtifact )
            throws MojoException
    {
        Path marker = getMarkerFile();
        if ( Files.exists( marker ) )
        {
            return isNewer( theArtifact.getPath().get(), marker );
        }
        else
        {
            marker = getMarkerFile( !this.resolved );
            if ( Files.exists( marker ) )
            {
                return isNewer( theArtifact.getPath().get(), marker );
            }
            else
            {
                // if the marker doesn't exist, we want to copy so assume it is
                // infinitely older
                return true;
            }
        }
    }

    @Override
    public void setMarker()
            throws MojoException
    {
        Path marker = getMarkerFile();

        // get the other file if it exists.
        Path clearMarker = getMarkerFile( !this.resolved );
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
            throw new MojoException( "Unable to create directory: " + marker.getParent(), e );
        }

        try
        {
            Files.createFile( marker );
            // clear the other file if it exists.
            Files.deleteIfExists( clearMarker );
        }
        catch ( IOException e )
        {
            throw new MojoException( "Unable to create Marker: " + marker.toAbsolutePath(), e );
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
        Path marker2 = getMarkerFile( !this.resolved );
        boolean markResult = delete( marker );
        boolean mark2Result = delete( marker2 );
        return markResult || mark2Result;
    }

    /**
     * @return Returns the resolved.
     */
    public boolean isResolved()
    {
        return this.resolved;
    }

    /**
     * @param isResolved The resolved to set.
     */
    public void setResolved( boolean isResolved )
    {
        this.resolved = isResolved;
    }
}
