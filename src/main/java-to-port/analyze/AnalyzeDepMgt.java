package org.apache.maven.plugins.dependency.analyze;

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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Dependency;
import org.apache.maven.api.model.DependencyManagement;
import org.apache.maven.api.model.Exclusion;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ProjectManager;
import org.codehaus.plexus.util.StringUtils;

import static org.apache.maven.plugins.dependency.utils.DependencyUtil.getManagementKey;

/**
 * This mojo looks at the dependencies after final resolution and looks for mismatches in your dependencyManagement
 * section. This mojo is also useful for detecting projects that override the dependencyManagement directly.
 * Set ignoreDirect to false to detect these otherwise normal conditions.
 *
 * @author <a href="mailto:brianefox@gmail.com">Brian Fox</a>
 * @since 2.0-alpha-3
 */
@Mojo( name = "analyze-dep-mgt", requiresDependencyResolution = ResolutionScope.TEST )
public class AnalyzeDepMgt
        implements org.apache.maven.api.plugin.Mojo
{
    // fields -----------------------------------------------------------------

    /**
     *
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    /**
     * Fail the build if a problem is detected.
     */
    @Parameter( property = "mdep.analyze.failBuild", defaultValue = "false" )
    private boolean failBuild = false;

    /**
     * Ignore Direct Dependency Overrides of dependencyManagement section.
     */
    @Parameter( property = "mdep.analyze.ignore.direct", defaultValue = "true" )
    private boolean ignoreDirect = true;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter( property = "mdep.analyze.skip", defaultValue = "false" )
    private boolean skip;

    @Component
    private Log log;

    @Component
    private Session session;

    // Mojo methods -----------------------------------------------------------

    /*
     * @see org.apache.maven.plugin.Mojo#execute()
     */
    @Override
    public void execute()
            throws MojoException
    {
        if ( skip )
        {
            getLog().info( "Skipping plugin execution" );
            return;
        }

        boolean result = checkDependencyManagement();
        if ( result )
        {
            if ( this.failBuild )

            {
                throw new MojoException( "Found Dependency errors." );
            }
            else
            {
                getLog().warn( "Potential problems found in Dependency Management " );
            }
        }
    }

    public Log getLog()
    {
        return log;
    }

    /**
     * Does the work of checking the DependencyManagement Section.
     *
     * @return true if errors are found.
     * @throws MojoException
     */
    private boolean checkDependencyManagement()
            throws MojoException
    {
        boolean foundError = false;

        getLog().info( "Found Resolved Dependency/DependencyManagement mismatches:" );

        List<Dependency> depMgtDependencies = null;

        DependencyManagement depMgt = project.getModel().getDependencyManagement();
        if ( depMgt != null )
        {
            depMgtDependencies = depMgt.getDependencies();
        }

        if ( depMgtDependencies != null && !depMgtDependencies.isEmpty() )
        {
            // put all the dependencies from depMgt into a map for quick lookup
            Map<String, Dependency> depMgtMap = new HashMap<>();
            Map<String, Exclusion> exclusions = new HashMap<>();
            for ( Dependency depMgtDependency : depMgtDependencies )
            {
                depMgtMap.put( getManagementKey( depMgtDependency ), depMgtDependency );

                // now put all the exclusions into a map for quick lookup
                exclusions.putAll( addExclusions( depMgtDependency.getExclusions() ) );
            }

            // get dependencies for the project (including transitive)
            List<Artifact> allDependencyArtifacts = session.getService( ProjectManager.class )
                    .getResolvedDependencies( getProject(), ResolutionScope.TEST );

            // don't warn if a dependency that is directly listed overrides
            // depMgt. That's ok.
            if ( this.ignoreDirect )
            {
                getLog().info( "\tIgnoring Direct Dependencies." );
                Set<Artifact> directDependencies = project.getDependencyArtifacts();
                allDependencyArtifacts.removeAll( directDependencies );
            }

            // log exclusion errors
            List<Artifact> exclusionErrors = getExclusionErrors( exclusions, allDependencyArtifacts );
            for ( Artifact exclusion : exclusionErrors )
            {
                getLog().info( StringUtils.stripEnd( getArtifactManagementKey( exclusion ), ":" )
                        + " was excluded in DepMgt, but version " + exclusion.getVersion()
                        + " has been found in the dependency tree." );
                foundError = true;
            }

            // find and log version mismatches
            Map<Artifact, Dependency> mismatch = getMismatch( depMgtMap, allDependencyArtifacts );
            for ( Map.Entry<Artifact, Dependency> entry : mismatch.entrySet() )
            {
                logMismatch( entry.getKey(), entry.getValue() );
                foundError = true;
            }
            if ( !foundError )
            {
                getLog().info( "\tNone" );
            }
        }
        else
        {
            getLog().info( "\tNothing in DepMgt." );
        }

        return foundError;
    }

    /**
     * Returns a map of the exclusions using the Dependency ManagementKey as the keyset.
     *
     * @param exclusionList to be added to the map.
     * @return a map of the exclusions using the Dependency ManagementKey as the keyset.
     */
    public Map<String, Exclusion> addExclusions( Collection<Exclusion> exclusionList )
    {
        if ( exclusionList != null )
        {
            return exclusionList.stream()
                    .collect( Collectors.toMap( this::getExclusionKey, exclusion -> exclusion ) );
        }
        return Collections.emptyMap();
    }

    /**
     * Returns a List of the artifacts that should have been excluded, but were found in the dependency tree.
     *
     * @param exclusions             a map of the DependencyManagement exclusions, with the ManagementKey as the key and
     *                               Dependency
     *                               as the value.
     * @param allDependencyArtifacts resolved artifacts to be compared.
     * @return list of artifacts that should have been excluded.
     */
    public List<Artifact> getExclusionErrors( Map<String, Exclusion> exclusions, List<Artifact> allDependencyArtifacts )
    {
        return allDependencyArtifacts.stream()
                .filter( artifact -> exclusions.containsKey( getExclusionKey( artifact ) ) )
                .collect( Collectors.toList() );
    }

    /**
     * @param artifact {@link Artifact}
     * @return The resulting GA.
     */
    public String getExclusionKey( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId();
    }

    /**
     * @param ex The exclusion key.
     * @return The resulting combination of groupId+artifactId.
     */
    public String getExclusionKey( Exclusion ex )
    {
        return ex.getGroupId() + ":" + ex.getArtifactId();
    }

    /**
     * Calculate the mismatches between the DependencyManagement and resolved artifacts
     *
     * @param depMgtMap              contains the Dependency.GetManagementKey as the keyset for quick lookup.
     * @param allDependencyArtifacts contains the set of all artifacts to compare.
     * @return a map containing the resolved artifact as the key and the listed dependency as the value.
     */
    public Map<Artifact, Dependency> getMismatch( Map<String, Dependency> depMgtMap,
                                                  List<Artifact> allDependencyArtifacts )
    {
        Map<Artifact, Dependency> mismatchMap = new HashMap<>();

        for ( Artifact dependencyArtifact : allDependencyArtifacts )
        {
            Dependency depFromDepMgt = depMgtMap.get( getArtifactManagementKey( dependencyArtifact ) );
            if ( depFromDepMgt != null )
            {
                // workaround for MNG-2961
                dependencyArtifact.isSnapshot();

                if ( depFromDepMgt.getVersion() != null
                        && !depFromDepMgt.getVersion().equals( dependencyArtifact.getVersion().asString() ) )
                {
                    mismatchMap.put( dependencyArtifact, depFromDepMgt );
                }
            }
        }
        return mismatchMap;
    }

    /**
     * This function displays the log to the screen showing the versions and information about the artifacts that don't
     * match.
     *
     * @param dependencyArtifact   the artifact that was resolved.
     * @param dependencyFromDepMgt the dependency listed in the DependencyManagement section.
     * @throws MojoException in case of errors.
     */
    public void logMismatch( Artifact dependencyArtifact, Dependency dependencyFromDepMgt )
            throws MojoException
    {
        if ( dependencyArtifact == null || dependencyFromDepMgt == null )
        {
            throw new MojoException( "Invalid params: Artifact: " + dependencyArtifact + " Dependency: "
                    + dependencyFromDepMgt );
        }

        getLog().info( "\tDependency: " + StringUtils.stripEnd( getManagementKey( dependencyFromDepMgt ), ":" ) );
        getLog().info( "\t\tDepMgt  : " + dependencyFromDepMgt.getVersion() );
        getLog().info( "\t\tResolved: " + dependencyArtifact.getVersion() );
    }

    /**
     * This function returns a string comparable with Dependency.GetManagementKey.
     *
     * @param artifact to gen the key for
     * @return a string in the form: groupId:ArtifactId:Type[:Classifier]
     */
    public String getArtifactManagementKey( Artifact artifact )
    {
        return artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getType()
                + ( ( artifact.getClassifier() != null ) ? ":" + artifact.getClassifier() : "" );
    }

    /**
     * @return the failBuild
     */
    protected final boolean isFailBuild()
    {
        return this.failBuild;
    }

    /**
     * @param theFailBuild the failBuild to set
     */
    public void setFailBuild( boolean theFailBuild )
    {
        this.failBuild = theFailBuild;
    }

    /**
     * @return the project
     */
    protected final Project getProject()
    {
        return this.project;
    }

    /**
     * @param theProject the project to set
     */
    public void setProject( Project theProject )
    {
        this.project = theProject;
    }

    /**
     * @return the ignoreDirect
     */
    protected final boolean isIgnoreDirect()
    {
        return this.ignoreDirect;
    }

    /**
     * @param theIgnoreDirect the ignoreDirect to set
     */
    public void setIgnoreDirect( boolean theIgnoreDirect )
    {
        this.ignoreDirect = theIgnoreDirect;
    }
}
