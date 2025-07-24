package fiji.plugin.trackmate.io;

import static fiji.plugin.trackmate.io.TrackMateGeffWriter.GEFF_PREFIX;
import static fiji.plugin.trackmate.io.TrackMateGeffWriter.GEFF_VERSION;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.mastodon.geff.GeffAxis;
import org.mastodon.geff.GeffEdge;
import org.mastodon.geff.GeffMetadata;
import org.mastodon.geff.GeffNode;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.SpotRoi;
import ucar.ma2.InvalidRangeException;

public class TrackMateGeffReader
{

	public static Model readModel( final String inputZarrPath ) throws IOException, InvalidRangeException
	{
		return readModel( inputZarrPath, new Model() );
	}

	public static Model readModel( final String zarrPath, final Model model ) throws IOException, InvalidRangeException
	{
		// Geff is a subfolder of the Zarr file.
		final String inputZarrPath = zarrPath.endsWith( "/" ) ? zarrPath + GEFF_PREFIX : zarrPath + "/" + GEFF_PREFIX;

		// Read the metadata.
		final GeffMetadata metadata = GeffMetadata.readFromZarr( inputZarrPath );
		final int xAxis = findSpatialAxis( metadata.getGeffAxes() );
		final String spaceUnits = metadata.getGeffAxes()[ xAxis ].getUnit();
		final int tAxis = findTemporalAxis( metadata.getGeffAxes() );
		final String timeUnits = metadata.getGeffAxes()[ tAxis ].getUnit();
		model.setPhysicalUnits( spaceUnits, timeUnits );

		// Read the nodes (spots).
		final List< GeffNode > nodes = GeffNode.readFromZarr( inputZarrPath, GEFF_VERSION );
		final SpotCollection spots = toSpotCollection( nodes );
		model.setSpots( spots, false );

		// Read the edges.
		final List< GeffEdge > geffEdges = GeffEdge.readFromZarr( inputZarrPath, GEFF_VERSION );
		System.out.println( geffEdges.size() + " edges found." );
		final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph = toGraph( geffEdges, spots );
		setTrackModel( model, graph );

		return model;
	}

	private static void setTrackModel( final Model model, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph )
	{
		final ConnectivityInspector< Spot, DefaultWeightedEdge > inspector = new ConnectivityInspector<>( graph );
		final List< Set< Spot > > trackSpots = inspector.connectedSets();
		System.out.println( "Found " + trackSpots.size() + " tracks." );

		final Map< Integer, Set< Spot > > trackSpotsMap = new HashMap<>();
		final Map< Integer, Set< DefaultWeightedEdge > > trackEdgesMap = new HashMap<>();
		final Map< Integer, Boolean > trackVisibility = new HashMap<>();
		final Map< Integer, String > trackNames = new HashMap<>();

		int trackId = 200; // TODO
		for ( final Set< Spot > spots : trackSpots )
		{
			trackId++;
			trackSpotsMap.put( trackId, spots );
			System.out.println( trackId + " has " + spots.size() + " spots." );

			final Set< DefaultWeightedEdge > edges = new HashSet<>();
			for ( final Spot spot : spots )
			{
				final Set< DefaultWeightedEdge > es = graph.edgesOf( spot );
				for ( final DefaultWeightedEdge e : es )
					edges.add( e );
			}
			trackEdgesMap.put( trackId, edges );

			final boolean visible = true; // TODO: How to determine visibility?
			trackVisibility.put( trackId, visible );

			// TODO: How to get the track name?
			final String trackName = "Track " + trackId;
			trackNames.put( trackId, trackName );
		}
		model.getTrackModel().from( graph, trackSpotsMap, trackEdgesMap, trackVisibility, trackNames );
	}

	private static SimpleWeightedGraph< Spot, DefaultWeightedEdge > toGraph( final List< GeffEdge > geffEdges, final SpotCollection spots )
	{
		// Map id -> spot.
		final Map< Integer, Spot > spotMap = new HashMap<>();
		for ( final Spot spot : spots.iterable( false ) )
			spotMap.put( spot.ID(), spot );

		final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph = new SimpleWeightedGraph<>( DefaultWeightedEdge.class );
		for ( final GeffEdge geffEdge : geffEdges )
		{
			final int sourceId = geffEdge.getSourceNodeId();
			final int targetId = geffEdge.getTargetNodeId();
			final Spot sourceSpot = spotMap.get( sourceId );
			final Spot targetSpot = spotMap.get( targetId );
			final double d = geffEdge.getDistance();
			final double weight = d * d;

			if ( sourceSpot != null && targetSpot != null )
			{
				graph.addVertex( sourceSpot );
				graph.addVertex( targetSpot );
				final DefaultWeightedEdge edge = graph.addEdge( sourceSpot, targetSpot );
				graph.setEdgeWeight( edge, weight );
			}
		}
		return graph;
	}

	private static SpotCollection toSpotCollection( final List< GeffNode > nodes )
	{
		final Map< Integer, List< Spot > > spotMap = new HashMap<>();
		for ( final GeffNode node : nodes )
		{
			final int id = node.getId();
			final double x = node.getX();
			final double y = node.getY();
			final double z = node.getZ();
			final int tp = node.getT();
			final int segmentId = node.getSegmentId(); // TODO
			final double r = node.getRadius();
			// TODO other features?

			final Spot spot = new Spot( id );
			spot.putFeature( Spot.POSITION_X, x );
			spot.putFeature( Spot.POSITION_Y, y );
			spot.putFeature( Spot.POSITION_Z, z );
			spot.putFeature( Spot.FRAME, ( double ) tp );
			spot.putFeature( Spot.RADIUS, r );

			// Do we have polygons?
			final double[] xp = node.getPolygonX();
			if ( xp != null && xp.length > 0 )
			{
				// Coordinates are expected to be relative to spot center.
				final double[] yp = node.getPolygonY();
				final SpotRoi roi = new SpotRoi( xp, yp );
				spot.setRoi( roi );
			}

			spotMap.computeIfAbsent( tp, k -> new ArrayList<>() ).add( spot );
		}

		final SpotCollection spots = new SpotCollection();
		for ( final Integer key : spotMap.keySet() )
		{
			final List< Spot > spotList = spotMap.get( key );
			spots.put( key, spotList );
		}

		return spots;
	}

	private static int findTemporalAxis( final GeffAxis[] axes )
	{
		for ( int d = 0; d < axes.length; d++ )
		{
			final String name = axes[ d ].getName().toLowerCase().trim();
			if ( name.equals( "t" ) || name.equals( "time" ) )
				return d;
		}
		return -1;
	}

	private static int findSpatialAxis( final GeffAxis[] axes )
	{
		for ( int d = 0; d < axes.length; d++ )
		{
			final String name = axes[ d ].getName().toLowerCase().trim();
			if ( name.equals( "x" ) || name.equals( "y" ) || name.equals( "z" ) )
				return d;
		}
		return -1;
	}
}
