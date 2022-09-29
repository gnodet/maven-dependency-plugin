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
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Node;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Type;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.DependencyCollector;
import org.apache.maven.api.services.ProjectBuilder;
import org.apache.maven.api.services.ProjectBuilderException;
import org.apache.maven.api.services.TypeRegistry;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;
import org.apache.maven.plugins.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactUtils;
import org.apache.maven.plugins.dependency.utils.filters.IncludeExcludeFilter;
import org.apache.maven.plugins.dependency.utils.filters.ScopeFilter;
import org.codehaus.plexus.util.StringUtils;

/**
 * Class that encapsulates the plugin parameters, and contains methods that handle dependency filtering
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @see org.apache.maven.plugins.dependency.AbstractDependencyMojo
 */
public abstract class AbstractDependencyFilterMojo
        extends AbstractDependencyMojo
{
    /**
     * Overwrite release artifacts
     *
     * @since 1.0
     */
    @Parameter( property = "overWriteReleases", defaultValue = "false" )
    protected boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
     *
     * @since 1.0
     */
    @Parameter( property = "overWriteSnapshots", defaultValue = "false" )
    protected boolean overWriteSnapshots;

    /**
     * Overwrite artifacts that don't exist or are older than the source.
     *
     * @since 2.0
     */
    @Parameter( property = "overWriteIfNewer", defaultValue = "true" )
    protected boolean overWriteIfNewer;

    /**
     * If we should exclude transitive dependencies
     *
     * @since 2.0
     */
    @Parameter( property = "excludeTransitive", defaultValue = "false" )
    protected boolean excludeTransitive;

    /**
     * Comma Separated list of Types to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "includeTypes", defaultValue = "" )
    protected String includeTypes;

    /**
     * Comma Separated list of Types to exclude. Empty String indicates don't exclude anything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "excludeTypes", defaultValue = "" )
    protected String excludeTypes;

    /**
     * Scope threshold to include. An empty string indicates include all dependencies (default).<br>
     * The scope threshold value being interpreted is the scope as
     * Maven filters for creating a classpath, not as specified in the pom. In summary:
     * <ul>
     * <li><code>runtime</code> include scope gives runtime and compile dependencies,</li>
     * <li><code>compile</code> include scope gives compile, provided, and system dependencies,</li>
     * <li><code>test</code> include scope gives all dependencies (equivalent to default),</li>
     * <li><code>provided</code> include scope just gives provided dependencies,</li>
     * <li><code>system</code> include scope just gives system dependencies.</li>
     * </ul>
     *
     * @since 2.0
     */
    @Parameter( property = "includeScope", defaultValue = "" )
    protected String includeScope;

    /**
     * Scope threshold to exclude, if no value is defined for include.
     * An empty string indicates no dependencies (default).<br>
     * The scope threshold value being interpreted is the scope as
     * Maven filters for creating a classpath, not as specified in the pom. In summary:
     * <ul>
     * <li><code>runtime</code> exclude scope excludes runtime and compile dependencies,</li>
     * <li><code>compile</code> exclude scope excludes compile, provided, and system dependencies,</li>
     * <li><code>test</code> exclude scope excludes all dependencies, then not really a legitimate option: it will
     * fail, you probably meant to configure includeScope = compile</li>
     * <li><code>provided</code> exclude scope just excludes provided dependencies,</li>
     * <li><code>system</code> exclude scope just excludes system dependencies.</li>
     * </ul>
     *
     * @since 2.0
     */
    @Parameter( property = "excludeScope", defaultValue = "" )
    protected String excludeScope;

    /**
     * Comma Separated list of Classifiers to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "includeClassifiers", defaultValue = "" )
    protected String includeClassifiers;

    /**
     * Comma Separated list of Classifiers to exclude. Empty String indicates don't exclude anything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "excludeClassifiers", defaultValue = "" )
    protected String excludeClassifiers;

    /**
     * Specify classifier to look for. Example: sources
     *
     * @since 2.0
     */
    @Parameter( property = "classifier", defaultValue = "" )
    protected String classifier;

    /**
     * Specify type to look for when constructing artifact based on classifier. Example: java-source,jar,war
     *
     * @since 2.0
     */
    @Parameter( property = "type", defaultValue = "" )
    protected String type;

    /**
     * Comma separated list of Artifact names to exclude.
     *
     * @since 2.0
     */
    @Parameter( property = "excludeArtifactIds", defaultValue = "" )
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "includeArtifactIds", defaultValue = "" )
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     *
     * @since 2.0
     */
    @Parameter( property = "excludeGroupIds", defaultValue = "" )
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include. Empty String indicates include everything (default).
     *
     * @since 2.0
     */
    @Parameter( property = "includeGroupIds", defaultValue = "" )
    protected String includeGroupIds;

    /**
     * Directory to store flag files
     *
     * @since 2.0
     */
    @Parameter( property = "markersDirectory",
                defaultValue = "${project.build.directory}/dependency-maven-plugin-markers" )
    protected Path markersDirectory;

    /**
     * Prepend the groupId during copy.
     *
     * @since 2.2
     */
    @Parameter( property = "mdep.prependGroupId", defaultValue = "false" )
    protected boolean prependGroupId = false;

    /**
     * Return an {@link Predicate<Artifact>} indicating which artifacts must be filtered out.
     *
     * @return an {@link Predicate<Artifact>} indicating which artifacts must be filtered out.
     */
    protected Predicate<Artifact> getMarkedArtifactFilter()
    {
        return null;
    }

    /**
     * Retrieves dependencies, either direct only or all including transitive.
     *
     * @param stopOnFailure true to fail if resolution does not work or false not to fail.
     * @return A set of artifacts
     * @throws MojoException in case of errors.
     */
    protected Set<Artifact> getResolvedDependencies( boolean stopOnFailure )
            throws MojoException

    {
        DependencyStatusSets<Artifact> status = getDependencySets( stopOnFailure );

        return status.getResolvedDependencies();
    }

    /**
     * @param stopOnFailure true/false.
     * @return {@link DependencyStatusSets}
     * @throws MojoException in case of an error.
     */
    protected DependencyStatusSets<Artifact> getDependencySets( boolean stopOnFailure )
            throws MojoException
    {
        return getDependencySets( stopOnFailure, false );
    }

    /**
     * Method creates filters and filters the projects dependencies. This method also transforms the dependencies if
     * classifier is set. The dependencies are filtered in least specific to most specific order
     *
     * @param stopOnFailure  true to fail if artifacts can't be resolved false otherwise.
     * @param includeParents <code>true</code> if parents should be included or not <code>false</code>.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoException in case of errors.
     */
    protected DependencyStatusSets<Artifact> getDependencySets( boolean stopOnFailure, boolean includeParents )
            throws MojoException
    {
        if ( "test".equals( this.excludeScope ) )
        {
            throw new MojoException( "Excluding every artifact inside 'test' resolution scope means "
                    + "excluding everything: you probably want includeScope='compile', "
                    + "read parameters documentation for detailed explanations" );
        }

        // add filters in well known order, least specific to most specific
        Predicate<Node> nodeFilter = null;
        if ( excludeTransitive )
        {
            Set<String> directDeps = getProject().getModel().getDependencies().stream()
                    .map( ArtifactUtils::conflictId )
                    .collect( Collectors.toSet() );
            nodeFilter = and( nodeFilter, n -> directDeps.contains( ArtifactUtils.conflictId( n.getArtifact() ) ) );
        }
        nodeFilter = and( nodeFilter, new ScopeFilter<>( includeScope, excludeScope,
                                                         n -> n.getDependency().getScope() ) );

        Predicate<Artifact> artifactFilter =  new IncludeExcludeFilter<>( includeTypes, excludeTypes,
                                                                         a -> a.getType().getName() );
        artifactFilter = and( artifactFilter, new IncludeExcludeFilter<>( includeClassifiers, excludeClassifiers,
                                                                          Artifact::getClassifier ) );
        artifactFilter = and( artifactFilter, new IncludeExcludeFilter<>( includeGroupIds, excludeGroupIds,
                                                                          Artifact::getGroupId, String::startsWith ) );
        artifactFilter = and( artifactFilter, new IncludeExcludeFilter<>( includeArtifactIds, excludeArtifactIds,
                                                                          Artifact::getArtifactId ) );

        // start with all nodes
        Node node = session.getService( DependencyCollector.class )
                .collect( session, getProject() ).getRoot();

        // Filter nodes and collect artifacts
        Set<Artifact> artifacts = node.stream()
                .filter( n -> !Objects.equals( n.getArtifact(), getProject().getArtifact() ) )
                .filter( nodeFilter )
                .map( Node::getArtifact ).collect( Collectors.toSet() );

        if ( includeParents )
        {
            // add dependencies parents
            for ( Artifact dep : new ArrayList<>( artifacts ) )
            {
                addParentArtifacts( buildProjectFromArtifact( dep ), artifacts );
            }

            // add current project parent
            addParentArtifacts( getProject(), artifacts );
        }

        // perform filtering
        artifacts = artifacts.stream().filter( artifactFilter ).collect( Collectors.toSet() );

        // resolve artifacts
        artifacts = artifacts.stream()
                        .map( a -> session.resolveArtifact( a ) )
                        .collect( Collectors.toSet() );

        // transform artifacts if classifier is set
        DependencyStatusSets<Artifact> status;
        if ( StringUtils.isNotEmpty( classifier ) )
        {
            status = getClassifierTranslatedDependencies( artifacts, stopOnFailure );
        }
        else
        {
            status = filterMarkedDependencies( artifacts );
        }

        return status;
    }
    
    private <T> Predicate<T> and( Predicate<T> t1, Predicate<T> t2 )
    {
        return t1 != null ? t1.and( t2 ) : t2;
    }

    private Project buildProjectFromArtifact( Artifact artifact )
            throws MojoException
    {
        try
        {
            return session.getService( ProjectBuilder.class )
                    .build( session, artifact )
                    .getProject()
                    .get();
        }
        catch ( ProjectBuilderException e )
        {
            throw new MojoException( e.getMessage(), e );
        }
    }

    private void addParentArtifacts( Project project, Set<Artifact> artifacts )
            throws MojoException
    {
        Session session = newResolveArtifactProjectBuildingRequest();
        while ( project.getParent().isPresent() )
        {
            project = project.getParent().get();
            Artifact artifact = project.getArtifact();
            if ( artifacts.add( artifact ) )
            {
                try
                {
                    Coordinate coord = session.createCoordinate( artifact.getGroupId(), artifact.getArtifactId(),
                            artifact.getVersion().asString(), artifact.getClassifier(), artifact.getExtension(),
                            artifact.getType().getName() );
                    Artifact resolvedArtifact = session.resolveArtifact( coord );
                    artifacts.add( resolvedArtifact );
                }
                catch ( ArtifactResolverException e )
                {
                    throw new MojoException( e.getMessage(), e );
                }
            }
        }
    }

    /**
     * Transform artifacts
     *
     * @param artifacts     set of artifacts {@link Artifact}.
     * @param stopOnFailure true/false.
     * @return DependencyStatusSets - Bean of TreeSets that contains information on the projects dependencies
     * @throws MojoException in case of an error.
     */
    protected DependencyStatusSets<Artifact> getClassifierTranslatedDependencies(
                    Set<Artifact> artifacts, boolean stopOnFailure )
            throws MojoException
    {
        Set<Artifact> unResolvedArtifacts = new LinkedHashSet<>();
        Set<Artifact> resolvedArtifacts = artifacts;

        // possibly translate artifacts into a new set of artifacts based on the
        // classifier and type
        // if this did something, we need to resolve the new artifacts
        if ( StringUtils.isNotEmpty( classifier ) )
        {
            getLog().debug( "Translating Artifacts using Classifier: " + this.classifier + " and Type: " + this.type );

            // TODO: the following lines look suspicious

            Collection<Coordinate> coordinates = artifacts.stream()
                    .map( this::translateClassifier )
                    .collect( Collectors.toList() );

            DependencyStatusSets<Artifact> status = filterMarkedDependencies( artifacts );

            // the unskipped artifacts are in the resolved set.
            artifacts = status.getResolvedDependencies();

            // resolve the rest of the artifacts
            resolvedArtifacts = resolve( new LinkedHashSet<>( coordinates ), stopOnFailure );

            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll( artifacts );
            unResolvedArtifacts.removeAll( resolvedArtifacts );
        }

        // return a bean of all 3 sets.
        DependencyStatusSets<Artifact> status = new DependencyStatusSets<>();
        status.setResolvedDependencies( resolvedArtifacts );
        status.setUnResolvedDependencies( unResolvedArtifacts );
        return status;
    }

    /*
     * (non-Javadoc)
     * @see org.apache.mojo.dependency.utils.translators.ArtifactTranslator#translate(java.util.Set,
     * org.apache.maven.plugin.logging.Log)
     */
    public Coordinate translateClassifier( Artifact artifact )
    {
        // this translator must pass both type and classifier here so we
        // will use the
        // base artifact value if null comes in
        final Type useType;
        if ( StringUtils.isNotEmpty( this.type ) )
        {
            useType = session.getService( TypeRegistry.class ).getType( this.type );
        }
        else
        {
            useType = artifact.getType();
        }

        final String extension;
        if ( useType != null )
        {
            extension = useType.getExtension();
        }
        else
        {
            extension = this.type;
        }

        String useClassifier;
        if ( StringUtils.isNotEmpty( this.classifier ) )
        {
            useClassifier = this.classifier;
        }
        else
        {
            useClassifier = artifact.getClassifier();
        }

        Coordinate coordinate = session.createCoordinate(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion().asString(),
                useClassifier,
                extension,
                null );

        // // Create a new artifact
        // Artifact newArtifact = factory.createArtifactWithClassifier( artifact.getGroupId(), artifact
        // .getArtifactId(), artifact.getVersion(), useType, useClassifier );
        //
        // // note the new artifacts will always have the scope set to null. We
        // // should
        // // reset it here so that it will pass other filters if needed
        // newArtifact.setScope( artifact.getScope() );
        //
        // if ( Artifact.SCOPE_SYSTEM.equals( newArtifact.getScope() ) )
        // {
        // File baseDir = repositoryManager.getLocalRepositoryBasedir( buildingRequest );
        // String path = repositoryManager.getPathForLocalArtifact( buildingRequest, newArtifact );
        // newArtifact.setFile( new File( baseDir, path ) );
        // }

        return coordinate;
    }

    /**
     * Filter the marked dependencies
     *
     * @param artifacts The artifacts set {@link Artifact}.
     * @return status set {@link DependencyStatusSets}.
     * @throws MojoException in case of an error.
     */
    protected DependencyStatusSets<Artifact> filterMarkedDependencies( Set<Artifact> artifacts )
            throws MojoException
    {
        // remove files that have markers already
        Predicate<Artifact> markedArtifactFilter = getMarkedArtifactFilter();
        Set<Artifact> unMarkedArtifacts = markedArtifactFilter != null
            ? artifacts.stream().filter( getMarkedArtifactFilter() ).collect( Collectors.toSet() )
            : Collections.emptySet();

        // calculate the skipped artifacts
        Set<Artifact> skippedArtifacts = new LinkedHashSet<>( artifacts );
        skippedArtifacts.removeAll( unMarkedArtifacts );

        return new DependencyStatusSets<>( unMarkedArtifacts, null, skippedArtifacts );
    }

    /**
     * @param coordinates   The set of artifact coordinates{@link Coordinate}.
     * @param stopOnFailure <code>true</code> if we should fail with exception if an artifact couldn't be resolved
     *                      <code>false</code> otherwise.
     * @return the resolved artifacts. {@link Artifact}.
     * @throws MojoException in case of error.
     */
    protected Set<Artifact> resolve( Set<Coordinate> coordinates, boolean stopOnFailure )
            throws MojoException
    {
        Session session = newResolveArtifactProjectBuildingRequest();

        Set<Artifact> resolvedArtifacts = new LinkedHashSet<>();
        for ( Coordinate coordinate : coordinates )
        {
            try
            {
                Artifact artifact = session.resolveArtifact( coordinate );
                resolvedArtifacts.add( artifact );
            }
            catch ( ArtifactResolverException ex )
            {
                // an error occurred during resolution, log it an continue
                getLog().debug( "error resolving: " + coordinate );
                getLog().debug( ex );
                if ( stopOnFailure )
                {
                    throw new MojoException( "error resolving: " + coordinate, ex );
                }
            }
        }
        return resolvedArtifacts;
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

    // TODO: Set marker files.

    /**
     * @return true, if the groupId should be prepended to the filename.
     */
    public boolean isPrependGroupId()
    {
        return prependGroupId;
    }

    /**
     * @param prependGroupId - true if the groupId must be prepended during the copy.
     */
    public void setPrependGroupId( boolean prependGroupId )
    {
        this.prependGroupId = prependGroupId;
    }

}
