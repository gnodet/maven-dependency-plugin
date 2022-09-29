package org.apache.maven.plugins.dependency.fromDependencies;

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
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.plugins.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.MarkerFileFilter;
import org.apache.maven.plugins.dependency.utils.markers.DefaultFileMarkerHandler;
import org.codehaus.plexus.components.io.filemappers.FileMapper;

/**
 * Goal that unpacks the project dependencies from the repository to a defined location.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo( name = "unpack-dependencies",
       requiresDependencyResolution = ResolutionScope.TEST,
       defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class UnpackDependenciesMojo
        extends AbstractFromDependenciesMojo
{
    /**
     * A comma separated list of file patterns to include when unpacking the artifact. i.e.
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0
     */
    @Parameter( property = "mdep.unpack.includes" )
    private String includes;

    /**
     * A comma separated list of file patterns to exclude when unpacking the artifact. i.e.
     * <code>**&#47;*.xml,**&#47;*.properties</code> NOTE: Excludes patterns override the includes. (component code =
     * <code>return isIncluded( name ) AND !isExcluded( name );</code>)
     *
     * @since 2.0
     */
    @Parameter( property = "mdep.unpack.excludes" )
    private String excludes;

    /**
     * Encoding of artifacts.
     *
     * @since 3.0
     */
    @Parameter( property = "mdep.unpack.encoding" )
    private String encoding;

    /**
     * {@link FileMapper}s to be used for rewriting each target path, or {@code null} if no rewriting shall happen.
     *
     * @since 3.1.2
     */
    @Parameter( property = "mdep.unpack.filemappers" )
    private FileMapper[] fileMappers;

    /**
     * Main entry into mojo. This method gets the dependencies and iterates through each one passing it to
     * DependencyUtil.unpackFile().
     *
     * @throws MojoException with a message if an error occurs.
     * @see #getDependencySets(boolean)
     * @see #unpack(Artifact, Path, String, FileMapper[])
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        DependencyStatusSets<Artifact> dss = getDependencySets( this.failOnMissingClassifierArtifact );

        for ( Artifact artifact : dss.getResolvedDependencies() )
        {
            Path destDir = DependencyUtil.getFormattedOutputDirectory( useSubDirectoryPerScope, useSubDirectoryPerType,
                    useSubDirectoryPerArtifact, useRepositoryLayout,
                    stripVersion, stripType, outputDirectory, artifact );
            unpack( artifact, destDir, getIncludes(), getExcludes(), getEncoding(), getFileMappers() );
            DefaultFileMarkerHandler handler = new DefaultFileMarkerHandler( artifact, this.markersDirectory );
            handler.setMarker();
        }

        for ( Artifact artifact : dss.getSkippedDependencies() )
        {
            getLog().info( artifact + " already exists in destination." );
        }
    }

    @Override
    protected Predicate<Artifact> getMarkedArtifactFilter()
    {
        Predicate<ArtifactItem> f = new MarkerFileFilter( this.overWriteReleases, this.overWriteSnapshots,
                this.overWriteIfNewer, new DefaultFileMarkerHandler( this.markersDirectory ) );
        return a -> f.test( new ArtifactItem( a ) );
    }

    /**
     * @return Returns a comma separated list of excluded items
     */
    public String getExcludes()
    {
        return DependencyUtil.cleanToBeTokenizedString( this.excludes );
    }

    /**
     * @param excludes A comma separated list of items to exclude i.e. <code>**\/*.xml, **\/*.properties</code>
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
        return DependencyUtil.cleanToBeTokenizedString( this.includes );
    }

    /**
     * @param includes A comma separated list of items to include i.e. <code>**\/*.xml, **\/*.properties</code>
     */
    public void setIncludes( String includes )
    {
        this.includes = includes;
    }

    /**
     * @return Returns the encoding.
     * @since 3.0
     */
    public String getEncoding()
    {
        return this.encoding;
    }

    /**
     * @param encoding The encoding to set.
     * @since 3.0
     */
    public void setEncoding( String encoding )
    {
        this.encoding = encoding;
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
