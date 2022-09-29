package org.apache.maven.plugins.dependency.tree;

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
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.maven.api.Artifact;
import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;
import org.apache.maven.api.Project;
import org.apache.maven.api.RemoteRepository;
import org.apache.maven.api.ResolutionScope;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Component;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.DependencyCollector;
import org.apache.maven.api.services.DependencyCollectorRequest;
import org.apache.maven.api.services.VersionParser;
import org.apache.maven.plugins.dependency.tree.SerializingDependencyNodeVisitor.GraphTokens;
import org.apache.maven.plugins.dependency.utils.DependencyUtil;
import org.apache.maven.plugins.dependency.utils.filters.StrictPatternArtifactFilter;

/**
 * Displays the dependency tree for this project. Multiple formats are supported: text (by default), but also
 * <a href="https://en.wikipedia.org/wiki/DOT_language">DOT</a>,
 * <a href="https://en.wikipedia.org/wiki/GraphML">GraphML</a>, and
 * <a href="https://en.wikipedia.org/wiki/Trivial_Graph_Format">TGF</a>.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 * @since 2.0-alpha-5
 */
@Mojo( name = "tree", requiresDependencyCollection = ResolutionScope.TEST )
public class TreeMojo
        implements org.apache.maven.api.plugin.Mojo
{
    // fields -----------------------------------------------------------------

    /**
     * The Maven project.
     */
    @Parameter( defaultValue = "${project}", readonly = true, required = true )
    private Project project;

    @Parameter( defaultValue = "${session}", readonly = true, required = true )
    private Session session;

    @Parameter( property = "outputEncoding", defaultValue = "${project.reporting.outputEncoding}" )
    private String outputEncoding;

    /**
     * Contains the full list of projects in the reactor.
     */
    @Parameter( defaultValue = "${reactorProjects}", readonly = true, required = true )
    private List<Project> reactorProjects;

    /**
     * The project's remote repositories to use for the resolution of project dependencies.
     */
    @Parameter( defaultValue = "${project.remoteProjectRepositories}" )
    private List<RemoteRepository> projectRepos;

    /**
     * If specified, this parameter will cause the dependency tree to be written to the path specified, instead of
     * writing to the console.
     *
     * @since 2.0-alpha-5
     */
    @Parameter( property = "outputFile" )
    private Path outputFile;

    /**
     * If specified, this parameter will cause the dependency tree to be written using the specified format. Currently
     * supported format are: <code>text</code> (default), <code>dot</code>, <code>graphml</code> and <code>tgf</code>.
     * These additional formats can be plotted to image files.
     *
     * @since 2.2
     */
    @Parameter( property = "outputType", defaultValue = "text" )
    private String outputType;

    /**
     * The scope to filter by when resolving the dependency tree, or <code>null</code> to include dependencies from all
     * scopes.
     *
     * @since 2.0-alpha-5
     */
    @Parameter( property = "scope" )
    private String scope;

    /**
     * Whether to include omitted nodes in the serialized dependency tree. Notice this feature actually uses Maven 2
     * algorithm and <a href="https://maven.apache.org/shared/maven-dependency-tree/">may give wrong results when used
     * with Maven 3</a>.
     *
     * @since 2.0-alpha-6
     */
    @Parameter( property = "verbose", defaultValue = "false" )
    private boolean verbose;

    /**
     * The token set name to use when outputting the dependency tree. Possible values are <code>whitespace</code>,
     * <code>standard</code> or <code>extended</code>, which use whitespace, standard (ie ASCII) or extended character
     * sets respectively.
     *
     * @since 2.0-alpha-6
     */
    @Parameter( property = "tokens", defaultValue = "standard" )
    private String tokens;

    /**
     * A comma-separated list of artifacts to filter the serialized dependency tree by, or <code>null</code> not to
     * filter the dependency tree. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     * <p>
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard.
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @see StrictPatternArtifactFilter
     * @since 2.0-alpha-6
     */
    @Parameter( property = "includes" )
    private String includes;

    /**
     * A comma-separated list of artifacts to filter from the serialized dependency tree, or <code>null</code> not to
     * filter any artifacts from the dependency tree. The filter syntax is:
     *
     * <pre>
     * [groupId]:[artifactId]:[type]:[version]
     * </pre>
     * <p>
     * where each pattern segment is optional and supports full and partial <code>*</code> wildcards. An empty pattern
     * segment is treated as an implicit wildcard.
     * <p>
     * For example, <code>org.apache.*</code> will match all artifacts whose group id starts with
     * <code>org.apache.</code>, and <code>:::*-SNAPSHOT</code> will match all snapshot artifacts.
     * </p>
     *
     * @see StrictPatternArtifactFilter
     * @since 2.0-alpha-6
     */
    @Parameter( property = "excludes" )
    private String excludes;

    @Component
    private Log log;

    /**
     * The computed dependency tree root node of the Maven project.
     */
    private Node rootNode;

    /**
     * Whether to append outputs into the output file or overwrite it.
     *
     * @since 2.2
     */
    @Parameter( property = "appendOutput", defaultValue = "false" )
    private boolean appendOutput;

    /**
     * Skip plugin execution completely.
     *
     * @since 2.7
     */
    @Parameter( property = "skip", defaultValue = "false" )
    private boolean skip;

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
            log.info( "Skipping plugin execution" );
            return;
        }

        try
        {
            String dependencyTreeString;

            rootNode = session.getService( DependencyCollector.class ).collect(
                    DependencyCollectorRequest.builder()
                            .session( session )
                            .rootArtifact( project.getArtifact() )
                            .dependencies( project.getDependencies() )
                            .managedDependencies( project.getManagedDependencies() )
                            .verbose( verbose )
                            .build() )
                    .getRoot();
            dependencyTreeString = serializeDependencyTree( rootNode );

            if ( outputFile != null )
            {
                String encoding = Objects.toString( outputEncoding, "UTF-8" );
                DependencyUtil.write( dependencyTreeString, outputFile, this.appendOutput, encoding );

                log.info( "Wrote dependency tree to: " + outputFile );
            }
            else
            {
                DependencyUtil.log( dependencyTreeString, log );
            }
        }
        catch ( IOException exception )
        {
            throw new MojoException( "Cannot serialize project dependency graph", exception );
        }
        catch ( Exception exception )
        {
            throw new MojoException( "Cannot build project dependency graph", exception );
        }
    }

    public Log getLog()
    {
        return log;
    }

    /**
     * Gets the Maven project used by this mojo.
     *
     * @return the Maven project
     */
    public Project getProject()
    {
        return project;
    }

    /**
     * Gets the computed dependency graph root node for the Maven project.
     *
     * @return the dependency tree root node
     */
    public Node getDependencyGraph()
    {
        return rootNode;
    }

    /**
     * @return {@link #skip}
     */
    public boolean isSkip()
    {
        return skip;
    }

    // private methods --------------------------------------------------------

    /**
     * @param skip {@link #skip}
     */
    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

    /**
     * Serializes the specified dependency tree to a string.
     *
     * @param rootNode the dependency tree root node to serialize
     * @return the serialized dependency tree
     */
    private String serializeDependencyTree( Node rootNode )
    {
        StringWriter writer = new StringWriter();

        NodeVisitor visitor = getSerializingDependencyNodeVisitor( writer );

        Predicate<Node> filter = createDependencyNodeFilter();
        if ( filter != null )
        {
            // Compute parents
            Map<Node, Node> parents = new HashMap<>();
            rootNode.stream().forEach( p -> p.getChildren().forEach( c -> parents.put( c, p ) ) );
            Set<Node> filtered = rootNode.stream()
                        .filter( filter )
                        .flatMap( n -> ancestorOrSelf( parents, n ) )
                        .collect( Collectors.toSet() );
            rootNode = rootNode.filter( filtered::contains );
        }

        rootNode.accept( visitor );

        return writer.toString();
    }

    private static Stream<Node> ancestorOrSelf( Map<Node, Node> parents, Node node )
    {
        return node != null
                ? Stream.concat( Stream.of( node ), ancestorOrSelf( parents, parents.get( node ) ) )
                : Stream.empty();
    }

    /**
     * @param writer {@link Writer}
     * @return {@link NodeVisitor}
     */
    public NodeVisitor getSerializingDependencyNodeVisitor( Writer writer )
    {
        if ( "graphml".equals( outputType ) )
        {
            return new GraphmlDependencyNodeVisitor( writer );
        }
        else if ( "tgf".equals( outputType ) )
        {
            return new TGFDependencyNodeVisitor( writer );
        }
        else if ( "dot".equals( outputType ) )
        {
            return new DOTDependencyNodeVisitor( writer );
        }
        else
        {
            return new SerializingDependencyNodeVisitor( writer, toGraphTokens( tokens ) );
        }
    }

    /**
     * Gets the graph tokens instance for the specified name.
     *
     * @param theTokens the graph tokens name
     * @return the <code>GraphTokens</code> instance
     */
    private GraphTokens toGraphTokens( String theTokens )
    {
        GraphTokens graphTokens;

        if ( "whitespace".equals( theTokens ) )
        {
            getLog().debug( "+ Using whitespace tree tokens" );

            graphTokens = SerializingDependencyNodeVisitor.WHITESPACE_TOKENS;
        }
        else if ( "extended".equals( theTokens ) )
        {
            getLog().debug( "+ Using extended tree tokens" );

            graphTokens = SerializingDependencyNodeVisitor.EXTENDED_TOKENS;
        }
        else
        {
            graphTokens = SerializingDependencyNodeVisitor.STANDARD_TOKENS;
        }

        return graphTokens;
    }

    // following is required because the version handling in maven code
    // doesn't work properly. I ripped it out of the enforcer rules.

    /**
     * Gets the dependency node filter to use when serializing the dependency graph.
     *
     * @return the dependency node filter, or <code>null</code> if none required
     */
    private Predicate<Node> createDependencyNodeFilter()
    {
        Predicate<Node> filter = null;
        VersionParser parser = session.getService( VersionParser.class );

        // filter includes
        if ( includes != null )
        {
            List<String> patterns = Arrays.asList( includes.split( "," ) );

            getLog().debug( "+ Filtering dependency tree by artifact include patterns: " + patterns );

            Predicate<Artifact> artifactFilter = new StrictPatternArtifactFilter( patterns, true, parser );
            filter = combine( filter,  n -> artifactFilter.test( n.getArtifact() ) );
        }

        // filter excludes
        if ( excludes != null )
        {
            List<String> patterns = Arrays.asList( excludes.split( "," ) );

            getLog().debug( "+ Filtering dependency tree by artifact exclude patterns: " + patterns );

            Predicate<Artifact> artifactFilter = new StrictPatternArtifactFilter( patterns, false, parser );
            filter = combine( filter, n -> artifactFilter.test( n.getArtifact() ) );
        }

        return filter;
    }

    static <T> Predicate<T> combine( Predicate<T> current, Predicate<T> with )
    {
        return current != null ? current.and( with ) : with;
    }
}
