package fiji.plugin.trackmate.io;

import java.io.File;
import java.io.IOException;

import org.apache.commons.io.FileUtils;

import fiji.plugin.trackmate.Model;
import fiji.plugin.trackmate.Settings;
import ij.ImagePlus;

public class Demo
{

	public static void main( final String[] args ) throws IOException
	{
		final String tmFilename = "../TrackMate/samples/FakeTracks.xml";
		final String inputZarrPath = "samples/FakeTracks.zarr/tracks";
		final String outputTMFilename = "samples/FakeTracks-fromGEFF.xml";

		// Recursively delete the output Zarr path if it exists.
		FileUtils.deleteDirectory( new File( inputZarrPath ) );

		write( tmFilename, inputZarrPath );
		final Model model = TrackMateGeffIO.readFromGeff( inputZarrPath );

		System.out.println( "\n\n------------------" );
		System.out.println( model );

		System.out.println( "\n\n------------------\nWriting to TrackMate file" );

		// Tmp fix 1: set a name for all spots.
		model.getSpots().iterable( false ).forEach( spot -> spot.setName( "ID" + spot.ID() ) );

		// Tmp fix 2: use a Settings object from elsewhere.
		final TmXmlReader reader = new TmXmlReader( new File( tmFilename ) );
		final ImagePlus imp = reader.readImage();
		final Settings settings = reader.readSettings( imp );

		final TmXmlWriter writer = new TmXmlWriter( new File( outputTMFilename ) );
		writer.appendModel( model );
		writer.appendSettings( settings );
		writer.writeToFile();
	}

	public static void write( final String filename, final String outputZarrPath )
	{
		final TmXmlReader reader = new TmXmlReader( new File( filename ) );
		if ( !reader.isReadingOk() )
		{
			System.err.println( "Error reading file: " + reader.getErrorMessage() );
			return;
		}

		final Model model = reader.getModel();
		TrackMateGeffIO.serializeToGeff( model, outputZarrPath );
	}
}
