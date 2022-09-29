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
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.PrintWriter;
import java.io.Writer;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Node;
import org.apache.maven.api.NodeVisitor;

/**
 * A dependency node visitor that serializes visited nodes to a writer.
 *
 * @author <a href="mailto:markhobson@gmail.com">Mark Hobson</a>
 */
public class SerializingDependencyNodeVisitor
        implements NodeVisitor
{
    // classes ----------------------------------------------------------------

    /**
     * Whitespace tokens to use when outputing the dependency graph.
     */
    public static final GraphTokens WHITESPACE_TOKENS = new GraphTokens( "   ", "   ", "   ", "   " );

    // constants --------------------------------------------------------------
    /**
     * The standard ASCII tokens to use when outputing the dependency graph.
     */
    public static final GraphTokens STANDARD_TOKENS = new GraphTokens( "+- ", "\\- ", "|  ", "   " );
    /**
     * The extended ASCII tokens to use when outputing the dependency graph.
     */
    public static final GraphTokens EXTENDED_TOKENS = new GraphTokens( "\u251C\u2500 ", "\u2514\u2500 ", "\u2502  ",
            "   " );
    /**
     * The writer to serialize to.
     */
    private final PrintWriter writer;

    // fields -----------------------------------------------------------------
    /**
     * The tokens to use when serializing the dependency graph.
     */
    private final GraphTokens tokens;
    private final Map<Node, Node> parents = new HashMap<>();
    /**
     * The depth of the currently visited dependency node.
     */
    private int depth;

    /**
     * Creates a dependency node visitor that serializes visited nodes to the specified writer using whitespace tokens.
     *
     * @param writer the writer to serialize to
     */
    public SerializingDependencyNodeVisitor( Writer writer )
    {
        this( writer, WHITESPACE_TOKENS );
    }

    // constructors -----------------------------------------------------------

    /**
     * Creates a dependency node visitor that serializes visited nodes to the specified writer using the specified
     * tokens.
     *
     * @param writer the writer to serialize to
     * @param tokens the tokens to use when serializing the dependency graph
     */
    public SerializingDependencyNodeVisitor( Writer writer, GraphTokens tokens )
    {
        if ( writer instanceof PrintWriter )
        {
            this.writer = (PrintWriter) writer;
        }
        else
        {
            this.writer = new PrintWriter( writer, true );
        }

        this.tokens = tokens;

        depth = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean enter( Node node )
    {
        if ( parents.isEmpty() )
        {
            node.stream().forEach( p -> p.getChildren().forEach( c -> parents.put( c, p ) ) );
        }

        indent( node );

        writer.println( node.asString() );

        depth++;

        return true;
    }

    // DependencyNodeVisitor methods ------------------------------------------

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean leave( Node node )
    {
        depth--;

        return true;
    }

    /**
     * Writes the necessary tokens to indent the specified dependency node to this visitor's writer.
     *
     * @param node the dependency node to indent
     */
    private void indent( Node node )
    {
        for ( int i = 1; i < depth; i++ )
        {
            writer.write( tokens.getFillIndent( isLast( node, i ) ) );
        }

        if ( depth > 0 )
        {
            writer.write( tokens.getNodeIndent( isLast( node ) ) );
        }
    }

    // private methods --------------------------------------------------------

    protected Node getParent( Node node )
    {
        return parents.get( node );
    }

    /**
     * Gets whether the specified dependency node is the last of its siblings.
     *
     * @param node the dependency node to check
     * @return <code>true</code> if the specified dependency node is the last of its last siblings
     */
    private boolean isLast( Node node )
    {
        // TODO: remove node argument and calculate from visitor calls only

        Node parent = getParent( node );

        boolean last;

        if ( parent == null )
        {
            last = true;
        }
        else
        {
            List<Node> siblings = parent.getChildren();

            last = ( siblings.indexOf( node ) == siblings.size() - 1 );
        }

        return last;
    }

    /**
     * Gets whether the specified dependency node ancestor is the last of its siblings.
     *
     * @param node          the dependency node whose ancestor to check
     * @param ancestorDepth the depth of the ancestor of the specified dependency node to check
     * @return <code>true</code> if the specified dependency node ancestor is the last of its siblings
     */
    private boolean isLast( Node node, int ancestorDepth )
    {
        // TODO: remove node argument and calculate from visitor calls only

        int distance = depth - ancestorDepth;

        while ( distance-- > 0 )
        {
            node = getParent( node );
        }

        return isLast( node );
    }

    /**
     * Provides tokens to use when serializing the dependency graph.
     */
    public static class GraphTokens
    {
        private final String nodeIndent;

        private final String lastNodeIndent;

        private final String fillIndent;

        private final String lastFillIndent;

        public GraphTokens( String nodeIndent, String lastNodeIndent, String fillIndent, String lastFillIndent )
        {
            this.nodeIndent = nodeIndent;
            this.lastNodeIndent = lastNodeIndent;
            this.fillIndent = fillIndent;
            this.lastFillIndent = lastFillIndent;
        }

        public String getNodeIndent( boolean last )
        {
            return last ? lastNodeIndent : nodeIndent;
        }

        public String getFillIndent( boolean last )
        {
            return last ? lastFillIndent : fillIndent;
        }
    }
}
