package fiji.plugin.trackmate.io;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.SimpleWeightedGraph;
import org.mastodon.geff.GeffEdge;
import org.mastodon.geff.GeffMetadata;
import org.mastodon.geff.GeffNode;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotCollection;
import fiji.plugin.trackmate.TrackModel;
import ucar.ma2.InvalidRangeException;

public class TrackMateGeffIO
{

	public static Model readFromGeff( final String inputZarrPath )
	{
		final Model model = new Model();

		// Read the metadata.
		try
		{
			final GeffMetadata metadata = GeffMetadata.readFromZarr( inputZarrPath );
			final int xAxis = findSpatialAxis( metadata.getAxisNames() );
			final String spaceUnits = metadata.getAxisUnits()[ xAxis ];
			final int tAxis = findTemporalAxis( metadata.getAxisNames() );
			final String timeUnits = metadata.getAxisUnits()[ tAxis ];
			model.setPhysicalUnits( spaceUnits, timeUnits );
		}
		catch ( IOException | InvalidRangeException e )
		{
			e.printStackTrace();
		}

		try
		{
			// Read the nodes (spots).
			final List< GeffNode > nodes = GeffNode.readFromZarr( inputZarrPath );
			final SpotCollection spots = toSpotCollection( nodes );
			model.setSpots( spots, false );

			// Read the edges.
			final List< GeffEdge > geffEdges = GeffEdge.readFromZarr( inputZarrPath );
			System.out.println( geffEdges.size() + " edges found." );
			final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph = toGraph( geffEdges, spots );
			setTrackModel( model, graph );

		}
		catch ( IOException | InvalidRangeException e )
		{
			e.printStackTrace();
		}

		return model;
	}

