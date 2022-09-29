package org.apache.maven.plugins.dependency;

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

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;

/**
 * Goal that sets a property pointing to the artifact file for each project dependency. For each dependency (direct and
 * transitive) a project property will be set which follows the <code>groupId:artifactId:type:[classifier]</code> form
 * and contains the path to the resolved artifact.
 *
 * @author Paul Gier
 * @since 2.2
 */
//CHECKSTYLE_OFF: LineLength
@Mojo( name = "properties", requiresDependencyResolution = ResolutionScope.TEST,
       defaultPhase = LifecyclePhase.INITIALIZE )
//CHECKSTYLE_ON: LineLength
public class PropertiesMojo
        implements org.apache.maven.api.plugin.Mojo
{

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private Session session;

    /**
     * The current Maven project
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter( property = "mdep.skip", defaultValue = "false" )
    private boolean skip;

    @Component
    private Log log;

    /**
     * Main entry into mojo. Gets the list of dependencies and iterates through setting a property for each artifact.
     *
     * @throws MojoException with a message if an error occurs.
     */
    @Override
    public void execute()
            throws MojoException
    {
        if ( skip )
        {
            log.info( "Skipping plugin execution" );
            return;
        }

        ProjectManager projectManager = session.getService( ProjectManager.class );

        List<Artifact> artifacts = projectManager.getResolvedDependencies( project, ResolutionScope.TEST );

        artifacts.forEach( artifact ->
        {
            String id = artifact.getGroupId() + ":" + artifact.getArtifactId()
                    + ":" + artifact.getType().getName()
                    + ( artifact.getClassifier().isEmpty() ? "" : ":" + artifact.getClassifier() );
            String path = artifact.getPath().get().toString();
            projectManager.setProperty( project, id, path );
        } );
    }

}
