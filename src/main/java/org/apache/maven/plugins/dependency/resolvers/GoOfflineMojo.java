package org.apache.maven.plugins.dependency.resolvers;

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Dependency;
import org.apache.maven.api.Node;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Plugin;
import org.apache.maven.api.model.ReportPlugin;
import org.apache.maven.api.model.Reporting;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;

/**
 * Goal that resolves all project dependencies, including plugins and reports and their dependencies.
 *
 * <a href="mailto:brianf@apache.org">Brian Fox</a>
 *
 * @author Maarten Mulders
 * @since 2.0
 */
@Mojo( name = "go-offline" )
public class GoOfflineMojo
        extends AbstractResolveMojo
{
    /**
     * Main entry into mojo. Gets the list of dependencies, resolves all that are not in the Reactor, and iterates
     * through displaying the resolved versions.
     *
     * @throws MojoException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
            throws MojoException
    {

        try
        {
            final Set<Artifact> plugins = resolvePluginArtifacts();

            final Set<Artifact> dependencies = resolveDependencyArtifacts();

            if ( !isSilent() )
            {
                for ( Artifact artifact : plugins )
                {
                    this.getLog().info( "Resolved plugin: "
                            + DependencyUtil.getFormattedFileName( artifact, false ) );
                }

                for ( Artifact artifact : dependencies )
                {
                    this.getLog().info( "Resolved dependency: "
                            + DependencyUtil.getFormattedFileName( artifact, false ) );
                }
            }

        }
        catch ( DependencyResolverException e )
        {
            throw new MojoException( e.getMessage(), e );
        }

    }

    /**
     * This method resolves the dependency artifacts from the project.
     *
     * @return set of resolved dependency artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolveDependencyArtifacts()
            throws DependencyResolverException
    {
        Collection<Dependency> dependencies = getProject().getDependencies();

        Session session = newResolveArtifactProjectBuildingRequest();

        return resolveDependableCoordinate( session, dependencies, "dependencies" );
    }

    private Set<Artifact> resolveDependableCoordinate( final Session session,
                                                       final Collection<? extends Coordinate> coordinates,
                                                       final String type )
            throws DependencyResolverException
    {
        Predicate<Node> filter = getTransformableFilter();

        this.getLog().debug( "Resolving '" + type + "' with following repositories:" );
        for ( RemoteRepository repo : session.getRemoteRepositories() )
        {
            getLog().debug( repo.getId() + " (" + repo.getUrl() + ")" );
        }

        final Set<Artifact> results = new HashSet<>();

        for ( Coordinate coordinate : coordinates )
        {
            List<ArtifactResolverResult> artifactResults =
                    session.getService( DependencyResolver.class )
                            .resolve( session, coordinate, filter ).getArtifactResults();

            for ( final ArtifactResolverResult artifactResult : artifactResults )
            {
                results.add( artifactResult.getArtifact() );
            }
        }

        return results;
    }

    private Predicate<Node> getTransformableFilter()
    {
        if ( this.excludeReactor )
        {
            return new ExcludeReactorProjectsDependencyFilter( this.reactorProjects, getLog() );
        }
        else
        {
            return null;
        }
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return set of resolved plugin artifacts.
     * @throws DependencyResolverException in case of an error while resolving the artifacts.
     */
    protected Set<Artifact> resolvePluginArtifacts()
            throws DependencyResolverException
    {
        Build build = getProject().getModel().getBuild();
        Reporting reporting = getProject().getModel().getReporting();

        Set<Coordinate> plugins = build != null
                ? build.getPlugins().stream().map( this::createCoordinate ).collect( Collectors.toSet() )
                : Collections.emptySet();
        Set<Coordinate> reports = reporting != null
                ? reporting.getPlugins().stream().map( this::createCoordinate ).collect( Collectors.toSet() )
                : Collections.emptySet();

        Set<Coordinate> coordinates = Stream.concat( plugins.stream(), reports.stream() )
                .collect( Collectors.toSet() );

        Session session = newResolvePluginProjectBuildingRequest();

        return resolveDependableCoordinate( session, coordinates, "plugins" );
    }

    private Coordinate createCoordinate( ReportPlugin plugin )
    {
        return session.createCoordinate( plugin.getGroupId(), plugin.getArtifactId(),
                plugin.getVersion(), "jar" );
    }

    private Coordinate createCoordinate( Plugin plugin )
    {
        return session.createCoordinate( plugin.getGroupId(), plugin.getArtifactId(),
                plugin.getVersion(), "jar" );
    }

}