	private static void setTrackModel( final Model model, final SimpleWeightedGraph< Spot, DefaultWeightedEdge > graph )
	{
		System.out.println( graph ); // DEBUG
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
			final double weight = 1.; // TODO

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
			final int tp = node.getTimepoint();
			final int segmentId = node.getSegmentId(); // TODO
			final double r = 5.; // TODO: How to get the radius?
			// TODO other features?

			final Spot spot = new Spot( id );
			spot.putFeature( Spot.POSITION_X, x );
			spot.putFeature( Spot.POSITION_Y, y );
			spot.putFeature( Spot.POSITION_Z, z );
			spot.putFeature( Spot.FRAME, ( double ) tp );
			spot.putFeature( Spot.RADIUS, r );

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

	private static int findTemporalAxis( final String[] axisNames )
	{
		for ( int d = 0; d < axisNames.length; d++ )
		{
			final String name = axisNames[ d ].toLowerCase().trim();
			if ( name.equals( "t" ) || name.equals( "time" ) )
				return d;
		}
		return -1;
	}

	private static int findSpatialAxis( final String[] axisNames )
	{
		for ( int d = 0; d < axisNames.length; d++ )
		{
			final String name = axisNames[ d ].toLowerCase().trim();
			if ( name.equals( "x" ) || name.equals( "y" ) || name.equals( "z" ) )
				return d;
		}
		return -1;
	}

	/**
	 * Serializes the given TrackMate model to a GEFF file.
	 *
	 * @param model
	 *            the TrackMate model to serialize.
	 * @param outputZarrPath
	 */
	public static void serializeToGeff( final Model model, final String outputZarrPath )
	{
		final FeatureModel featureModel = model.getFeatureModel();

		// Serialize spots.
		serializeSpots( model.getSpots().iterable( false ), model.getTrackModel(), outputZarrPath );

		// Serialize edges.
		final TrackModel trackModel = model.getTrackModel();
		serializeEdges( trackModel, featureModel, outputZarrPath );

		final Set< Integer > trackIDs = trackModel.unsortedTrackIDs( false );
		for ( final Integer trackID : trackIDs )
		{
			/*
			 * Write what tracks are marked as visible and serialize their
			 * features.
			 */
			final boolean trackVisible = trackModel.isVisible( trackID );
			serializeTrack( trackID, trackModel, featureModel, trackVisible );
		}

		// Write feature declarations.
		serializeFeatureDeclarations( featureModel );

		// GEFF metadata.
		final String version = "0.1.0";
		final boolean directed = true;
		final double[] roiMin = getRoiMin( model.getSpots().iterable( false ) );
		final double[] roiMax = getRoiMax( model.getSpots().iterable( false ) );
		final String positionAttr = "position"; // TODO What should I put here?
		final String[] axisNames = new String[] { "x", "y", "z", "t" };
		final String[] axisUnits = new String[] { model.getSpaceUnits(), model.getSpaceUnits(), model.getSpaceUnits(), model.getTimeUnits() };
		final GeffMetadata metadata = new GeffMetadata( version, directed, roiMin, roiMax, positionAttr, axisNames, axisUnits );
		try
		{
			GeffMetadata.writeToZarr( metadata, outputZarrPath );
		}
		catch ( final UnsupportedOperationException | IOException e )
		{
			e.printStackTrace();
		}

		// Done for the model.
	}

	private static double[] getRoiMin( final Iterable< Spot > iterable )
	{
		final double[] min = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		for ( final Spot spot : iterable )
		{
			for ( int d = 0; d < 3; d++ )
			{
				final double pos = spot.getDoublePosition( d );
				if ( pos < min[ d ] )
					min[ d ] = pos;
			}
			final int t = spot.getFeature( Spot.FRAME ).intValue();
			if ( t < min[ 3 ] )
				min[ 3 ] = t;
		}
		return min;
	}

	private static double[] getRoiMax( final Iterable< Spot > iterable )
	{
		final double[] max = new double[] { Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE };
		for ( final Spot spot : iterable )
		{
			for ( int d = 0; d < 3; d++ )
			{
				final double pos = spot.getDoublePosition( d );
				if ( pos > max[ d ] )
					max[ d ] = pos;
			}
			final int t = spot.getFeature( Spot.FRAME ).intValue();
			if ( t > max[ 3 ] )
				max[ 3 ] = t;
		}
		return max;
	}

	private static void serializeEdges(
			final TrackModel trackModel,
			final FeatureModel featureModel,
			final String outputZarrPath )
	{
		final List< GeffEdge > geffEdges = new ArrayList<>();
		final Set< Integer > trackIDs = trackModel.trackIDs( false );

		for ( final Integer trackID : trackIDs )
		{
			final Set< DefaultWeightedEdge > edges = trackModel.trackEdges( trackID );
			for ( final DefaultWeightedEdge edge : edges )
			{
				final Spot source = trackModel.getEdgeSource( edge );
				final int sourceId = source.ID();
				final Spot target = trackModel.getEdgeTarget( edge );
				final int targetId = target.ID();
				final GeffEdge geffEdge = new GeffEdge( sourceId, targetId );
				geffEdges.add( geffEdge );
			}
		}
		try
		{
			GeffEdge.writeToZarr( geffEdges, outputZarrPath + "/edges", GeffEdge.getChunkSize( outputZarrPath ) );
		}
		catch ( IOException | InvalidRangeException e )
		{
			e.printStackTrace();
		}
	}

	/**
	 * Serializes the spots in the GEFF file format.
	 * <p>
	 * The spots are serialized as nodes in the GEFF file format, with the id
	 * being the ID of the spot.
	 *
	 * @param iterable
	 *            an iterable of spots to serialize.
	 * @param outputZarrPath
	 *            the path to the output Zarr file where the spots will be
	 *            serialized.
	 * @param trackModel
	 *            the TrackModel to retrieve track IDs for the spots.
	 */
	private static void serializeSpots( final Iterable< Spot > iterable, final TrackModel trackModel, final String outputZarrPath )
	{
		final List< GeffNode > nodes = new ArrayList<>();
		for ( final Spot spot : iterable )
		{
			final int id = spot.ID();
			final int tp = spot.getFeature( Spot.FRAME ).intValue();
			final double x = spot.getDoublePosition( 0 );
			final double y = spot.getDoublePosition( 1 );
			final double z = spot.getDoublePosition( 2 );

			// Segment ID is track ID here. Is it valid? // TODO
			final Integer segmentIdObj = trackModel.trackIDOf( spot );
			final int segmentId = segmentIdObj != null ? segmentIdObj : -1;
			final GeffNode node = new GeffNode( id, tp, x, y, z, segmentId );
			nodes.add( node );
		}
		try
		{
			GeffNode.writeToZarr( nodes, outputZarrPath + "/nodes", GeffNode.getChunkSize( outputZarrPath ) );
		}
		catch ( IOException | InvalidRangeException e )
		{
			e.printStackTrace();
		}
	}

	private static void serializeFeatureDeclarations( final FeatureModel fm )
	{
		// Spots
		Collection< String > features = fm.getSpotFeatures();
		Map< String, String > featureNames = fm.getSpotFeatureNames();
		Map< String, String > featureShortNames = fm.getSpotFeatureShortNames();
		Map< String, Dimension > featureDimensions = fm.getSpotFeatureDimensions();
		Map< String, Boolean > featureIsInt = fm.getSpotFeatureIsInt();
		serializeFeatureGroup( "SpotFeatures", features, featureNames, featureShortNames, featureDimensions, featureIsInt );

		// Edges
		features = fm.getEdgeFeatures();
		featureNames = fm.getEdgeFeatureNames();
		featureShortNames = fm.getEdgeFeatureShortNames();
		featureDimensions = fm.getEdgeFeatureDimensions();
		featureIsInt = fm.getEdgeFeatureIsInt();
		serializeFeatureGroup( "EdgeFeatures", features, featureNames, featureShortNames, featureDimensions, featureIsInt );

		// Tracks
		features = fm.getTrackFeatures();
		featureNames = fm.getTrackFeatureNames();
		featureShortNames = fm.getTrackFeatureShortNames();
		featureDimensions = fm.getTrackFeatureDimensions();
		featureIsInt = fm.getTrackFeatureIsInt();
		serializeFeatureGroup( "TrackFeatures", features, featureNames, featureShortNames, featureDimensions, featureIsInt );
	}

	/**
	 * Serializes a group of features in the GEFF file format.
	 *
	 * @param type
	 *            the type of the feature group, e.g., "SpotFeatures",
	 *            "EdgeFeatures",
	 * @param features
	 *            the collection of feature keys to serialize (e.g.,
	 *            "MEAN_INTENSITY_CH1", ...).
	 * @param featureNames
	 *            the map of feature keys to their names (e.g., "Mean intensity
	 *            ch1").
	 * @param featureShortNames
	 *            the map of feature keys to their short names (e.g., "Mean
	 *            ch1").
	 * @param featureDimensions
	 *            the map of feature keys to their dimensions (e.g.,
	 *            "INTENSITY", "COUNT", "LENGTH", etc.).
	 * @param featureIsInt
	 *            the map of feature keys to whether they are integers or
	 *            doubles.
	 */
	private static void serializeFeatureGroup( final String type, final Collection< String > features, final Map< String, String > featureNames, final Map< String, String > featureShortNames, final Map< String, Dimension > featureDimensions, final Map< String, Boolean > featureIsInt )
	{
		for ( final String feature : features )
		{
			final String name = featureNames.get( feature );
			final String shortName = featureShortNames.get( feature );
			final String dimension = featureDimensions.get( feature ).name();
			final boolean isInt = featureIsInt.get( feature );
			serializeFeatureDeclaration( type, feature, name, shortName, dimension, isInt );
		}
	}

	/**
	 * Serializes a feature declaration in the GEFF file format.
	 *
	 * @param type
	 *            the type of the feature, e.g., "SpotFeatures", "EdgeFeatures",
	 *            or "TrackFeatures".
	 * @param feature
	 *            the key of the feature to serialize ("MEAN_INTENSITY_CH1",
	 *            ...).
	 * @param name
	 *            the name of the feature, e.g., "Mean intensity ch1".
	 * @param shortName
	 *            the short name of the feature, e.g., "Mean ch1".
	 * @param dimension
	 *            the dimension of the feature, e.g., "INTENSITY", "COUNT",
	 *            "LENGTH", etc.
	 * @param isInt
	 *            <code>true</code> if the feature values map to integers. If
	 *            <code>false</code> if they map to doubles.
	 */
	private static void serializeFeatureDeclaration( final String type, final String feature, final String name, final String shortName, final String dimension, final boolean isInt )
	{
		// TODO

	}

	/**
	 * Serializes the features and the visibility of a track in the GEFF file.
	 *
	 * @param trackID
	 *            the id of the track to serialize.
	 * @param trackModel
	 * @param featureModel
	 *            the feature model, needed to retrieve track features.
	 * @param trackVisible
	 *            true if the track is visible, false otherwise.
	 */
	private static void serializeTrack( final Integer trackID, final TrackModel trackModel, final FeatureModel featureModel, final boolean trackVisible )
	{
		// TODO
	}
}
