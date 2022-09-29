package org.apache.maven.plugins.dependency.fromConfiguration;

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

import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.dependency.utils.filters.MarkerFileFilter;
import org.apache.maven.plugins.dependency.utils.markers.MarkerHandler;
import org.apache.maven.plugins.dependency.utils.markers.UnpackFileMarkerHandler;
import org.codehaus.plexus.components.io.filemappers.FileMapper;
import org.codehaus.plexus.util.StringUtils;

/**
 * Goal that retrieves a list of artifacts from the repository and unpacks them in a defined location.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo( name = "unpack", defaultPhase = LifecyclePhase.PROCESS_SOURCES, requiresProject = false )
public class UnpackMojo
        extends AbstractFromConfigurationMojo
{

    /**
     * Directory to store flag files after unpack
     */
    @Parameter( defaultValue = "${project.build.directory}/dependency-maven-plugin-markers" )
    private Path markersDirectory;

    /**
     * A comma separated list of file patterns to include when unpacking the artifact. i.e.
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0-alpha-5
     */
    @Parameter( property = "mdep.unpack.includes" )
    private String includes;

    /**
     * A comma separated list of file patterns to exclude when unpacking the artifact. i.e. **\/*.xml,**\/*.properties
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0-alpha-5
     */
    @Parameter( property = "mdep.unpack.excludes" )
    private String excludes;

    /**
     * {@link FileMapper} to be used for rewriting each target path, or {@code null} if no rewriting shall happen.
     *
     * @since 3.1.2
     */
    @Parameter( property = "mdep.unpack.filemappers" )
    private FileMapper[] fileMappers;

    /**
     * The artifact to unpack from command line. A string of the form
     * <code>groupId:artifactId:version[:packaging[:classifier]]</code>. Use {@link #artifactItems} within the POM
     * configuration.
     */
    @SuppressWarnings( "unused" ) // marker-field, setArtifact(String) does the magic
    @Parameter( property = "artifact" )
    private String artifact;

    /**
     * Main entry into mojo. This method gets the ArtifactItems and iterates through each one passing it to
     * unpackArtifact.
     *
     * @throws MojoException with a message if an error occurs.
     * @see ArtifactItem
     * @see #getArtifactItems
     * @see #unpackArtifact(ArtifactItem)
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        verifyRequirements();

        List<ArtifactItem> processedItems = getProcessedArtifactItems( false );
        for ( ArtifactItem artifactItem : processedItems )
        {
            if ( artifactItem.isNeedsProcessing() )
            {
                unpackArtifact( artifactItem );
            }
            else
            {
                this.getLog().info( artifactItem.getArtifact().getPath().get().getFileName() + " already unpacked." );
            }
        }
    }

    /**
     * This method gets the Artifact object and calls DependencyUtil.unpackFile.
     *
     * @param artifactItem containing the information about the Artifact to unpack.
     * @throws MojoException with a message if an error occurs.
     * @see #getArtifact
     */
    private void unpackArtifact( ArtifactItem artifactItem )
            throws MojoException
    {
        MarkerHandler handler = new UnpackFileMarkerHandler( artifactItem, this.markersDirectory );

        unpack( artifactItem.getArtifact(), artifactItem.getType(), artifactItem.getOutputDirectory(),
                artifactItem.getIncludes(), artifactItem.getExcludes(), artifactItem.getEncoding(),
                artifactItem.getFileMappers() );
        handler.setMarker();
    }

    @Override
    Predicate<ArtifactItem> getMarkedArtifactFilter( ArtifactItem item )
    {
        MarkerHandler handler = new UnpackFileMarkerHandler( item, this.markersDirectory );

        return new MarkerFileFilter( this.isOverWriteReleases(), this.isOverWriteSnapshots(), this.isOverWriteIfNewer(),
                handler );
    }

    /**
     * @param removeVersion removeVersion.
     * @return list of {@link ArtifactItem}
     * @throws MojoException in case of an error.
     */
    protected List<ArtifactItem> getProcessedArtifactItems( boolean removeVersion )
            throws MojoException
    {
        List<ArtifactItem> items =
                super.getProcessedArtifactItems(
                        new ProcessArtifactItemsRequest( removeVersion, false, false, false ) );
        for ( ArtifactItem artifactItem : items )
        {
            if ( StringUtils.isEmpty( artifactItem.getIncludes() ) )
            {
                artifactItem.setIncludes( getIncludes() );
            }
            if ( StringUtils.isEmpty( artifactItem.getExcludes() ) )
            {
                artifactItem.setExcludes( getExcludes() );
            }
        }
        return items;
    }

    /**
     * @return Returns the markersDirectory.
     */
    public Path getMarkersDirectory()
    {
        return this.markersDirectory;
    }

    /**
     * @param theMarkersDirectory The markersDirectory to set.
     */
    public void setMarkersDirectory( Path theMarkersDirectory )
    {
        this.markersDirectory = theMarkersDirectory;
    }

    /**
     * @return Returns a comma separated list of excluded items
     */
    public String getExcludes()
    {
        return this.excludes;
    }

    /**
     * @param excludes A comma separated list of items to exclude i.e. **\/*.xml, **\/*.properties
     */
    public void setExcludes( String excludes )
    {
        this.excludes = excludes;
    }

    /**
     * @return Returns a comma separated list of included items
     */
    public String getIncludes()
    {
        return this.includes;
    }

    /**
     * @param includes A comma separated list of items to include i.e. **\/*.xml, **\/*.properties
     */
    public void setIncludes( String includes )
    {
        this.includes = includes;
    }

    /**
     * @return {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting shall
     * happen.
     * @since 3.1.2
     */
    public FileMapper[] getFileMappers()
    {
        return this.fileMappers;
    }

    /**
     * @param fileMappers {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no
     *                    rewriting shall happen.
     * @since 3.1.2
     */
    public void setFileMappers( FileMapper[] fileMappers )
    {
        this.fileMappers = fileMappers;
    }
}
