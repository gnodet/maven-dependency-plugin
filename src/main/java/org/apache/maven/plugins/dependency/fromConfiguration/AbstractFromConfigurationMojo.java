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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.codehaus.plexus.util.StringUtils;

/**
 * Abstract parent class used by mojos that get Artifact information from the plugin configuration as an ArrayList of
 * ArtifactItems
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @see ArtifactItem
 */
public abstract class AbstractFromConfigurationMojo
        extends AbstractDependencyMojo
{
    /**
     * Default output location used for mojo, unless overridden in ArtifactItem.
     *
     * @since 1.0
     */
    @Parameter( property = "outputDirectory", defaultValue = "${project.build.directory}/dependency" )
    private Path outputDirectory;

    /**
     * Overwrite release artifacts
     *
     * @since 1.0
     */
    @Parameter( property = "mdep.overWriteReleases", defaultValue = "false" )
    private boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
     *
     * @since 1.0
     */
    @Parameter( property = "mdep.overWriteSnapshots", defaultValue = "false" )
    private boolean overWriteSnapshots;

    /**
     * Overwrite if newer
     *
     * @since 2.0
     */
    @Parameter( property = "mdep.overIfNewer", defaultValue = "true" )
    private boolean overWriteIfNewer;

    /**
     * Collection of ArtifactItems to work on. (ArtifactItem contains groupId, artifactId, version, type, classifier,
     * outputDirectory, destFileName, overWrite and encoding.) See <a href="./usage.html">Usage</a> for details.
     *
     * @since 1.0
     */
    @Parameter
    private List<ArtifactItem> artifactItems;

    /**
     * Path to override default local repository during plugin's execution. To remove all downloaded artifacts as part
     * of the build, set this value to a location under your project's target directory
     *
     * @since 2.2
     */
    @Parameter
    private Path localRepositoryDirectory;

    abstract Predicate<ArtifactItem> getMarkedArtifactFilter( ArtifactItem item );

    /**
     * artifactItems is filled by either field injection or by setArtifact().
     *
     * @throws MojoException in case of an error.
     */
    protected void verifyRequirements()
            throws MojoException
    {
        if ( artifactItems == null || artifactItems.isEmpty() )
        {
            throw new MojoException( "Either artifact or artifactItems is required " );
        }
    }

    /**
     * Preprocesses the list of ArtifactItems. This method defaults the outputDirectory if not set and creates the
     * output Directory if it doesn't exist.
     *
     * @param processArtifactItemsRequest preprocessing instructions
     * @return An ArrayList of preprocessed ArtifactItems
     * @throws MojoException with a message if an error occurs.
     * @see ArtifactItem
     */
    protected List<ArtifactItem> getProcessedArtifactItems( ProcessArtifactItemsRequest processArtifactItemsRequest )
            throws MojoException
    {

        boolean removeVersion = processArtifactItemsRequest.isRemoveVersion(),
                prependGroupId = processArtifactItemsRequest.isPrependGroupId(),
                useBaseVersion = processArtifactItemsRequest.isUseBaseVersion();

        boolean removeClassifier = processArtifactItemsRequest.isRemoveClassifier();

        if ( artifactItems == null || artifactItems.size() < 1 )
        {
            throw new MojoException( "There are no artifactItems configured." );
        }

        for ( ArtifactItem artifactItem : artifactItems )
        {
            this.getLog().info( "Configured Artifact: " + artifactItem.toString() );

            if ( artifactItem.getOutputDirectory() == null )
            {
                artifactItem.setOutputDirectory( this.outputDirectory );
            }
            try
            {
                Files.createDirectories( artifactItem.getOutputDirectory() );
            }
            catch ( IOException e )
            {
                throw new MojoException( "Unable to create output directory " + artifactItem.getOutputDirectory(), e );
            }

            // make sure we have a version.
            if ( StringUtils.isEmpty( artifactItem.getVersion() ) )
            {
                fillMissingArtifactVersion( artifactItem );
            }

            artifactItem.setArtifact( this.getArtifact( artifactItem ) );

            if ( StringUtils.isEmpty( artifactItem.getDestFileName() ) )
            {
                artifactItem.setDestFileName( DependencyUtil.getFormattedFileName( artifactItem.getArtifact(),
                        removeVersion, prependGroupId,
                        useBaseVersion, removeClassifier ) );
            }

            try
            {
                artifactItem.setNeedsProcessing( checkIfProcessingNeeded( artifactItem ) );
            }
            catch ( MojoException e )
            {
                throw new MojoException( e.getMessage(), e );
            }
        }
        return artifactItems;
    }

    private boolean checkIfProcessingNeeded( ArtifactItem item )
            throws MojoException
    {
        return StringUtils.equalsIgnoreCase( item.getOverWrite(), "true" )
                || getMarkedArtifactFilter( item ).test( item );
    }

    /**
     * Resolves the Artifact from the remote repository if necessary. If no version is specified, it will be retrieved
     * from the dependency list or from the DependencyManagement section of the pom.
     *
     * @param artifactItem containing information about artifact from plugin configuration.
     * @return Artifact object representing the specified file.
     * @throws MojoException with a message if the version can't be found in DependencyManagement.
     */
    protected Artifact getArtifact( ArtifactItem artifactItem )
            throws MojoException
    {
        Artifact artifact;

        try
        {
            // mdep-50 - rolledback for now because it's breaking some functionality.
            /*
             * List listeners = new ArrayList(); Set theSet = new HashSet(); theSet.add( artifact );
             * ArtifactResolutionResult artifactResolutionResult = artifactCollector.collect( theSet, project
             * .getArtifact(), managedVersions, this.local, project.getRemoteArtifactRepositories(),
             * artifactMetadataSource, null, listeners ); Iterator iter =
             * artifactResolutionResult.getArtifactResolutionNodes().iterator(); while ( iter.hasNext() ) {
             * ResolutionNode node = (ResolutionNode) iter.next(); artifact = node.getArtifact(); }
             */

            Session session = newResolveArtifactProjectBuildingRequest();

            if ( localRepositoryDirectory != null )
            {
                session = session.withLocalRepository( session.createLocalRepository( localRepositoryDirectory ) );
            }

            // Map dependency to artifact coordinate
            Coordinate coordinate = session.createCoordinate(
                    artifactItem.getGroupId(),
                    artifactItem.getArtifactId(),
                    artifactItem.getVersion(),
                    artifactItem.getClassifier(),
                    null,
                    artifactItem.getType() );

            artifact = session.resolveArtifact( coordinate );
        }
        catch ( ArtifactResolverException e )
        {
            throw new MojoException( "Unable to find/resolve artifact.", e );
        }

        return artifact;
    }

    /**
     * Tries to find missing version from dependency list and dependency management. If found, the artifact is updated
     * with the correct version. It will first look for an exact match on artifactId/groupId/classifier/type and if it
     * doesn't find a match, it will try again looking for artifactId and groupId only.
     *
     * @param artifact representing configured file.
     * @throws MojoException
     */
    private void fillMissingArtifactVersion( ArtifactItem artifact )
            throws MojoException
    {
        Project project = getProject();
        List<Dependency> deps = project.getModel().getDependencies();
        List<Dependency> depMngt = project.getModel().getDependencyManagement() == null ? Collections.emptyList()
                : project.getModel().getDependencyManagement().getDependencies();

        if ( !findDependencyVersion( artifact, deps, false )
                && ( project.getModel().getDependencyManagement() == null || !findDependencyVersion( artifact, depMngt,
                false ) )
                && !findDependencyVersion( artifact, deps, true )
                && ( project.getModel().getDependencyManagement() == null || !findDependencyVersion( artifact, depMngt,
                true ) ) )
        {
            throw new MojoException( "Unable to find artifact version of " + artifact.getGroupId() + ":"
                    + artifact.getArtifactId() + " in either dependency list or in project's dependency management." );
        }
    }

    /**
     * Tries to find missing version from a list of dependencies. If found, the artifact is updated with the correct
     * version.
     *
     * @param artifact     representing configured file.
     * @param dependencies list of dependencies to search.
     * @param looseMatch   only look at artifactId and groupId
     * @return the found dependency
     */
    private boolean findDependencyVersion( ArtifactItem artifact, List<Dependency> dependencies, boolean looseMatch )
    {
        for ( Dependency dependency : dependencies )
        {
            if ( Objects.equals( dependency.getArtifactId(), artifact.getArtifactId() )
                    && Objects.equals( dependency.getGroupId(), artifact.getGroupId() )
                    && ( looseMatch || Objects.equals( dependency.getClassifier(), artifact.getClassifier() ) )
                    && ( looseMatch || Objects.equals( dependency.getType(), artifact.getType() ) ) )
            {
                artifact.setVersion( dependency.getVersion() );

                return true;
            }
        }

        return false;
    }

    /**
     * @return Returns the artifactItems.
     */
    public List<ArtifactItem> getArtifactItems()
    {
        return this.artifactItems;
    }

    /**
     * @param theArtifactItems The artifactItems to set.
     */
    public void setArtifactItems( List<ArtifactItem> theArtifactItems )
    {
        this.artifactItems = theArtifactItems;
    }

    /**
     * @return Returns the outputDirectory.
     */
    public Path getOutputDirectory()
    {
        return this.outputDirectory;
    }

    /**
     * @param theOutputDirectory The outputDirectory to set.
     */
    public void setOutputDirectory( Path theOutputDirectory )
    {
        this.outputDirectory = theOutputDirectory;
    }

    /**
     * @return Returns the overWriteIfNewer.
     */
    public boolean isOverWriteIfNewer()
    {
        return this.overWriteIfNewer;
    }

    /**
     * @param theOverWriteIfNewer The overWriteIfNewer to set.
     */
    public void setOverWriteIfNewer( boolean theOverWriteIfNewer )
    {
        this.overWriteIfNewer = theOverWriteIfNewer;
    }

    /**
     * @return Returns the overWriteReleases.
     */
    public boolean isOverWriteReleases()
    {
        return this.overWriteReleases;
    }

    /**
     * @param theOverWriteReleases The overWriteReleases to set.
     */
    public void setOverWriteReleases( boolean theOverWriteReleases )
    {
        this.overWriteReleases = theOverWriteReleases;
    }

    /**
     * @return Returns the overWriteSnapshots.
     */
    public boolean isOverWriteSnapshots()
    {
        return this.overWriteSnapshots;
    }

    /**
     * @param theOverWriteSnapshots The overWriteSnapshots to set.
     */
    public void setOverWriteSnapshots( boolean theOverWriteSnapshots )
    {
        this.overWriteSnapshots = theOverWriteSnapshots;
    }

    /**
     * @param localRepositoryDirectory {@link #localRepositoryDirectory}
     */
    public void setLocalRepositoryDirectory( Path localRepositoryDirectory )
    {
        this.localRepositoryDirectory = localRepositoryDirectory;
    }

    /**
     * @param artifact The artifact.
     * @throws MojoException in case of an error.
     */
    public void setArtifact( String artifact )
            throws MojoException
    {
        if ( artifact != null )
        {
            String packaging = "jar";
            String classifier;
            String[] tokens = StringUtils.split( artifact, ":" );
            if ( tokens.length < 3 || tokens.length > 5 )
            {
                throw new MojoException( "Invalid artifact, "
                        + "you must specify groupId:artifactId:version[:packaging[:classifier]] " + artifact );
            }
            String groupId = tokens[0];
            String artifactId = tokens[1];
            String version = tokens[2];
            if ( tokens.length >= 4 )
            {
                packaging = tokens[3];
            }
            if ( tokens.length == 5 )
            {
                classifier = tokens[4];
            }
            else
            {
                classifier = null;
            }

            ArtifactItem artifactItem = new ArtifactItem();
            artifactItem.setGroupId( groupId );
            artifactItem.setArtifactId( artifactId );
            artifactItem.setVersion( version );
            artifactItem.setType( packaging );
            artifactItem.setClassifier( classifier );

            setArtifactItems( Collections.singletonList( artifactItem ) );
        }
    }
}
