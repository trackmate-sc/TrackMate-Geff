package fiji.plugin.trackmate.io;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import org.jgrapht.graph.DefaultWeightedEdge;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.TrackModel;

public class TrackMateGeffIO
{

	public static void main( final String[] args )
	{
		final String filename = "../TrackMate/samples/FakeTracks.xml";
		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		/*
		 * Placeholder for serialization with GEFF.
		 */

		final Model model = reader.getModel();
		serializeToGeff( model );
	}

	/**
	 * Serializes the given TrackMate model to a GEFF file.
	 *
	 * @param model
	 *            the TrackMate model to serialize.
	 */
	public static void serializeToGeff( final Model model )
	{
		final FeatureModel featureModel = model.getFeatureModel();

		// Serialize spatial and temporal units.
		serializeUnits( model.getSpaceUnits(), model.getTimeUnits() );

		// Serialize spots.
		final Map< Spot, Integer > spotIdMap = new HashMap<>();
		model.getSpots().iterable( false ).forEach( s -> {
			final int id = serializeSpot( s );
			spotIdMap.put( s, id );
		} );

		// Serialize edges.
		final TrackModel trackModel = model.getTrackModel();
		final Set< Integer > trackIDs = trackModel.unsortedTrackIDs( false );
		for ( final Integer trackID : trackIDs )
		{
			final Set< DefaultWeightedEdge > edges = trackModel.trackEdges( trackID );
			for ( final DefaultWeightedEdge edge : edges )
			{
				final Spot source = trackModel.getEdgeSource( edge );
				final Integer sourceId = spotIdMap.get( source );
				if ( sourceId == null )
				{
					System.err.println( "Source spot not found in spotIdMap: " + source );
					continue;
				}

				final Spot target = trackModel.getEdgeTarget( edge );
				final Integer targetId = spotIdMap.get( target );
				if ( targetId == null )
				{
					System.err.println( "Target spot not found in spotIdMap: " + target );
					continue;
				}

				serializeEdge( sourceId, targetId, trackID, edge, featureModel );
			}

			/*
			 * Write what tracks are marked as visible and serialize their
			 * features.
			 */
			final boolean trackVisible = trackModel.isVisible( trackID );
			serializeTrack( trackID, featureModel, trackVisible );

		}

		// Write feature declarations.
		serializeFeatureDeclarations( featureModel );

		// Done for the model.
	}

	/**
	 * Serializes the spatial and temporal units in the GEFF file format.
	 *
	 * @param spaceUnits
	 *            the spatial units, e.g., "micrometer", "pixel", etc.
	 * @param timeUnits
	 *            the temporal units, e.g., "second", "minute", etc.
	 */
	private static void serializeUnits( final String spaceUnits, final String timeUnits )
	{
		// TODO
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
	 * @param featureModel
	 *            the feature model, needed to retrieve track features.
	 * @param trackVisible
	 *            true if the track is visible, false otherwise.
	 */
	private static void serializeTrack( final Integer trackID, final FeatureModel featureModel, final boolean trackVisible )
	{
		// TODO
	}

	/**
	 * Writes the given edge to the currently open GEFF file.
	 *
	 * @param sourceId
	 *            the id of the source spot in the GEFF file.
	 * @param targetId
	 *            the id of the target spot in the GEFF file.
	 * @param trackID
	 *            the id of the track to which the edge belongs. Required to
	 *            later mark some tracks as visible in the GEFF file.
	 * @param edge
	 *            the edge to serialize.
	 * @param featureModel
	 *            the feature model, needed to retrieve edge features.
	 * @return the id node of the edge in the GEFF file.
	 */
	private static void serializeEdge( final Integer sourceId, final Integer targetId, final Integer trackID, final DefaultWeightedEdge edge, final FeatureModel featureModel )
	{
		// TODO
	}

	/**
	 * Writes the given spot to the currently open GEFF file and returns the id
	 * node of the spot in the GEFF file.
	 *
	 * @param spot
	 *            the spot to serialize.
	 * @return the id node of the spot in the GEFF file.
	 */
	private static int serializeSpot( final Spot spot )
	{
		// TODO
		return -1;
	}

}
