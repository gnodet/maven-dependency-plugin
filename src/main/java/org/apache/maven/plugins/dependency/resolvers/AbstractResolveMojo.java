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

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Node;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.DependencyResolverException;
import org.apache.maven.plugins.dependency.fromDependencies.AbstractDependencyFilterMojo;

/**
 * @author <a href="mailto:brianf@apache.org">Brian Fox</a>
 */
public abstract class AbstractResolveMojo
        extends AbstractDependencyFilterMojo
{
    /**
     * If specified, this parameter causes the dependencies to be written to the path specified instead of
     * the console.
     *
     * @since 2.0
     */
    @Parameter( property = "outputFile" )
    protected Path outputFile;

    /**
     * Whether to append outputs into the output file or overwrite it.
     *
     * @since 2.2
     */
    @Parameter( property = "appendOutput", defaultValue = "false" )
    protected boolean appendOutput;

    /**
     * Don't resolve plugins that are in the current reactor.
     *
     * @since 2.7
     */
    @Parameter( property = "excludeReactor", defaultValue = "true" )
    protected boolean excludeReactor;

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
     * This method resolves all transitive dependencies of an artifact.
     *
     * @param artifact the artifact used to retrieve dependencies
     * @return resolved set of dependencies
     * @throws DependencyResolverException in case of error while resolving artifacts.
     */
    protected Set<Artifact> resolveArtifactDependencies( final Coordinate artifact )
            throws DependencyResolverException
    {
        Session session = newResolveArtifactProjectBuildingRequest();

        Node node = session.resolveDependencies( session.createDependency( artifact ) );

        return node.stream().map( Node::getArtifact ).collect( Collectors.toSet() );
    }
}
