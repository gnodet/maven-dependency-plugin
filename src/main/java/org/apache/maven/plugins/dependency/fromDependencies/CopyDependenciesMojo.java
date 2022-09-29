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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactInstallerException;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.plugins.dependency.fromConfiguration.ArtifactItem;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.DestFileFilter;

/**
 * Goal that copies the project dependencies from the repository to a defined location.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 1.0
 */
@Mojo( name = "copy-dependencies",
       requiresDependencyResolution = ResolutionScope.TEST,
       defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class CopyDependenciesMojo
        extends AbstractFromDependenciesMojo
{
    /**
     * Also copy the pom of each artifact.
     *
     * @since 2.0
     */
    @Parameter( property = "mdep.copyPom", defaultValue = "false" )
    protected boolean copyPom = true;

    /**
     * Either append the artifact's baseVersion or uniqueVersion to the filename. Will only be used if
     * {@link #isStripVersion()} is {@code false}.
     *
     * @since 2.6
     */
    @Parameter( property = "mdep.useBaseVersion", defaultValue = "true" )
    protected boolean useBaseVersion = true;

    /**
     * Add parent poms to the list of copied dependencies (both current project pom parents and dependencies parents).
     *
     * @since 2.8
     */
    @Parameter( property = "mdep.addParentPoms", defaultValue = "false" )
    protected boolean addParentPoms;

    /**
     * <i>not used in this goal</i>
     */
    @Deprecated
    @Parameter
    protected boolean useJvmChmod = true;

    /**
     * <i>not used in this goal</i>
     */
    @Deprecated
    @Parameter
    protected boolean ignorePermissions;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through calling copyArtifact.
     *
     * @throws MojoException with a message if an error occurs.
     * @see #getDependencySets(boolean, boolean)
     * @see #copyArtifact(Artifact, boolean, boolean, boolean, boolean)
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        DependencyStatusSets<Artifact> dss = getDependencySets( this.failOnMissingClassifierArtifact, addParentPoms );
        Set<Artifact> artifacts = dss.getResolvedDependencies();

        if ( !useRepositoryLayout )
        {
            for ( Artifact artifact : artifacts )
            {
                copyArtifact( artifact, isStripVersion(), this.prependGroupId, this.useBaseVersion,
                        this.stripClassifier );
            }
        }
        else
        {
            Session session = this.session.withLocalRepository( this.session.createLocalRepository( outputDirectory ) );

            artifacts.forEach( artifact -> installArtifact( artifact, session ) );
        }

        Set<Artifact> skippedArtifacts = dss.getSkippedDependencies();
        for ( Artifact artifact : skippedArtifacts )
        {
            getLog().info( artifact + " already exists in destination." );
        }

        if ( isCopyPom() && !useRepositoryLayout )
        {
            copyPoms( getOutputDirectory(), artifacts, this.stripVersion );
            // Artifacts that already exist may not yet have poms
            copyPoms( getOutputDirectory(), skippedArtifacts, this.stripVersion, this.stripClassifier );
        }
    }

    /**
     * install the artifact and the corresponding pom if copyPoms=true
     */
    private void installArtifact( Artifact artifact, Session session )
    {
        try
        {
            session.installArtifacts( artifact );
            installBaseSnapshot( artifact, session );

            if ( !"pom".equals( artifact.getExtension() ) && isCopyPom() )
            {
                Artifact pomArtifact = getResolvedPomArtifact( artifact );
                if ( pomArtifact != null && pomArtifact.getPath().isPresent()
                        && Files.exists( pomArtifact.getPath().get() ) )
                {
                    session.installArtifacts( pomArtifact );
                    installBaseSnapshot( pomArtifact, session );
                }
            }
        }
        catch ( ArtifactInstallerException e )
        {
            getLog().warn( "unable to install " + artifact, e );
        }
    }

    private void installBaseSnapshot( Artifact artifact, Session session )
            throws ArtifactInstallerException
    {
        // TODO: implement with apiv4
        //        if ( artifact.isSnapshot() && !artifact.getBaseVersion().equals( artifact.getVersion() ) )
        //        {
        //            String version = artifact.getVersion();
        //            try
        //            {
        //                artifact.setVersion( artifact.getBaseVersion() );
        //                installer.install( buildingRequest, Collections.singletonList( artifact ) );
        //            }
        //            finally
        //            {
        //                artifact.setVersion( version );
        //            }
        //        }
    }

    /**
     * Copies the Artifact after building the destination file name if overridden. This method also checks if the
     * classifier is set and adds it to the destination file name if needed.
     *
     * @param artifact          representing the object to be copied.
     * @param removeVersion     specifies if the version should be removed from the file name when copying.
     * @param prependGroupId    specifies if the groupId should be prepend to the file while copying.
     * @param theUseBaseVersion specifies if the baseVersion of the artifact should be used instead of the version.
     * @param removeClassifier  specifies if the classifier should be removed from the file name when copying.
     * @throws MojoException with a message if an error occurs.
     * @see #copyFile(Path, Path)
     * @see DependencyUtil#getFormattedOutputDirectory(boolean, boolean, boolean, boolean, boolean, boolean, Path, Artifact)
     */
    protected void copyArtifact( Artifact artifact, boolean removeVersion, boolean prependGroupId,
                                 boolean theUseBaseVersion, boolean removeClassifier )
            throws MojoException
    {

        String destFileName = DependencyUtil.getFormattedFileName( artifact, removeVersion, prependGroupId,
                theUseBaseVersion, removeClassifier );

        Path destDir = DependencyUtil.getFormattedOutputDirectory( useSubDirectoryPerScope, useSubDirectoryPerType,
                useSubDirectoryPerArtifact, useRepositoryLayout,
                stripVersion, stripType, outputDirectory, artifact );
        Path destFile = destDir.resolve( destFileName );

        copyFile( artifact.getPath().get(), destFile );
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir       The destination directory {@link File}.
     * @param artifacts     The artifacts {@link Artifact}.
     * @param removeVersion remove version or not.
     * @throws MojoException in case of errors.
     */
    public void copyPoms( Path destDir, Set<Artifact> artifacts, boolean removeVersion )
            throws MojoException

    {
        copyPoms( destDir, artifacts, removeVersion, false );
    }

    /**
     * Copy the pom files associated with the artifacts.
     *
     * @param destDir          The destination directory {@link File}.
     * @param artifacts        The artifacts {@link Artifact}.
     * @param removeVersion    remove version or not.
     * @param removeClassifier remove the classifier or not.
     * @throws MojoException in case of errors.
     */
    public void copyPoms( Path destDir, Set<Artifact> artifacts, boolean removeVersion, boolean removeClassifier )
            throws MojoException

    {
        for ( Artifact artifact : artifacts )
        {
            Artifact pomArtifact = getResolvedPomArtifact( artifact );

            // Copy the pom
            if ( pomArtifact != null && pomArtifact.getPath().isPresent()
                    && Files.exists( pomArtifact.getPath().get() ) )
            {
                Path pomDestFile =
                        destDir.resolve( DependencyUtil.getFormattedFileName(
                                pomArtifact, removeVersion, prependGroupId, useBaseVersion, removeClassifier ) );
                if ( !Files.exists( pomDestFile ) )
                {
                    copyFile( pomArtifact.getPath().get(), pomDestFile );
                }
            }
        }
    }

    /**
     * @param artifact {@link Artifact}
     * @return {@link Artifact}
     */
    protected Artifact getResolvedPomArtifact( Artifact artifact )
    {
        Artifact pomArtifact = null;
        // Resolve the pom artifact using repos
        try
        {
            Session session = newResolveArtifactProjectBuildingRequest();
            pomArtifact = session.resolveArtifact(
                    session.createCoordinate(
                            artifact.getGroupId(),
                            artifact.getArtifactId(),
                            artifact.getVersion().asString(),
                            "pom" )
            );
        }
        catch ( ArtifactResolverException e )
        {
            getLog().info( e.getMessage() );
        }
        return pomArtifact;
    }

    @Override
    protected Predicate<Artifact> getMarkedArtifactFilter()
    {
        Predicate<ArtifactItem> f =  new DestFileFilter( this.overWriteReleases, this.overWriteSnapshots,
                this.overWriteIfNewer, this.useSubDirectoryPerArtifact, this.useSubDirectoryPerType,
                this.useSubDirectoryPerScope, this.useRepositoryLayout, this.stripVersion,
                this.prependGroupId, this.useBaseVersion, this.outputDirectory );
        return a -> f.test( new ArtifactItem( a ) );
    }

    /**
     * @return true, if the pom of each artifact must be copied
     */
    public boolean isCopyPom()
    {
        return this.copyPom;
    }

    /**
     * @param copyPom - true if the pom of each artifact must be copied
     */
    public void setCopyPom( boolean copyPom )
    {
        this.copyPom = copyPom;
    }
}
