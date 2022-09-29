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

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.Node;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.services.DependencyCollectorException;
import org.apache.maven.plugins.dependency.AbstractDependencyMojo;

/**
 * Goal that resolves all project dependencies and then lists the repositories used by the build and by the transitive
 * dependencies
 *
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 * @since 2.2
 */
@Mojo( name = "list-repositories", requiresDependencyResolution = ResolutionScope.TEST )
public class ListRepositoriesMojo
        extends AbstractDependencyMojo
{
    /**
     * Dependency collector, needed to resolve dependencies.
     */
    /**
     * Displays a list of the repositories used by this build.
     *
     * @throws MojoException with a message if an error occurs.
     */
    @Override
    protected void doExecute()
            throws MojoException
    {
        try
        {
            Node node = session.collectDependencies( getProject() );
            Set<RemoteRepository> repositories = node.stream()
                    .flatMap( n -> n.getRemoteRepositories().stream() )
                    .collect( Collectors.toSet() );

            this.getLog().info( "Repositories used by this build:" );

            for ( RemoteRepository repo : repositories )
            {
                this.getLog().info( repo.toString() );
            }
        }
        catch ( DependencyCollectorException e )
        {
            throw new MojoException( "Unable to resolve artifacts", e );
        }
    }

}
