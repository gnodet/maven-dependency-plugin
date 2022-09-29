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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.Coordinate;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.Session;
import org.apache.maven.api.model.Repository;
import org.apache.maven.api.model.RepositoryPolicy;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactResolver;
import org.apache.maven.api.services.ArtifactResolverResult;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverResult;
import org.codehaus.plexus.util.StringUtils;


/**
 * Retrieves and lists all classes contained in the specified artifact from the specified remote repositories.
 */
@Mojo( name = "list-classes", requiresProject = false )
public class ListClassesMojo
        implements org.apache.maven.api.plugin.Mojo
{
    private static final Pattern ALT_REPO_SYNTAX_PATTERN = Pattern.compile( "(.+)::(.*)::(.+)" );

    @Parameter( defaultValue = "${session}", required = true, readonly = true )
    private Session session;

    /**
     * The group ID of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "groupId" )
    private String groupId;

    /**
     * The artifact ID of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "artifactId" )
    private String artifactId;

    /**
     * The version of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "version" )
    private String version;

    /**
     * The classifier of the artifact to download. Ignored if {@link #artifact} is used.
     *
     * @since 2.3
     */
    @Parameter( property = "classifier" )
    private String classifier;

    /**
     * The packaging of the artifact to download. Ignored if {@link #artifact} is used.
     */
    @Parameter( property = "packaging", defaultValue = "jar" )
    private String packaging = "jar";

    /**
     * Repositories in the format id::[layout]::url or just URLs, separated by comma. That is,
     * central::default::https://repo.maven.apache.org/maven2,myrepo::::https://repo.acme.com,https://repo.acme2.com
     */
    @Parameter( property = "remoteRepositories" )
    private String remoteRepositories;

    /**
     * A string of the form groupId:artifactId:version[:packaging[:classifier]].
     */
    @Parameter( property = "artifact" )
    private String artifact;

    @Parameter( defaultValue = "${project.remoteProjectRepositories}", readonly = true, required = true )
    private List<RemoteRepository> pomRemoteRepositories;

    /**
     * Download transitively, retrieving the specified artifact and all of its dependencies.
     */
    @Parameter( property = "transitive", defaultValue = "false" )
    private boolean transitive = false;

    /**
     * Skip plugin execution completely.
     */
    @Parameter( property = "mdep.skip", defaultValue = "false" )
    private boolean skip;

    @Component
    private Log log;

    @Override
    public void execute() throws MojoException
    {
        if ( skip )
        {
            log.info( "Skipping plugin execution" );
            return;
        }

        String groupId = this.groupId;
        String artifactId = this.artifactId;
        String version = this.version;
        String classifier = this.classifier;
        String type = this.packaging;

        if ( artifactId == null && artifact == null )
        {
            throw new MojoException( "You must specify an artifact, "
                    + "e.g. -Dartifact=org.apache.maven.plugins:maven-downloader-plugin:1.0" );
        }
        if ( artifact != null )
        {
            String[] tokens = StringUtils.split( artifact, ":" );
            if ( tokens.length < 3 || tokens.length > 5 )
            {
                throw new MojoException( "Invalid artifact, you must specify "
                        + "groupId:artifactId:version[:packaging[:classifier]] " + artifact );
            }
            groupId = tokens[0];
            artifactId = tokens[1];
            version = tokens[2];
            if ( tokens.length >= 4 )
            {
                type = tokens[3];
            }
            if ( tokens.length == 5 )
            {
                classifier = tokens[4];
            }
        }

        List<RemoteRepository> repoList = new ArrayList<>();
        if ( pomRemoteRepositories != null )
        {
            repoList.addAll( pomRemoteRepositories );
        }
        if ( remoteRepositories != null )
        {
            // Use the same format as in the deploy plugin id::layout::url
            String[] repos = StringUtils.split( remoteRepositories, "," );
            for ( String repo : repos )
            {
                repoList.add( parseRepository( repo ) );
            }
        }

        Session session = this.session.withRemoteRepositories( repoList );
        try
        {
            Coordinate coordinate = null;
            if ( transitive )
            {
                DependencyResolverResult result = session
                        .getService( DependencyResolver.class )
                        .resolve( session, coordinate );

                result.getArtifactResults().forEach( this::printClassesFromArtifactResult );
            }
            else
            {
                ArtifactResolverResult result = session
                        .getService( ArtifactResolver.class )
                        .resolve( session, coordinate );

                printClassesFromArtifactResult( result );
            }
        }
        catch ( Exception e )
        {
            throw new MojoException( "Couldn't download artifact: " + e.getMessage(), e );
        }
    }

    private void printClassesFromArtifactResult( ArtifactResolverResult result )
    {
        // open jar file in try-with-resources statement to guarantee the file closes after use regardless of errors
        try ( JarFile jarFile = new JarFile( result.getArtifact().getPath().get().toFile() ) )
        {
            Enumeration<JarEntry> entries = jarFile.entries();

            while ( entries.hasMoreElements() )
            {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();

                // filter out files that do not end in .class
                if ( !entryName.endsWith( ".class" ) )
                {
                    continue;
                }

                // remove .class from the end and change format to use periods instead of forward slashes
                String className = entryName.substring( 0, entryName.length() - 6 ).replace( '/', '.' );
                log.info( className );
            }
        }
        catch ( IOException e )
        {
            throw new MojoException( "Unable to read classes from artifact " + result.getArtifact(), e );
        }
    }

    RemoteRepository parseRepository( String repo )
            throws MojoException
    {
        // if it's a simple url
        String id = "temp";
        String layout = "default";
        String url = repo;

        // if it's an extended repo URL of the form id::layout::url
        if ( repo.contains( "::" ) )
        {
            Matcher matcher = ALT_REPO_SYNTAX_PATTERN.matcher( repo );
            if ( !matcher.matches() )
            {
                throw new MojoException( repo, "Invalid syntax for repository: " + repo,
                        "Invalid syntax for repository. Use \"id::layout::url\" or \"URL\"." );
            }

            id = matcher.group( 1 ).trim();
            if ( !StringUtils.isEmpty( matcher.group( 2 ) ) )
            {
                layout = matcher.group( 2 ).trim();
            }
            url = matcher.group( 3 ).trim();
        }
        RepositoryPolicy always = RepositoryPolicy.newBuilder()
                .enabled( "true" )
                .checksumPolicy( "always" )
                .updatePolicy( "always" )
                .build();
        return session.createRemoteRepository( Repository.newBuilder()
                .id( id )
                .layout( layout )
                .url( url )
                .releases( always )
                .snapshots( always )
                .build() );
    }
}
