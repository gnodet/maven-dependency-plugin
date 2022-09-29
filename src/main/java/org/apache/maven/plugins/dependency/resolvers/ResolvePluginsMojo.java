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

import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactResolverException;
import org.apache.maven.api.services.CoordinateFactory;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.IncludeExcludeFilter;

/**
 * Goal that resolves all project plugins and reports and their dependencies.
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.0
 */
@Mojo( name = "resolve-plugins", defaultPhase = LifecyclePhase.GENERATE_SOURCES )
public class ResolvePluginsMojo
        extends AbstractResolveMojo
{

    @Parameter( property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}" )
    private String outputEncoding;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through displaying the resolved version.
     *
     * @throws MojoException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        try
        {
            // ideally this should either be DependencyCoordinates or DependencyNode
            final Set<Artifact> plugins = resolvePluginArtifacts();

            StringBuilder sb = new StringBuilder();
            sb.append( System.lineSeparator() );
            sb.append( "The following plugins have been resolved:" );
            sb.append( System.lineSeparator() );
            if ( plugins == null || plugins.isEmpty() )
            {
                sb.append( "   none" );
                sb.append( System.lineSeparator() );
            }
            else
            {
                for ( Artifact plugin : plugins )
                {
                    String artifactFilename = null;
                    if ( outputAbsoluteArtifactFilename )
                    {
                        try
                        {
                            // we want to print the absolute file name here
                            artifactFilename = plugin.getPath().get().toAbsolutePath().toString();
                        }
                        catch ( NullPointerException e )
                        {
                            // ignore the null pointer, we'll output a null string
                            artifactFilename = null;
                        }
                    }

                    String id = plugin.toString();
                    sb.append( "   " )
                            .append( id )
                            .append( outputAbsoluteArtifactFilename ? ":" + artifactFilename : "" )
                            .append( System.lineSeparator() );

                    if ( !excludeTransitive )
                    {
                        Coordinate pluginCoordinate = session.createCoordinate(
                                plugin.getGroupId(), plugin.getArtifactId(), plugin.getVersion().asString(), null
                        );

                        for ( final Artifact artifact : resolveArtifactDependencies( pluginCoordinate ) )
                        {
                            artifactFilename = null;
                            if ( outputAbsoluteArtifactFilename )
                            {
                                try
                                {
                                    // we want to print the absolute file name here
                                    artifactFilename = artifact.getPath().get().toAbsolutePath().toString();
                                }
                                catch ( NullPointerException e )
                                {
                                    // ignore the null pointer, we'll output a null string
                                    artifactFilename = null;
                                }
                            }

                            id = artifact.toString();
                            sb.append( "      " )
                                    .append( id )
                                    .append( outputAbsoluteArtifactFilename ? ":" + artifactFilename : "" )
                                    .append( System.lineSeparator() );
                        }
                    }
                }
                sb.append( System.lineSeparator() );

                String output = sb.toString();
                if ( outputFile == null )
                {
                    DependencyUtil.log( output, getLog() );
                }
                else
                {
                    String encoding = Objects.toString( outputEncoding, "UTF-8" );
                    DependencyUtil.write( output, outputFile, appendOutput, encoding );
                }
            }
        }
        catch ( Exception e )
        {
            throw new MojoException( e.getMessage(), e );
        }
    }

    /**
     * This method resolves the plugin artifacts from the project.
     *
     * @return set of resolved plugin artifacts
     * @throws ArtifactResolverException in case of an error
     */
    protected Set<Artifact> resolvePluginArtifacts()
            throws MojoException, ArtifactResolverException
    {
        Session session = newResolvePluginProjectBuildingRequest();
        CoordinateFactory factory = session.getService( CoordinateFactory.class );

        Set<Coordinate> coordinates = Stream.concat(
                getProject().getModel().getBuild().getPlugins().stream()
                        .map( p -> factory.create( session, p ) ),
                getProject().getModel().getReporting().getPlugins().stream()
                        .map( p -> factory.create( session, p ) )
        ).collect( Collectors.toSet() );

        coordinates = coordinates.stream()
                .filter( getArtifactsFilter() ).collect( Collectors.toSet() );

        return coordinates.stream()
                .map( session::resolveArtifact )
                .collect( Collectors.toSet() );
    }

    /**
     * @return {@link Predicate<Coordinate>}
     */
    protected Predicate<Coordinate> getArtifactsFilter()
    {
        Predicate<Coordinate> f = c -> true;

        if ( excludeReactor )
        {
            f = f.and( new ExcludeReactorProjectsArtifactFilter( reactorProjects, getLog() ) );
        }

//        f = f.and( new ScopeFilter<>( includeScope, excludeScope, Coordinate::getScope ) );
        f = f.and( new IncludeExcludeFilter<>( includeTypes, excludeTypes, a -> a.getType().getName() ) );
        f = f.and(
                new IncludeExcludeFilter<>( includeClassifiers, excludeClassifiers, Coordinate::getClassifier ) );
        f = f.and( new IncludeExcludeFilter<>( includeGroupIds, excludeGroupIds, Coordinate::getGroupId,
                String::startsWith ) );
        f = f.and( new IncludeExcludeFilter<>( includeArtifactIds, excludeArtifactIds, Coordinate::getArtifactId ) );

        return f;
    }

}
