package fiji.plugin.trackmate.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.SelectionModel;
import fiji.plugin.trackmate.Settings;
import fiji.plugin.trackmate.TrackMate;
import fiji.plugin.trackmate.gui.displaysettings.DisplaySettings;
import fiji.plugin.trackmate.gui.wizard.TrackMateWizardSequence;
import fiji.plugin.trackmate.visualization.hyperstack.HyperStackDisplayer;
import ij.ImageJ;
import ij.ImagePlus;
import ucar.ma2.InvalidRangeException;

public class Demo
{

	public static void main( final String[] args ) throws IOException, InvalidRangeException
	{
		final String tmFilename = "../TrackMate/samples/FakeTracks.xml";
		final String inputZarrPath = "samples/FakeTracks.zarr/tracks";
		final String outputTMFilename = "samples/FakeTracks-fromGEFF.xml";

		System.out.println( "\n\nDeleting previous run\n------------------" );
		// Recursively delete the output Zarr path if it exists.
		FileUtils.deleteDirectory( new File( inputZarrPath ) );

		System.out.println( "\n\nLoad a TrackMate file and resave it as GEFF\n------------------" );
		write( tmFilename, inputZarrPath );

		System.out.println( "\n\nLoad the GEFF and open it into TrackMate\n------------------" );
		final Model model = TrackMateGeffIO.readFromGeff( inputZarrPath );

		System.out.println( model );

		System.out.println( "\n\n------------------\nResave the imported TrackMate data to a TrackMate file" );

		// Tmp fix 1: set a name for all spots.
		model.getSpots().iterable( false ).forEach( spot -> spot.setName( "ID" + spot.ID() ) );

		// Tmp fix 2: use a Settings object from elsewhere.
		final TmXmlReader reader = new TmXmlReader( new File( tmFilename ) );
		final ImagePlus imp = reader.readImage();
		final Settings settings = reader.readSettings( imp );

		// Tmp fix 3: mark all spots as visible.
		model.getSpots().setVisible( true );

		final TmXmlWriter writer = new TmXmlWriter( new File( outputTMFilename ) );
		writer.appendModel( model );
		writer.appendSettings( settings );
		writer.writeToFile();

		System.out.println( "\n\n------------------\nReloading and displaying the new TrackMate file" );
		final TmXmlReader reader2 = new TmXmlReader( new File( outputTMFilename ) );
		if ( !reader2.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader2.getErrorMessage() );
			return;
		}
		final Model model2 = reader2.getModel();
		final ImagePlus imp2 = reader2.readImage();
		final Settings settings2 = reader2.readSettings( imp2 );
		final DisplaySettings ds = reader2.getDisplaySettings();
		final SelectionModel sm2 = new SelectionModel( model2 );

		final TrackMateWizardSequence sequence = new TrackMateWizardSequence( new TrackMate( model2, settings2 ), sm2, ds );
		sequence.setCurrent( "ConfigureViews" );
		sequence.run( "Test GEFF" ).setVisible( true );

		ImageJ.main( args );
		final HyperStackDisplayer viewer = new HyperStackDisplayer( model2, sm2, imp2, ds );
		viewer.render();
	}

	public static void write( final String filename, final String outputZarrPath ) throws IOException, InvalidRangeException
	{
		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();
		TrackMateGefffWriter.export( model, outputZarrPath );
	}
}
