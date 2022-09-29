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

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.apache.maven.api.Coordinate;
import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.plugins.dependency.utils.filters.ArtifactUtils;

/**
 * {@link ArtifactFilter} implementation that excludes artifacts found in the Reactor.
 *
 * @author Maarten Mulders
 */
public class ExcludeReactorProjectsArtifactFilter implements Predicate<Coordinate>
{
    private final Log log;
    private final Set<String> reactorArtifactKeys;

    public ExcludeReactorProjectsArtifactFilter( final List<Project> reactorProjects, final Log log )
    {
        this.log = log;
        this.reactorArtifactKeys = reactorProjects.stream()
                .map( p -> ArtifactUtils.key( p.getArtifact() ) )
                .collect( Collectors.toSet() );
    }

    @Override
    public boolean test( Coordinate artifact )
    {
        String key = ArtifactUtils.key( artifact );
        if ( !reactorArtifactKeys.contains( key ) )
        {
            return true;
        }
        else
        {
            if ( log.isDebugEnabled() )
            {
                log.debug( "Skipped artifact " + key + " because it is present in the reactor" );
            }
            return false;
        }
    }

}
