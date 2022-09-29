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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.maven.api.Project;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.LifecyclePhase;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;

/**
 * Displays all ancestor POMs of the project. This may be useful in a continuous integration system where you want to
 * know all parent poms of the project.
 *
 * @author Mirko Friedenhagen
 * @since 2.9
 */
@Mojo( name = "display-ancestors", requiresProject = true, defaultPhase = LifecyclePhase.VALIDATE )
public class DisplayAncestorsMojo
        implements org.apache.maven.api.plugin.Mojo
{

    /**
     * POM
     */
    @Parameter( defaultValue = "${project}", readonly = true )
    private Project project;

    @Component
    private Log log;

    @Override
    public void execute()
            throws MojoException
    {
        final List<String> ancestors = collectAncestors();

        if ( ancestors.isEmpty() )
        {
            getLog().info( "No Ancestor POMs!" );
        }
        else
        {
            getLog().info( "Ancestor POMs: " + String.join( " <- ", ancestors ) );
        }
    }

    protected Log getLog()
    {
        return log;
    }

    private ArrayList<String> collectAncestors()
    {
        final ArrayList<String> ancestors = new ArrayList<>();

        Project currentAncestor = project.getParent().orElse( null );
        while ( currentAncestor != null )
        {
            final String gav = String.format( Locale.US, "%s:%s:%s", currentAncestor.getGroupId(),
                    currentAncestor.getArtifactId(), currentAncestor.getVersion() );

            ancestors.add( gav );

            currentAncestor = currentAncestor.getParent().orElse( null );
        }

        return ancestors;
    }

}
