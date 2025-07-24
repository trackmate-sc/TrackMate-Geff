/*-
 * #%L
 * TrackMate: your buddy for everyday tracking.
 * %%
 * Copyright (C) 2025 TrackMate developers.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */
package fiji.plugin.trackmate.io;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.graph.DefaultWeightedEdge;
import org.mastodon.geff.GeffAxis;
import org.mastodon.geff.GeffEdge;
import org.mastodon.geff.GeffMetadata;
import org.mastodon.geff.GeffNode;
import org.mastodon.geff.GeffNode.Builder;
import org.mastodon.geff.ZarrUtils;

import fiji.plugin.trackmate.Dimension;
import fiji.plugin.trackmate.FeatureModel;
import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Spot;
import fiji.plugin.trackmate.SpotRoi;
import fiji.plugin.trackmate.TrackModel;
import fiji.plugin.trackmate.features.manual.ManualSpotColorAnalyzerFactory;
import ucar.ma2.InvalidRangeException;

/**
 * Exports a TrackMate model to a GEFF format.
 */
public class TrackMateGeffWriter
{

	public static final String GEFF_VERSION = "0.4.0";

	public static final String GEFF_PREFIX = "tracking_graph";

	public static void export( final Model model, final String zarrPath ) throws IOException, InvalidRangeException
	{
		// Geff is a subfolder of the Zarr file.
		final String outputZarrPath = zarrPath.endsWith( "/" ) ? zarrPath + GEFF_PREFIX : zarrPath + "/" + GEFF_PREFIX;

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
		final boolean directed = true;
		final double[] roiMin = getRoiMin( model.getSpots().iterable( false ) );
		final double[] roiMax = getRoiMax( model.getSpots().iterable( false ) );
		final String[] axisNames = new String[] { "t", "z", "y", "x" };
		final String spaceUnits = model.getSpaceUnits();
		final String timeUnits = model.getTimeUnits();
		final GeffAxis[] axes = new GeffAxis[ 4 ];
		axes[ 0 ] = GeffAxis.createTimeAxis( axisNames[ 0 ], timeUnits, roiMin[ 0 ], roiMax[ 0 ] );
		for ( int d = 1; d < 4; d++ )
			axes[ d ] = GeffAxis.createSpaceAxis( axisNames[ d ], spaceUnits, roiMin[ d ], roiMax[ d ] );

		final GeffMetadata metadata = new GeffMetadata( GEFF_VERSION, directed, axes );
		GeffMetadata.writeToZarr( metadata, outputZarrPath );
		// Done for the model.
	}

	private static final double[] getRoiMin( final Iterable< Spot > iterable )
	{
		final double[] min = new double[] { Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE, Double.MAX_VALUE };
		for ( final Spot spot : iterable )
		{
			final int t = spot.getFeature( Spot.FRAME ).intValue();
			if ( t < min[ 3 ] )
				min[ 0 ] = t;
			for ( int d = 1; d < 4; d++ )
			{
				// change axis order. xyz -> zyx
				final int e = 3 - d;
				final double pos = spot.getDoublePosition( e );
				if ( pos < min[ d ] )
					min[ d ] = pos;
			}
		}
		return min;
	}

	private static double[] getRoiMax( final Iterable< Spot > iterable )
	{
		final double[] max = new double[] { Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE, Double.MIN_VALUE };
		for ( final Spot spot : iterable )
		{
			final int t = spot.getFeature( Spot.FRAME ).intValue();
			if ( t > max[ 0 ] )
				max[ 0 ] = t;
			for ( int d = 1; d < 4; d++ )
			{
				// change axis order. xyz -> zyx
				final int e = 3 - d;
				final double pos = spot.getDoublePosition( e );
				if ( pos > max[ d ] )
					max[ d ] = pos;
			}
		}
		return max;
	}

	private static void serializeEdges(
			final TrackModel trackModel,
			final FeatureModel featureModel,
			final String outputZarrPath )
			throws IOException, InvalidRangeException
	{
		final List< GeffEdge > geffEdges = new ArrayList<>();
		final Set< Integer > trackIDs = trackModel.trackIDs( false );

		int id = 0;
		for ( final Integer trackID : trackIDs )
		{
			final Set< DefaultWeightedEdge > edges = trackModel.trackEdges( trackID );
			for ( final DefaultWeightedEdge edge : edges )
			{
				final Spot source = trackModel.getEdgeSource( edge );
				final int sourceId = source.ID();
				final Spot target = trackModel.getEdgeTarget( edge );
				final int targetId = target.ID();
				final double score = -1.;
				final double distance = Math.sqrt( trackModel.getEdgeWeight( edge ) );
				final GeffEdge geffEdge = new GeffEdge( id++, sourceId, targetId, score, distance );
				geffEdges.add( geffEdge );
			}
		}
		GeffEdge.writeToZarr( geffEdges, outputZarrPath, ZarrUtils.getChunkSize( outputZarrPath ), GEFF_VERSION );
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
	 * @throws InvalidRangeException
	 * @throws IOException
	 */
	private static void serializeSpots( final Iterable< Spot > iterable, final TrackModel trackModel, final String outputZarrPath ) throws IOException, InvalidRangeException
	{
		final List< GeffNode > nodes = new ArrayList<>();
		final Builder builder = GeffNode.builder();
		for ( final Spot spot : iterable )
		{
			final double[] color = new double[ 4 ];
			getColorFromSpot( spot, color );

			final Integer segmentIdObj = trackModel.trackIDOf( spot );
			final int segmentId = segmentIdObj != null ? segmentIdObj : -1;

			final GeffNode node = builder
					.x( spot.getDoublePosition( 0 ) )
					.y( spot.getDoublePosition( 1 ) )
					.z( spot.getDoublePosition( 2 ) )
					.timepoint( spot.getFeature( Spot.FRAME ).intValue() )
					.id( spot.ID() )
					.radius( spot.getFeature( Spot.RADIUS ).doubleValue() )
					.color( color )
					.segmentId( segmentId )
					.build();

			// Polygon, if any.
			final SpotRoi roi = spot.getRoi();
			if ( null != roi )
			{
				// Coordinates are expected to be relative to spot center.
				node.setPolygonX( roi.x );
				node.setPolygonY( roi.y );
			}

			nodes.add( node );
		}

		GeffNode.writeToZarr( nodes, outputZarrPath, ZarrUtils.getChunkSize( outputZarrPath ), GEFF_VERSION );
	}

	private static void getColorFromSpot( final Spot spot, final double[] color )
	{
		final Double val = spot.getFeature( ManualSpotColorAnalyzerFactory.FEATURE );
		final Color c;
		if ( null == val )
			c = Color.GRAY.darker();
		else
			c = new Color( val.intValue() );

		color[ 0 ] = c.getRed() / 255.0;
		color[ 1 ] = c.getGreen() / 255.0;
		color[ 2 ] = c.getBlue() / 255.0;
		color[ 3 ] = c.getAlpha() / 255.0;
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
